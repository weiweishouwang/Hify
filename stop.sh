#!/usr/bin/env bash
#
# 优雅停止 Hify 本地开发环境（后端 + 前端）
#
# 按 pid 文件找进程：先 SIGTERM 让它自己收尾，等一会儿；
# 超时还没退出才 SIGKILL。
#
# 用法：
#   ./stop.sh              # 停后端 + 前端
#   ./stop.sh --backend    # 只停后端
#   ./stop.sh --frontend   # 只停前端
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOG_DIR="${ROOT_DIR}/logs"
API_PID_FILE="${LOG_DIR}/api.pid"
WEB_PID_FILE="${LOG_DIR}/web.pid"

# SIGTERM 后等多久还没退出就 SIGKILL
GRACE_SECONDS="${HIFY_STOP_GRACE_SECONDS:-10}"

STOP_BACKEND=1
STOP_FRONTEND=1
for arg in "$@"; do
  case "$arg" in
    --backend)  STOP_FRONTEND=0 ;;
    --frontend) STOP_BACKEND=0 ;;
    -h|--help)
      sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) printf '未知参数：%s（用 --help 看用法）\n' "$arg" >&2; exit 2 ;;
  esac
done

if [ -t 1 ]; then
  C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_OFF=$'\033[0m'
else
  C_INFO=""; C_OK=""; C_WARN=""; C_ERR=""; C_OFF=""
fi
log_info() { printf '%s[停止]%s %s\n' "${C_INFO}" "${C_OFF}" "$*"; }
log_ok()   { printf '%s[  OK]%s %s\n' "${C_OK}" "${C_OFF}" "$*"; }
log_warn() { printf '%s[警告]%s %s\n' "${C_WARN}" "${C_OFF}" "$*"; }
log_err()  { printf '%s[失败]%s %s\n' "${C_ERR}" "${C_OFF}" "$*" >&2; }

# ── 工具 ──────────────────────────────────────────────────────────────

# 先杀子进程再杀自己：vite 是 npm 的子进程，只杀 npm 会留下 vite 孤儿
kill_tree() {
  local pid="$1" sig="$2" child
  for child in $(pgrep -P "${pid}" 2>/dev/null || true); do
    kill_tree "${child}" "${sig}"
  done
  kill -"${sig}" "${pid}" 2>/dev/null || true
}

# 这个 pid 是不是本项目的进程？
# pid 会被系统回收后复用给别的进程，不核对就 kill 会误伤无关服务。
is_ours() {
  local pid="$1" pattern="$2" cmd
  cmd="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
  printf '%s' "${cmd}" | grep -qE "${pattern}"
}

# 停一个由 pid 文件管理的进程
stop_one() {
  local name="$1" pid_file="$2" pattern="$3"
  local pid cmd

  if [ ! -f "${pid_file}" ]; then
    log_info "${name}：没有 pid 文件，跳过"
    return 0
  fi

  pid="$(cat "${pid_file}" 2>/dev/null || true)"
  # pid 文件用完就删，免得下次读到过期的 pid
  rm -f "${pid_file}"

  if [ -z "${pid}" ]; then
    log_warn "${name}：pid 文件是空的"
    return 0
  fi
  if ! ps -p "${pid}" >/dev/null 2>&1; then
    log_ok "${name}：进程 ${pid} 已经不在了"
    return 0
  fi
  if ! is_ours "${pid}" "${pattern}"; then
    cmd="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
    log_warn "${name}：pid ${pid} 的命令行不像本项目的进程，不动它（${cmd}）"
    return 0
  fi

  log_info "${name}：SIGTERM → pid ${pid}"
  kill_tree "${pid}" TERM

  local i=0
  while [ "${i}" -lt "${GRACE_SECONDS}" ]; do
    if ! ps -p "${pid}" >/dev/null 2>&1; then
      log_ok "${name}：已优雅退出"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done

  log_warn "${name}：${GRACE_SECONDS}s 内没退出，SIGKILL"
  kill_tree "${pid}" KILL
  sleep 1
  if ps -p "${pid}" >/dev/null 2>&1; then
    log_err "${name}：pid ${pid} 杀不掉，手动处理：kill -9 ${pid}"
    return 1
  fi
  log_ok "${name}：已强制结束"
  return 0
}

# ── 主流程 ────────────────────────────────────────────────────────────
rc=0

if [ "${STOP_BACKEND}" -eq 1 ]; then
  stop_one "后端" "${API_PID_FILE}" 'hify-app' || rc=1
fi
if [ "${STOP_FRONTEND}" -eq 1 ]; then
  stop_one "前端" "${WEB_PID_FILE}" 'npm run dev|vite' || rc=1
fi

if [ "${rc}" -eq 0 ]; then
  log_ok "全部停掉了"
else
  log_err "有进程没停下来，看上面的提示"
fi
exit "${rc}"

#!/usr/bin/env bash
#
# Hify 本地开发环境一键启动
#
#   1. 检查 MySQL / Redis 是否可用
#   2. 构建后端（Maven）并后台启动
#   3. 轮询后端健康检查，通过后才继续
#   4. 启动前端开发服务器（前台，Ctrl-C 退出时后端一并停掉）
#
# 用法：
#   ./start.sh                # 完整启动（前端在前台，Ctrl-C 一起停）
#   ./start.sh --skip-build   # 跳过后端构建（只改了前端代码时用）
#   ./start.sh --background   # 全栈放后台，脚本立刻返回（配合 stop.sh）
#
# 环境约定：
#   端口与凭据沿用 docker-compose.yml 那套 HIFY_* 变量名。
#   根目录有 .env 就加载它（本地 compose 的端口在这里生效，
#   本地 java 进程连的也是同一套库），没有就用 application.yml 的默认值。
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

# ── 参数 ──────────────────────────────────────────────────────────────
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --background) BACKGROUND=1 ;;
    -h|--help)
      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) printf '未知参数：%s（用 --help 看用法）\n' "$arg" >&2; exit 2 ;;
  esac
done

# ── 输出 ──────────────────────────────────────────────────────────────
# 定义在配置之前：加载 .env 时就要打日志
if [ -t 1 ]; then
  C_INFO=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_OFF=$'\033[0m'
else
  C_INFO=""; C_OK=""; C_WARN=""; C_ERR=""; C_OFF=""
fi
log_info() { printf '%s[启动]%s %s\n' "${C_INFO}" "${C_OFF}" "$*"; }
log_ok()   { printf '%s[  OK]%s %s\n' "${C_OK}" "${C_OFF}" "$*"; }
log_warn() { printf '%s[警告]%s %s\n' "${C_WARN}" "${C_OFF}" "$*"; }
log_err()  { printf '%s[失败]%s %s\n' "${C_ERR}" "${C_OFF}" "$*" >&2; }
fail()     { log_err "$*"; exit 1; }

# ── 配置 ──────────────────────────────────────────────────────────────
if [ -f "${ROOT_DIR}/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "${ROOT_DIR}/.env"
  set +a
  log_info "已加载 .env"
fi

DB_HOST="${HIFY_DB_HOST:-localhost}"
DB_PORT="${HIFY_DB_PORT:-3306}"
DB_NAME="${HIFY_DB_NAME:-hify}"
DB_USER="${HIFY_DB_USERNAME:-hify}"
DB_PASSWORD="${HIFY_DB_PASSWORD:-}"

REDIS_HOST="${HIFY_REDIS_HOST:-localhost}"
REDIS_PORT="${HIFY_REDIS_PORT:-6379}"
REDIS_PASSWORD="${HIFY_REDIS_PASSWORD:-}"

API_PORT="${HIFY_LOCAL_API_PORT:-8080}"
WEB_PORT="${HIFY_WEB_DEV_PORT:-5173}"

HEALTH_TIMEOUT="${HIFY_HEALTH_TIMEOUT:-90}"

LOG_DIR="${ROOT_DIR}/logs"
API_LOG="${LOG_DIR}/api.log"
API_PID_FILE="${LOG_DIR}/api.pid"
WEB_LOG="${LOG_DIR}/web.log"
WEB_PID_FILE="${LOG_DIR}/web.pid"
mkdir -p "${LOG_DIR}"

API_PID=""
WEB_PID=""
BACKGROUND="${BACKGROUND:-0}"

# ── 退出清理 ──────────────────────────────────────────────────────────
# 后台模式（--background）成功时进程要继续活着，由 stop.sh 负责停；
# 其余情况（前台模式、中途失败）都把本次启动的进程收掉，别留一堆孤儿。
cleanup() {
  local code="${1:-0}"

  if [ "${code}" -eq 0 ] && [ "${BACKGROUND}" -eq 1 ]; then
    # 后台模式正常收工：进程留着，pid 文件留给 stop.sh
    exit 0
  fi

  if [ -n "${WEB_PID}" ] && ps -p "${WEB_PID}" >/dev/null 2>&1; then
    printf '\n'
    log_info "停止前端进程（pid ${WEB_PID}）"
    kill "${WEB_PID}" 2>/dev/null || true
    for _ in 1 2 3 4 5; do
      ps -p "${WEB_PID}" >/dev/null 2>&1 || break
      sleep 1
    done
    kill -9 "${WEB_PID}" 2>/dev/null || true
  fi
  if [ -n "${API_PID}" ] && ps -p "${API_PID}" >/dev/null 2>&1; then
    printf '\n'
    log_info "停止后端进程（pid ${API_PID}）"
    kill "${API_PID}" 2>/dev/null || true
    # 给它一点时间优雅退出，不行就强杀
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      ps -p "${API_PID}" >/dev/null 2>&1 || break
      sleep 1
    done
    kill -9 "${API_PID}" 2>/dev/null || true
  fi
  rm -f "${API_PID_FILE}" "${WEB_PID_FILE}"
  exit "${code}"
}
trap 'cleanup $?' EXIT
trap 'cleanup 130' INT
trap 'cleanup 143' TERM

# ── 工具函数 ──────────────────────────────────────────────────────────

# TCP 端口探测：优先 nc，没有就用 bash 的 /dev/tcp（bash 4+）
tcp_probe() {
  local host="$1" port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 2 "${host}" "${port}" >/dev/null 2>&1
    return $?
  fi
  if [ -n "${BASH_VERSION:-}" ]; then
    # shellcheck disable=SC3042
    (exec 3<>"/dev/tcp/${host}/${port}") >/dev/null 2>&1
    return $?
  fi
  return 2
}

# 端口是否已被占用。
#
# IPv4 与 IPv6 都要试：vite 默认绑 localhost，macOS 上解析成 ::1，
# 只探 127.0.0.1 会漏掉一个明明在监听的端口。
port_in_use() {
  local port="$1"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 1 127.0.0.1 "${port}" >/dev/null 2>&1 && return 0
    nc -z -w 1 ::1 "${port}" >/dev/null 2>&1 && return 0
    nc -z -w 1 localhost "${port}" >/dev/null 2>&1 && return 0
    return 1
  fi
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1 && return 0
    return 1
  fi
  return 1
}

# 端口被谁占了（给人看的提示）
port_owner() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN 2>/dev/null | awk 'NR>1 {print $1" (pid "$2")"}' | head -1
  fi
}

# 等端口被监听起来（超时返回 1）
wait_port() {
  local port="$1" timeout="$2"
  local deadline=$((SECONDS + timeout))
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    if port_in_use "${port}"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

check_mysql() {
  local host="$1" port="$2" user="$3" password="$4"
  if command -v mysqladmin >/dev/null 2>&1; then
    MYSQL_PWD="${password}" mysqladmin --connect-timeout=3 -h "${host}" -P "${port}" -u "${user}" ping 2>/dev/null \
      | grep -q "mysqld is alive"
    return $?
  fi
  # 没装 mysql 客户端，退化成 TCP 探测（只能证明端口开着）
  log_warn "没找到 mysqladmin，只做 TCP 端口探测（证明不了数据库真的能连）"
  tcp_probe "${host}" "${port}"
}

check_redis() {
  local host="$1" port="$2" password="$3"
  if command -v redis-cli >/dev/null 2>&1; then
    local args=(-h "${host}" -p "${port}")
    [ -n "${password}" ] && args+=(-a "${password}")
    [ -n "${password}" ] && export REDISCLI_AUTH="${password}"
    local out
    out="$(redis-cli "${args[@]}" ping 2>/dev/null)"
    unset REDISCLI_AUTH
    [ "${out}" = "PONG" ]
    return $?
  fi
  log_warn "没找到 redis-cli，只做 TCP 端口探测（证明不了 Redis 真的能连）"
  tcp_probe "${host}" "${port}"
}

tail_api_log() {
  [ -f "${API_LOG}" ] || return 0
  printf '\n%s── 后端日志最后 30 行 ──%s\n' "${C_INFO}" "${C_OFF}" >&2
  tail -n 30 "${API_LOG}" >&2
}

# ── 步骤 1：依赖检查 ─────────────────────────────────────────────────
step_check_tools() {
  log_info "检查依赖命令"
  local missing=()
  for cmd in java mvn node npm curl; do
    command -v "${cmd}" >/dev/null 2>&1 || missing+=("${cmd}")
  done
  if [ ${#missing[@]} -gt 0 ]; then
    fail "缺少命令：${missing[*]}。装好后再跑（Java 21+、Maven 3.9+、Node 20.19+）。"
  fi
  log_ok "java $(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/') / mvn $(mvn -v 2>/dev/null | head -1 | awk '{print $3}') / node $(node -v)"
}

# ── 步骤 2：MySQL ────────────────────────────────────────────────────
step_check_mysql() {
  log_info "检查 MySQL ${DB_HOST}:${DB_PORT}"
  if check_mysql "${DB_HOST}" "${DB_PORT}" "${DB_USER}" "${DB_PASSWORD}"; then
    log_ok "MySQL 可用（库 ${DB_NAME}，用户 ${DB_USER}）"
  else
    fail "MySQL 不可用（${DB_HOST}:${DB_PORT}）。先起中间件：make up（或 docker compose up -d mysql）"
  fi
}

# ── 步骤 3：Redis ────────────────────────────────────────────────────
step_check_redis() {
  log_info "检查 Redis ${REDIS_HOST}:${REDIS_PORT}"
  if check_redis "${REDIS_HOST}" "${REDIS_PORT}" "${REDIS_PASSWORD}"; then
    log_ok "Redis 可用"
  else
    fail "Redis 不可用（${REDIS_HOST}:${REDIS_PORT}）。先起中间件：make up（或 docker compose up -d redis）"
  fi
}

# ── 步骤 4：构建后端 ─────────────────────────────────────────────────
step_build_backend() {
  if [ "${SKIP_BUILD}" -eq 1 ]; then
    log_warn "跳过构建（--skip-build）"
  else
    log_info "构建后端（mvn -pl hify-app -am package -DskipTests）"
    if ! mvn -B -pl hify-app -am package -DskipTests; then
      fail "后端构建失败"
    fi
    log_ok "构建完成"
  fi

  local jar=""
  local f
  for f in "${ROOT_DIR}"/hify-app/target/hify-app-*.jar; do
    [ -f "${f}" ] || continue
    case "${f}" in
      *.original) continue ;;
    esac
    jar="${f}"
    break
  done
  [ -n "${jar}" ] || fail "找不到后端 jar（hify-app/target/hify-app-*.jar），先跑一次 mvn package"
  echo "${jar}" > "${LOG_DIR}/.jar-path"
  log_ok "后端 jar：${jar#"${ROOT_DIR}"/}"
}

# ── 步骤 5：启动后端 ─────────────────────────────────────────────────
step_start_backend() {
  local jar
  jar="$(cat "${LOG_DIR}/.jar-path")"

  # 端口被占用就直接报错。
  # 本脚本不主动结束任何不是自己启动的进程——那多半是用户自己开的服务，
  # 误杀比启动失败更糟。只提示怎么腾出端口。
  if port_in_use "${API_PORT}"; then
    local owner
    owner="$(port_owner "${API_PORT}")"
    if [ -f "${API_PID_FILE}" ]; then
      local old_pid
      old_pid="$(cat "${API_PID_FILE}" 2>/dev/null || true)"
      if [ -n "${old_pid}" ] && ps -p "${old_pid}" >/dev/null 2>&1; then
        fail "端口 ${API_PORT} 被上次启动的后端占着（pid ${old_pid}）。先 kill ${old_pid} 再重跑。"
      fi
    fi
    fail "端口 ${API_PORT} 已被占用${owner:+（${owner}）}。先停掉它，或用 HIFY_LOCAL_API_PORT 换个端口。"
  fi

  mkdir -p "${LOG_DIR}"
  log_info "后台启动后端，端口 ${API_PORT}，日志 ${API_LOG#"${ROOT_DIR}"/}"
  nohup java -jar "${jar}" --server.port="${API_PORT}" > "${API_LOG}" 2>&1 &
  API_PID=$!
  echo "${API_PID}" > "${API_PID_FILE}"
  log_ok "后端进程已启动（pid ${API_PID}）"
}

# ── 步骤 6：轮询健康检查 ─────────────────────────────────────────────
step_wait_health() {
  log_info "等待后端健康检查通过（最多 ${HEALTH_TIMEOUT}s）"
  local url="http://localhost:${API_PORT}/actuator/health/readiness"
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    if ! ps -p "${API_PID}" >/dev/null 2>&1; then
      tail_api_log
      fail "后端进程已退出，看上面的日志"
    fi
    if curl -fsS -o /dev/null --max-time 3 "${url}" 2>/dev/null; then
      log_ok "后端就绪：${url}"
      return 0
    fi
    sleep 2
  done
  tail_api_log
  fail "健康检查超时（${HEALTH_TIMEOUT}s），后端没在预期时间内就绪"
}

# ── 步骤 7：前端 ─────────────────────────────────────────────────────
step_start_frontend() {
  cd "${ROOT_DIR}/hify-web"
  if [ ! -d node_modules ]; then
    log_info "安装前端依赖（首次）"
    npm install || fail "npm install 失败"
  fi
  if port_in_use "${WEB_PORT}"; then
    local owner
    owner="$(port_owner "${WEB_PORT}")"
    fail "端口 ${WEB_PORT} 已被占用${owner:+（${owner}）}。先停掉它，或用 HIFY_WEB_DEV_PORT 换个端口。"
  fi

  # vite 的 /api 代理要指向本次启动的后端，否则换个后端端口前端就打到别处去了
  export VITE_API_PROXY_TARGET="http://localhost:${API_PORT}"
  log_info "前端 /api 代理目标：${VITE_API_PROXY_TARGET}"

  if [ "${BACKGROUND}" -eq 1 ]; then
    # 后台模式：前端也放后台，记 pid 供 stop.sh 用
    nohup npm run dev -- --port "${WEB_PORT}" --strictPort > "${WEB_LOG}" 2>&1 &
    WEB_PID=$!
    echo "${WEB_PID}" > "${WEB_PID_FILE}"
    log_info "前端进程已启动（pid ${WEB_PID}），日志 ${WEB_LOG#"${ROOT_DIR}"/}"
    wait_port "${WEB_PORT}" 30 || fail "前端开发服务器没在预期时间内监听 ${WEB_PORT} 端口"
    log_ok "前端就绪：http://localhost:${WEB_PORT}"
  fi

  printf '\n'
  log_ok "────────────────────────────────────────────"
  log_ok "  前端开发服务器：http://localhost:${WEB_PORT}"
  log_ok "  后端接口：      http://localhost:${API_PORT}"
  log_ok "  后端日志：      ${API_LOG#"${ROOT_DIR}"/}"
  if [ "${BACKGROUND}" -eq 1 ]; then
    log_ok "  前端日志：      ${WEB_LOG#"${ROOT_DIR}"/}"
    log_ok "  停止全部：      ./stop.sh"
  else
    log_ok "  按 Ctrl-C 停止（后端会一起停）"
  fi
  log_ok "────────────────────────────────────────────"
  printf '\n'

  if [ "${BACKGROUND}" -eq 0 ]; then
    # 前台运行：vite 的输出仍然打在终端上，用 wait 等它结束。
    # 放后台再 wait 是为了拿到它的 pid 写进 pid 文件——stop.sh 要用。
    npm run dev -- --port "${WEB_PORT}" --strictPort &
    WEB_PID=$!
    echo "${WEB_PID}" > "${WEB_PID_FILE}"
    local rc=0
    wait "${WEB_PID}" || rc=$?
    # 130/143 是被 Ctrl-C / TERM 打断，属于正常退出
    if [ "${rc}" -ne 0 ] && [ "${rc}" -ne 130 ] && [ "${rc}" -ne 143 ]; then
      fail "前端开发服务器退出（code ${rc}）"
    fi
  fi
}

# ── 主流程 ───────────────────────────────────────────────────────────
main() {
  printf '\n%s=== Hify 本地开发环境 ===%s\n\n' "${C_INFO}" "${C_OFF}"
  step_check_tools
  step_check_mysql
  step_check_redis
  step_build_backend
  step_start_backend
  step_wait_health
  step_start_frontend
}

main "$@"

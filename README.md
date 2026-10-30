# Hify

可本地部署的 AI Agent 开发平台：多模型管理、Agent 配置、知识库 RAG、简版工作流、对话交互、MCP 工具接入。

## 快速开始

```bash
# 1. 起中间件（MySQL + pgvector + Redis，走 docker compose）
make up

# 2. 起后端 + 前端
make start
```

`make start` 等价于 `./start.sh`：依次做四件事——**检查 MySQL/Redis → 构建并后台启动后端 → 轮询健康检查通过 → 启动前端 dev server**。
任何一步失败都会停下并说明原因。后端日志在 `logs/api.log`，Ctrl-C 会把后端一起停掉。

浏览器打开 <http://localhost:5173>，后端在 <http://localhost:8080>。

也可以用 Makefile：

```bash
make up         # 起中间件
make down       # 停中间件
make start      # 后台起应用（后端 + 前端）
make stop       # 优雅停掉应用
make restart    # 重启应用
make build      # 构建后端（含测试）+ 前端
make package    # 打成 dist/hify-<版本>.tar.gz
make            # 看全部命令
```

只改了前端代码时可以跳过构建：`./start.sh --skip-build`。

想直接跑容器化的全栈（前端用 nginx 托管静态产物，连后端也用容器）：

```bash
docker compose up -d --build
# 浏览器 http://localhost:8083
```

## 端口

| 服务 | 宿主端口 | 说明 |
|---|---|---|
| MySQL | 3308 | 标准端口被本机既有容器栈占用，容器内部仍是 3306 |
| PostgreSQL (pgvector) | 5433 | |
| Redis | 6380 | |
| api | 8082 | compose 容器；`start.sh` 起的本地进程用 8080 |
| web | 8083 | compose 里的 nginx |

端口与凭据都可以用根目录的 `.env` 覆盖（模板见 `.env.example`），`start.sh` 与 `docker-compose.yml` 读的是同一份。

## 文档

- [`CLAUDE.md`](CLAUDE.md) —— 项目定位、架构、编码规范、关键决策
- `docs/` —— 设计文档与规范
- [`hify-web/README.md`](hify-web/README.md) —— 前端工程说明

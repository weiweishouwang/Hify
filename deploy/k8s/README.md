# Hify 生产部署清单（Kubernetes）

> 这份清单是 `docs/superpowers/specs/2026-10-29-hify-deployment.md` 的可执行版本。
> 每个值在那份文档里都有出处 —— **改之前先读它**，别凭感觉调。

## 用法

```bash
# 1. 先建密钥（不要用 02-secret.example.yaml 里的假值）
kubectl -n hify create secret generic hify-secret \
  --from-literal=HIFY_DB_PASSWORD='...' \
  --from-literal=HIFY_DB_ROOT_PASSWORD='...' \
  --from-literal=HIFY_PG_PASSWORD='...' \
  --from-literal=HIFY_REDIS_PASSWORD='...' \
  --from-literal=HIFY_CREDENTIAL_ENCRYPTION_KEY='$(openssl rand -base64 32)'

# 2. 改掉 01-configmap.yaml 里的域名与 CORS 来源，改掉镜像 tag

# 3. 一次应用
kubectl apply -f deploy/k8s/
```

`kubectl apply -f` 会按文件名顺序应用，命名空间 → 配置 → 工作负载 → 路由。
`02-secret.example.yaml` **不要 apply** —— 它的值是假值，只用来让清单能过 dry-run。

## 文件一览

| 文件 | 内容 | 要不要改 |
|---|---|---|
| `00-namespace.yaml` | 命名空间 | 一般不改 |
| `01-configmap.yaml` | 非敏感配置（库地址、CORS 来源、日志目录） | **要改**：域名、CORS 来源 |
| `02-secret.example.yaml` | 密钥模板 | **只作参考** |
| `10-api-deployment.yaml` | 3 副本、探针、preStop、资源限额 | 镜像 tag |
| `11-api-service.yaml` | api 的内部服务 | 不改 |
| `12-api-pdb.yaml` | 至少留一个副本可用 | 不改 |
| `20-web-deployment.yaml` | 2 副本前端 nginx | 镜像 tag |
| `21-web-service.yaml` | web 的内部服务 | 不改 |
| `30-ingress.yaml` | TLS + 路由 + SSE 注解 | **要改**：域名、TLS secret |
| `40-networkpolicy.yaml` | 出网白名单 | **要改**：LLM 供应商的域名 |
| `50-statefulset-storage.yaml` | 自建 MySQL / PG / Redis | **用云托管就删掉** |
| `60-backup-cronjob.yaml` | 定时 dump | 存储后端按环境补 |

## 两种路由拓扑，别混着配

| 拓扑 | `/api` 走哪 | 前端与 api | CORS |
|---|---|---|---|
| **方案 A（本清单默认）** | Ingress 直接路由到 `hify-api` | 不同源 | **要配** `HIFY_CORS_ALLOWED_ORIGINS` |
| 方案 B | web 容器里的 nginx 反代 | 同源 | 不用配 |

方案 A 下 SSE 少走一跳 nginx，少一层配置、少一个出错点；代价是跨域。
`hify-web/templates/default.conf.template` 里的 `/api` 反代在方案 A 下用不上，
留着是为了直连 web Service 排障时仍然能用。

## SSE 的三个必配项

1. **Ingress 的 `proxy-buffering: off`** —— 不配的话前端看到的是「等很久，然后一次性全部到达」，
   很容易被误判成「模型慢」。内层 nginx（`hify-web` 的模板）也配了同一项，两边都配才安全。
2. **`terminationGracePeriodSeconds: 180` + `preStop: sleep 10`** —— 前者给最长生成时长留窗口，
   后者等 Endpoint 摘除传播完（不等会有一批 502）。
3. **liveness 只查 `livenessState`** —— 绑了 db 的话，MySQL 一抖就把整个 Deployment 重启，
   所有进行中的对话被一次性掐断。readiness 才查 db。

## 存储：优先云托管

`50-statefulset-storage.yaml` 只在公司**没有**托管实例时用。数据库的备份、主从切换、
版本升级、监控告警是一整套运维工作，50 人内部工具不值得自己扛。

自建的话三样东西必须配齐：PVC（`Retain` 策略）、定时备份（`60-backup-cronjob.yaml`）、
明确的恢复预案（谁、什么情况下、执行什么命令）。

**恢复演练要做两遍**：只恢复 MySQL 会让知识库元数据与向量不一致 —— 检索要么返回空，
要么返回不存在的分段。

## 密钥

- `HIFY_CREDENTIAL_ENCRYPTION_KEY` 是 Base64 编码的 32 字节，给接入点、工具、MCP 服务的凭据做
  AES-GCM 加密。**它一换，历史密文就解不开了**（封套的 keyId 绑在 AAD 上，改不得）。
- Secret 是 base64 编码，不是加密。K8s 1.21+ 默认开了 etcd 静态加密，但更稳的做法是用
  External Secrets Operator 从密钥平台同步。

## 扩缩容

**固定 3 副本，不做 HPA。** SSE 是 IO 密集，占满连接数时 CPU 可能只有 20%，按 CPU 扩容永不触发。
真要扩容，改 `replicas` 重新 apply 即可。

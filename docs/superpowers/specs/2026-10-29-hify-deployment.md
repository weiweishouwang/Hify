# Hify 部署架构（当前阶段）

> 规模：50 人内部使用
> 运行时：Docker + Kubernetes
> 应用形态：模块化单体（Spring Boot）+ Vue 3 前端
> 状态：MySQL（业务）+ PostgreSQL/pgvector（向量）+ Redis（缓存/限流/锁）
> 开发环境：Docker Compose（与生产共用同一套环境变量）

---

## 1. 架构总览

```
                         浏览器
                           │ HTTPS
                           ▼
              ┌─────────────────────────────┐
              │   Ingress (nginx-ingress)   │  TLS 终止 / 路由 / 超时与缓冲策略
              │   · /          → hify-web   │
              │   · /api/v1/** → hify-api   │
              └──────────────┬──────────────┘
                             │ ClusterIP
              ┌──────────────▼──────────────┐
              │      hify-api (Deployment)    │
              │  副本 2–3 · 无状态 · 优雅停机  │
              │  · SSE 对话流（长连接）        │
              │  · REST 管理接口              │
              └──┬────────┬────────┬─────────┘
                 │        │        │
      ┌──────────▼──┐ ┌───▼─────┐ ┌▼──────────────┐
      │  MySQL      │ │ Redis   │ │ PostgreSQL     │
      │  (业务数据) │ │ (缓存)  │ │ + pgvector     │
      │  StatefulSet│ │Stateful │ │ (向量)         │
      └─────────────┘ └─────────┘ └───────────────┘
                                              │
              ┌───────────────────────────────┘
              │ egress（集群外）
              ▼
        OpenAI / Claude / Gemini
        （Ollama 走 ClusterIP 内部服务）
```

**关键点**：整个系统只有 **hify-api 一个有状态计算单元**，其余都是无状态的或有状态的存储。50 人规模下，这个拓扑的 Pod 总数约 6–8 个。

---

## 2. 组件清单

| 组件 | K8s 资源 | 副本 | 职责 | 状态 |
|---|---|---:|---|---|
| **Ingress** | Ingress + nginx-ingress Controller | — | TLS 终止、路由、**SSE 专用的超时与缓冲策略** | 无状态 |
| **hify-web** | Deployment | 2 | Vue 静态资源 + nginx 反代到 hify-api（合并同源，避免 CORS） | 无状态 |
| **hify-api** | Deployment | 2–3 | Spring Boot 主应用：编排画布、对话 SSE、工作流引擎、MCP 客户端、知识库检索 | 无状态 |
| **hify-mysql** | StatefulSet 或**云托管 RDS** | 1 | 业务数据：模型接入点、Agent 配置、会话与消息、工作流定义、MCP server 配置 | 有状态 |
| **hify-postgres** | StatefulSet 或**云托管 PG** | 1 | 向量库：知识库 chunk + embedding + 向量索引（pgvector） | 有状态 |
| **hify-redis** | StatefulSet 或**云托管 Redis** | 1 | 缓存、限流计数、分布式锁、幂等键 | 有状态（建议持久化） |
| **hify-ollama**（可选） | Deployment / 独立节点 | 1 | 本地模型推理。**独立于 hify-api**，不塞进 API 容器 | 有状态（模型驻留显存） |
| ConfigMap | ConfigMap | — | 非敏感配置：LLM 超时与连接池、路由前缀、开关 | — |
| Secret | Secret（External Secrets 更好） | — | 模型 API Key、MCP token、DB 密码、JWT 密钥 | — |
| Service | ClusterIP | — | api / mysql / postgres / redis 的内部 DNS | — |
| PDB | PodDisruptionBudget | — | 保证滚动更新与节点维护期间至少 1 个副本可用 | — |
| 备份 | CronJob | — | MySQL / PG 的定时 dump → 对象存储 | — |

### 关于有状态组件

**优先用云托管实例**（RDS / 云数据库 / 云 Redis），如果公司有。原因：数据库的备份、主从切换、版本升级、监控告警是一整套运维工作，50 人内部工具不值得自己扛。

只有当公司没有托管服务时才自建 StatefulSet，并且必须配：
- PVC（`ReadWriteOnce`，`Retain` 策略）
- 定时备份 CronJob
- 明确恢复预案（谁在什么情况下执行什么命令）

### Redis 的一个硬约束

**淘汰策略是实例级的**，不能按逻辑库区分。所以：

- 用 `allkeys-lru`（纯缓存场景）→ 锁键和幂等键可能被淘汰，导致**锁失效、幂等失效**
- 用 `noeviction`（正确性场景）→ 缓存键堆积会触发 OOM

**推荐：两个 Redis 实例**（或至少给 Redis 留足内存 + 显式 TTL）。如果只有一套，用 `noeviction` + 严格的 TTL + 内存告警。

---

## 3. 请求流转

### 3.1 静态资源

```
浏览器 → Ingress → hify-web (nginx) → 静态文件（带 hash 长缓存）
```

### 3.2 管理接口（REST，短请求）

```
浏览器 → Ingress → hify-api (Pod A)
                    ├─ 读 Redis（模型配置、Agent 配置缓存）
                    ├─ 读/写 MySQL
                    └─ 返回 JSON
```

### 3.3 对话流（SSE，长连接）—— 需要特殊对待

```
浏览器 → Ingress → hify-api (Pod A)
   │        │           │
   │        │           ├─ 读 MySQL：加载 Agent 配置、会话历史
   │        │           ├─ 读 Redis：模型配置缓存
   │        │           ├─ 读 Postgres/pgvector：知识库检索 top-k
   │        │           │
   │        │           └─ 出集群 → LLM API（SSE 上游）
   │        │                 │
   │        │◄── chunk 流 ────┘
   │        │
   │        └─ 逐 chunk 写回浏览器（publishOn → boundedElastic 线程）
   │
   └◄── SSE 帧流（可能持续 30–120 秒）
```

**这条路径有三个与普通 REST 不同的约束**：

1. **连接在 Ingress 上停留很久** → 必须调大 `proxy-read-timeout`，且关闭 `proxy_buffering`
2. **Pod 滚动更新时连接会被切断** → 必须有优雅停机（见 §6.3）
3. **上游 LLM 调用占用 Pod 资源整个生成期** → 副本数决定了同时能跑多少条流

### 3.4 文件上传（知识库 TXT）

```
浏览器 → Ingress → hify-api → 写入存储 → 异步任务切分 + embedding → Postgres
```

上传的原文存哪：
- **简单方案**：存 MySQL 的 `longblob`（知识库体积小，50 人规模足够）
- **稳妥方案**：对象存储（公司现有 MinIO / S3 兼容服务）

**不要**用容器本地目录存上传的文件——Pod 重建就没了。需要持久化就用上面的方案。

---

## 4. SSE 在 Kubernetes 上的六个坑

这是把 Hify 部署到 K8s 后最需要专门处理的部分。

### 坑 1：Ingress 缓冲会让流式变成非流式

```yaml
metadata:
  annotations:
    nginx.ingress.kubernetes.io/proxy-buffering: "off"        # 必须
    nginx.ingress.kubernetes.io/proxy-request-buffering: "off" # POST 体也不缓冲
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"      # 默认 60s 会掐断长生成
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-body-size: "20m"          # 知识库上传
```

**症状**：不加 `proxy-buffering off`，前端看到的是"等很久，然后一次性全部到达"。这是最容易被误判成"模型慢"的问题。

### 坑 2：客户端 HTTP/1.1 的 6 连接上限

浏览器对同一域名最多 6 个 HTTP/1.1 并发连接。SSE 占住一个不放，**用户开 3 个对话标签页就开始互相阻塞**，表现为"流式随机卡住"。

**解法**：确认 ingress 面向客户端启用了 HTTP/2：

```yaml
# ingress-nginx ConfigMap
data:
  use-http2: "true"     # 面向浏览器的 HTTP/2（默认多为 true，需确认）
```

启用后浏览器用一条 h2 连接复用多个流，此问题消失。**上游**是否用 h2 是另一回事（`backend-protocol: HTTP2`）。

### 坑 3：滚动更新会切断所有进行中的对话

见 §6.3 的优雅停机配置。`terminationGracePeriodSeconds` 必须 ≥ 最长可能的生成时长。

### 坑 4：`maxUnavailable` 默认值会同时干掉两个副本

默认 `maxUnavailable: 25%`，3 副本时滚动更新会先停 1 个——问题不大；但配合 PDB 要显式写清楚。

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0      # 先起新的再停旧的，滚动期间容量不降
```

### 坑 5：liveness 探针太严会在依赖抖动时集体重启

SSE 应用的长连接会让 `/actuator/health` 的响应变慢吗？不一定——但数据库连接池耗尽时健康检查会失败，如果 liveness 绑定了 DB 检查，**整个 Deployment 会被重启**，把所有进行中的对话一次性掐断。

**必须分开**：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true       # 暴露 /actuator/health/liveness 与 /readiness
      group:
        liveness:
          include: livenessState     # 只看进程自己活着，不看依赖
        readiness:
          include: readinessState,db,redis
```

- **liveness** 只包含 `livenessState`——依赖挂了不该重启进程（重启也修不好）
- **readiness** 包含 DB/Redis——依赖挂了就不接新流量，但已有连接继续

### 坑 6：CPU 限制触发 OOMKill 会随机杀 Pod

JVM 堆 512m，但容器内存还包含 Metaspace、线程栈、Netty 的 DirectBuffer。**容器 limit 必须是堆的 2 倍**。

```yaml
env:
  - name: JAVA_TOOL_OPTIONS
    value: >-
      -Xmx512m
      -XX:MaxMetaspaceSize=128m
      -XX:MaxDirectMemorySize=128m
      -XX:+ExitOnOutOfMemoryError
      -Djava.security.egd=file:/dev/./urandom
resources:
  requests:
    memory: 768Mi
    cpu: 500m
  limits:
    memory: 1Gi          # ← 必须 > Xmx + metaspace + 线程栈 + direct
    cpu: 2000m
```

`MaxDirectMemorySize` 别漏——Reactor Netty 的 DirectBuffer 在堆外，不设上限会在流量高峰撑爆容器。

---

## 5. MCP 在 K8s 下的特殊性

**这是一个 K8s 才出现、Compose 下不存在的问题。**

stdio 传输的 MCP server 是 **Hify 在本机 spawn 起的子进程**。在 K8s 里：

- 每个 Pod 各 spawn 一份 → **3 个副本 = 3 个 MCP 进程，状态互不相同**
- Pod 漂移/重建后进程状态丢失
- 如果 MCP server 是有状态的（如 filesystem、memory server），副本之间行为不一致

**推荐方案：MCP server 独立部署，用 HTTP transport 接入**

```
hify-mcp-filesystem (Deployment, 独立)
        │  ClusterIP HTTP
        ▼
hify-api（配置里填 http://hify-mcp-filesystem:8080/mcp）
```

这样 MCP server 变成一个标准的服务，Hify 只是它的客户端——**这恰好就是之前规范里"MCP 工具来源"设计的兑现方式**。

如果确实要用 stdio：
- 只允许**无状态**的 MCP server
- 在管理界面显式标注"stdio 模式，每个副本独立运行"
- 只允许管理员配置（这条规范里已有：stdio 会在服务器启动子进程）

---

## 6. 关键 K8s 配置

### 6.1 hify-api Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hify-api
  namespace: hify
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels: { app: hify-api }
    spec:
      # 给 Ingress 摘除 Endpoint 的传播时间，再开始关停
      terminationGracePeriodSeconds: 180
      containers:
        - name: api
          image: registry.internal/hify/api:1.0.0
          ports:
            - { name: http, containerPort: 8080 }
          envFrom:
            - configMapRef: { name: hify-config }
            - secretRef: { name: hify-secret }
          env:
            - name: SERVER_FORWARD_HEADERS_STRATEGY
              value: framework
            - name: JAVA_TOOL_OPTIONS
              value: >-
                -Xmx512m
                -XX:MaxMetaspaceSize=128m
                -XX:MaxDirectMemorySize=128m
                -XX:+ExitOnOutOfMemoryError
                -XX:+UseG1GC
                -XX:MaxGCPauseMillis=100
                -Djava.security.egd=file:/dev/./urandom
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 10"]   # 等 Endpoint 摘除生效
          startupProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            failureThreshold: 30
            periodSeconds: 5
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
            failureThreshold: 2
          resources:
            requests: { memory: 1024Mi, cpu: 500m }
            limits:   { memory: 1536Mi, cpu: 2000m }   # 见 §13 的 native 内存预算
```

**`preStop: sleep 10` 的作用**：K8s 收到删除信号时，先发 SIGTERM 再执行 preStop hook——但 Endpoint 摘除是异步的。如果不等一下，Ingress 会在应用已经停止接受新连接后，仍然往这个 Pod 转发新请求，造成一批 502。睡 10 秒让摘除传播完成。

### 6.2 PodDisruptionBudget

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: hify-api
  namespace: hify
spec:
  minAvailable: 1
  selector:
    matchLabels: { app: hify-api }
```

保证节点维护或滚动更新时至少有一个副本存活。

### 6.3 优雅停机的完整时序

```
kubectl delete / 滚动更新
  ↓
① Pod 进入 Terminating，Endpoint 立刻被标记为 not ready
   （但已建立的 TCP/SSE 连接不会断——这是 K8s 的语义）
② preStop 执行 sleep 10（给摘除传播时间）
③ SIGTERM 发给 Spring Boot
   → Spring 开始 graceful shutdown：停止接受新请求，等待进行中的请求完成
   → Actuator readiness 转 DOWN
   → 存量的 SSE 连接继续推流，最多到 180s（terminationGracePeriodSeconds）
④ 180s 到期仍未结束 → SIGKILL，连接被切断
```

**因此 `terminationGracePeriodSeconds` 必须 ≥ 最长生成时长 + 缓冲**。120s 的生成上限 → 配 180s。

### 6.4 Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hify
  namespace: hify
  annotations:
    nginx.ingress.kubernetes.io/proxy-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-request-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-body-size: "20m"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
    - hosts: [hify.internal.company.com]
      secretName: hify-tls
  rules:
    - host: hify.internal.company.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service: { name: hify-api, port: { number: 80 } }
          - path: /
            pathType: Prefix
            backend:
              service: { name: hify-web, port: { number: 80 } }
```

---

## 7. 配置与密钥

### 7.1 配置单一来源

**开发和生产共用同一套环境变量名**，只是注入方式不同：

```
# 开发：.env 文件（Docker Compose）
# 生产：ConfigMap + Secret
HIFY_LLM_PROVIDERS_OPENAI_BASE_URL=https://api.openai.com/v1
HIFY_LLM_PROVIDERS_OPENAI_API_KEY=...
HIFY_SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/hify
HIFY_SPRING_DATASOURCE_USERNAME=hify
HIFY_SPRING_DATASOURCE_PASSWORD=...
```

Spring Boot 的 relaxed binding 让 `HIFY_LLM_PROVIDERS_OPENAI_BASE_URL` 映射到 `hify.llm.providers.openai.base-url`。**两边配置零漂移**，不会出现"开发好好的，生产起不来因为配置名不一样"。

### 7.2 密钥管理

| 密钥 | 存放 |
|---|---|
| 模型 API Key（OpenAI/Claude/Gemini） | Secret，最好用 External Secrets 从 Vault/密钥平台同步 |
| MCP server token | Secret |
| MySQL / PG 密码 | Secret |
| JWT 签名密钥 | Secret |

**Secret 是 base64 编码，不是加密**。K8s 1.21+ 默认开启 etcd 静态加密，但更稳的做法是用 **External Secrets Operator** 从公司的密钥管理系统同步，Secret 里只存引用。

**应用侧**：密钥落库必须加密存储（AES + 主密钥来自环境变量），前端永不回显完整 Key。

### 7.3 配置变更的生效方式

- **非敏感配置**：改 ConfigMap → 触发 `kubectl rollout restart deployment/hify-api`
- **敏感配置**：改 Secret → 同上（Secret 不会自动热更新）

**不要为了追求热更新而引入 Spring Cloud Config**——50 人内部工具，重启一次 Pod 也就几秒。

---

## 8. 扩缩容

### 8.1 当前阶段：固定副本，不做 HPA

50 人 → 峰值 3–5 QPS、并发 SSE 约 25–50 条。**3 个副本绰绰有余**。

HPA 在这个规模是纯粹的复杂度：
- SSE 应用是 **I/O 密集**，CPU 指标反映不出并发压力（占满连接数时 CPU 可能只有 20%）
- 按 CPU 扩容会**永不触发**；按并发 SSE 扩容需要 prometheus-adapter 把自定义指标接进 HPA（额外部署一套组件）

**推荐：固定 `replicas: 3`**。真需要扩容时，改一个字段重新 apply 即可。

### 8.2 将来真要 HPA 的正确指标

`llm.inflight`（在途 LLM 调用数）或并发 SSE 连接数，通过 Micrometer → Prometheus → prometheus-adapter → HPA。

**不要用 CPU。**

### 8.3 扩容前提（现在就要满足）

hify-api 必须是**完全无状态**的：
- 会话状态在 MySQL
- 模型/Agent 配置缓存放 Redis（**不要用本地 Caffeine**，多副本会不一致）
- 限流计数放 Redis（不要用本地计数）
- 幂等键放 Redis
- 分布式锁用 Redis（不要用本地锁）
- **MCP session 复用要小心**——stdio 模式每副本独立，HTTP 模式可以连同一个 MCP 服务

这几条现在不做，将来扩容时会出现"单机好好的，上了两台就出灵异 bug"。

---

## 9. 可观测性

### 9.1 必备组件

| 组件 | 作用 |
|---|---|
| Prometheus + ServiceMonitor | 采集 Actuator/Micrometer 指标 |
| Grafana | 看板 |
| 日志采集（集群已有） | stdout → 采集 |

### 9.2 关键指标与告警

| 指标 | 告警阈值建议 | 意义 |
|---|---|---|
| `hify_websocket_sse_active` 或自建并发 SSE 数 | > 60 | 接近副本承载上限 |
| `llm_first_token_seconds` P95 | > 10s | 用户体感应变差 |
| `llm_circuit_state == OPEN` | 持续 > 2min | 某 provider 挂了 |
| `hikaricp_connections_pending` | > 0 持续 1min | 连接池打满 |
| `llm_retries_total` 速率 | 突增 | 上游抖动 |
| `jvm_memory_used` / `container_memory_working_set` | > 85% limit | 逼近 OOMKill |
| `tomcat_threads_busy` | 长期接近 max | 线程池打满 |

### 9.3 Actuator 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never     # 不要在公网暴露依赖详情
  metrics:
    tags:
      application: hify
```

**不要暴露 `env`、`heapdump`、`threaddump`、`configprops`** 到集群外——`env` 会泄露环境变量里的密钥。

---

## 10. 开发环境（Compose）与生产（K8s）

生产用 K8s，**开发不要用 K8s**（minikube/kind 的启动慢、资源占用大、调试麻烦）。

用 Docker Compose 作开发环境，两者共用同一套镜像和同一套环境变量名：

```yaml
# docker-compose.yml（开发）
services:
  mysql:    { image: mysql:8.4, ports: ["3306:3306"], volumes: [mysql-data:/var/lib/mysql] }
  postgres: { image: pgvector/pgvector:pg16, ports: ["5432:5432"], volumes: [pg-data:/var/lib/postgresql/data] }
  redis:    { image: redis:7, ports: ["6379:6379"] }
  api:      { build: ., env_file: [.env], ports: ["8080:8080"], depends_on: [mysql, postgres, redis] }
  web:      { build: ../web, ports: ["80:80"] }
volumes: { mysql-data: {}, pg-data: {} }
```

`pgvector/pgvector:pg16` 是官方维护的镜像，开箱带 pgvector 扩展。

**配置一致性**：`.env`（开发）与 ConfigMap/Secret（生产）里用**相同的 key 名**。差异只在值（开发用本地地址，生产用 Service DNS 名）。

---

## 11. 成本与取舍

**50 人用 K8s 是"偏重"的，但有三个理由让它合理**：

1. **组织约束**：如果公司平台要求所有内部服务上 K8s，这不是技术选择，是准入条件
2. **兑现"为扩容留口子"**：之前定的"模块化单体 + 将来拆微服务"，K8s 就是那些未来服务的落点——现在就把 stateless、优雅停机、健康检查、配置外置做对，将来拆出去几乎零改造
3. **多环境一致性**：开发/测试/生产用同一套编排

**如果 K8s 不是硬性要求**，我的建议是：

| 阶段 | 方案 |
|---|---|
| 现在（50 人） | Docker Compose 单机，4–6 个容器，`docker compose up` 一键起 |
| 500 人 | 同样 Compose，加副本（Compose 的 `deploy.replicas` 在非 Swarm 模式下不生效，需改用 Nginx upstream 轮询） |
| 几千人 | 迁 K8s |

但**开发环境用 Compose、生产环境用 K8s** 的组合在任何阶段都是对的——这是本文档 §10 的结构。

---

## 12. 双库已确认：MySQL（业务）+ PostgreSQL/pgvector（向量）

### 12.1 职责边界

| 库 | 内容 | 表前缀 |
|---|---|---|
| **MySQL** | 模型接入点、Agent 配置、会话与消息、工作流定义、MCP server 配置、工具配置、知识库**元数据**（文档、分段参数、状态） | `ai_` `wf_` `conv_` `mcp_` `tool_` `kb_` |
| **PostgreSQL** | 知识库 chunk 正文 + embedding + 向量索引 | `vec_` |

知识库的"元数据"和"内容"分属两库：MySQL 的 `kb_document` 记录文档状态（UPLOADED / INDEXING / READY / FAILED / DELETING），PG 的 `vec_chunk` 存实际分段文本与向量。

### 12.2 向量检索改用 pgvector

这一条**取代**了早先"MySQL 8.x 无向量能力 → 应用层暴力检索"的方案——既然已有独立的 PG 向量库，就该用 pgvector 的索引能力，而不是在 Java 里算 cosine。

```sql
CREATE TABLE vec_chunk (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       VARCHAR(64)  NOT NULL,
    doc_id      VARCHAR(64)  NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    embedding   vector(1536) NOT NULL,
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (doc_id, chunk_index)
);

CREATE INDEX vec_chunk_embedding_hnsw
    ON vec_chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

检索：

```sql
SELECT id, doc_id, chunk_index, content, metadata,
       embedding <=> :queryVector AS distance
FROM vec_chunk
WHERE kb_id = :kbId
ORDER BY embedding <=> :queryVector
LIMIT :topK;
```

**`<=>` 是余弦距离运算符**，HNSW 索引要求运算符与索引定义一致（`vector_cosine_ops` ↔ `<=>`）。若换用内积或 L2，索引定义也要跟着换。

**HNSW 的 `m` 与 `ef_construction` 在建索引时确定，建好后修改需要重建整个索引**——上线前用真实数据量做一次基准再定值。检索时的 `ef_search` 是查询参数，可运行时调。

### 12.3 跨库一致性：写入必须可重入

两库之间没有分布式事务，所以知识库的写入流程必须设计成**可重入**的。

**索引流程**

1. MySQL 写 `kb_document`（status=INDEXING）
2. 切分 → 调 embedding → 写 PG `vec_chunk`
   （`UNIQUE (doc_id, chunk_index)` 保证幂等，冲突则覆盖）
3. MySQL 更新 status=READY

任一步崩溃：MySQL 停在 INDEXING，由定时任务扫出并重跑。因为 PG 侧写入是 upsert，**重跑安全**。

**删除流程（顺序不能反）**

1. MySQL 软删除 `kb_document`（status=DELETING）
2. 异步删除 PG 的 `vec_chunk`（按 doc_id）
3. MySQL 硬删除行

**先软删 MySQL 再删 PG**，理由：中途崩溃时，PG 里留下的是孤儿分段——浪费存储但不影响功能，可由 GC 任务按 `kb_document` 反查清理。反过来（先删 PG）中途崩溃，会导致 MySQL 指向不存在的分段，**检索静默返回空**，更难排查。

### 12.4 两个连接池

MySQL 与 PG 各一个 HikariCP 数据源，各 `maximum-pool-size` 8–10 即可（两个库负载都轻）。注意**两库连接数叠加**，别超过 MySQL 的 `max_connections`。

**PG 访问不使用 MyBatis-Plus**：向量检索要用 pgvector 的 `<=>` 运算符和 HNSW 索引，MP 的 `BaseMapper` 生成不了这类 SQL。PG 侧统一用 `NamedParameterJdbcTemplate` 手写。

### 12.5 迁移与备份

两套迁移脚本（MySQL 与 PG 各一套 Flyway）、两套备份 CronJob。

**恢复演练要做两遍**：只恢复 MySQL 会让知识库元数据与向量不一致——检索要么返回空，要么返回不存在的分段。

### 12.6 已否决的备选：单库（整体换 PostgreSQL）

曾评估过整体换成 PostgreSQL（业务 + 向量同库），**已决定不采用**，理由记录在此：

- MySQL 是团队既有技术栈，运维工具与经验都在这一侧
- 向量库独立成 Service 后，未来若要换向量方案（Qdrant / Milvus）只需替换 `knowledge` 模块的数据访问实现，不影响其他模块

代价是组件多一个、备份多一套——这个代价是已知且接受的。

## 13. 容量规划与调优后的配置基线

本节给出按 50 人规模（峰值 3–5 QPS、100–150 并发 SSE、3 副本）算过的配置值，以及每个值背后的推导。**改配置前先读这张表**——有几个值是从容器 CPU limit 间接算出来的，改一个数字会悄悄改变另一个。

### 13.1 native 内存预算（容器 limit 不是堆）

| 项 | 值 | 说明 |
|---|---:|---|
| JVM 堆 | 512m | `MaxRAMPercentage` 不设，显式 `-Xmx` |
| Metaspace | ≤128m | 必须设上限，否则无界增长会撑爆容器 |
| DirectBuffer | ≤128m | Reactor Netty 的池化 buffer 在堆外，不设上限同样是坑 |
| Tomcat 线程栈 | 200 × 1MB = 200m | 保持默认 200 |
| 业务线程栈 | 48 × 1MB = 48m | boundedElastic + MCP + JDBC offload |
| JVM 其他 native | ~50m | |
| **合计** | **~1066m** | |
| **容器 limit** | **1536Mi** | **必须 ≥ 堆 + native，约为堆的 3 倍** |

`1Gi` 装不下（1066m 已超过 1024m）。若集群内存配额紧张，**先把堆降到 384m**（合计约 938m），而不是压线程数。

**`requests.memory` 设为 1024Mi 而非 768Mi**：requests 决定调度密度。若按 768Mi 排 Pod，一台 8Gi 的节点能排进 10 个，但每个 Pod 的 limit 是 1536Mi（合计 15Gi），节点内存会先于容器 OOM。**request 应按 limit 的 2/3 设，留出调度余量。**

> 线程栈是**保留**的虚拟地址空间，容器 limit 统计的是 RSS（实际占用），实际值通常低于上表——但按保留量做预算是更保守的做法。
>
> **Tomcat 线程保持默认 200 不调低**：正常情况下 SSE 不占 Tomcat 线程，200 几乎不会被用到；但保留这个余量的价值在于——一旦某处漏了 `subscribeOn` offload、有阻塞调用跑在 Tomcat 线程上，200 的余量能让故障以"变慢"的形式暴露，而不是"全面拒绝服务"。线程数是最后一道缓冲，不该提前拆掉。

### 13.2 线程配置

**Tomcat 线程保持默认 200**，不调低（理由见 §13.1）。

**boundedElastic 必须显式配置，不能用默认值。**

```
默认 = 10 × availableProcessors()
容器 limits.cpu = 2000m → availableProcessors() = 2 → 默认只有 20 线程
```

这 20 个线程正是 SSE 的写出线程池。正常情况利用率约 2%，**但一个慢客户端的阻塞写就占住一个，20 个慢客户端能让全部 SSE 停摆，而 CPU 指标完全正常**。所以：

```java
/** SSE 写出专用调度器：有界线程 + 有界队列 */
public static final Scheduler SSE_WRITE_SCHEDULER = Schedulers.newBoundedElastic(
        48,                      // maxThreads
        1000,                    // maxTaskQueue 必须有界（默认 10 万无界，会变成延迟堆积）
        60, TimeUnit.SECONDS);   // ttl

// Controller 里
return llmGateway.stream(chatId)
        .map(chunk -> ServerSentEvent.builder().event(chunk.type()).data(chunk).build())
        .publishOn(SSE_WRITE_SCHEDULER);
```

**不要靠调高 CPU limit 来"解决"线程池问题**——CPU limit 是资源决策，线程池是正确性决策，两者必须解耦。

### 13.3 LLM 连接池

| provider | maxConnections | pendingAcquireTimeout | 依据 |
|---|---:|---:|---|
| openai / claude / gemini | **48** | 5s | 峰值在途生成约 90 条、3 副本均摊约 30 条/副本，48 留 60% 不均衡余量 |
| ollama | **6** | 10s | 受显存限制，并发过高会触发模型换出换入 |

```
单 provider 总连接数 = 3 副本 × 48 = 144
```

如果 provider 侧的并发流上限低于 144，需要下调 `maxConnections` 并接受排队——但此时 `pendingAcquireTimeout` 要相应放宽（队列等待应给用户"稍等"的提示，而不是 5 秒后直接失败）。

**`pendingAcquireMaxCount` 保持 `2 × maxConnections`**（默认即如此），不要放大。

### 13.4 每用户并发上限（防单用户吃掉整个池）

3 副本下，一个用户开 10 个标签页会在 3 个 Pod 上各占一条连接，池的隔离被绕过。用 Redis 计数：

```lua
-- acquire.lua
local cur = redis.call('INCR', KEYS[1])
if cur == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
if cur > tonumber(ARGV[1]) then
  redis.call('DECR', KEYS[1])
  return 0
end
return 1
```

```
key   = hify:llm:inflight:{userId}:{provider}
limit = 3        -- 每用户每 provider 同时在途
ttl   = 120s     -- 兜底，防止计数器泄漏
```

调用前 acquire，`doFinally` 里 release。超限返回明确提示："你当前有 3 个对话在进行中，请等待完成后再试"。

### 13.5 上下文与响应边界

```yaml
hify:
  llm:
    max-context-tokens: 16384      # 内部工具不需要 1M context
    max-output-tokens: 4096
  spring:
    codec:
      max-in-memory-size: 2MB       # WebClient 默认 256KB，非流式响应稍大即抛异常
```

上下文不设上限 → 150 并发 × 大上下文 → 堆压力 → OOMKill。**这是加内存解决不了的问题，必须设边界。**

### 13.6 数据源与 Redis

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 3000
      leak-detection-threshold: 60000   # 持有超过 60s 即告警——正常请求不该持有这么久
      max-lifetime: 1700000
  data:
    redis:
      timeout: 2s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
```

**Redis 只存小值**：配置缓存、计数、锁、幂等键。**绝不放 embedding、会话正文、知识库片段**——Redis 单线程，一个大 value 会拖慢全部能力。

`maxmemory-policy` 是实例级的，锁与幂等键不能被淘汰：单实例用 `noeviction` + 严格 TTL；或用两个实例分别承载"缓存"（`allkeys-lru`）与"正确性数据"（`noeviction`）。

### 13.7 pgvector 参数

```sql
-- 单知识库规模
CREATE INDEX vec_chunk_embedding_hnsw ON vec_chunk
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 多知识库并存：按 kb_id 声明式分区，每个分区独立 HNSW 索引
```

```
ef_search 默认 40，暴露为检索设置项可调
pgvector 0.8+ 多知识库过滤场景：SET hnsw.iterative_scan = strict_order;
```

`m` 与 `ef_construction` 建索引时确定，**改动需要重建全部索引**——上线前用真实数据量做基准再定值。

### 13.8 hify-web 内层 nginx（与 Ingress 同样重要）

Ingress 配了 SSE 注解还不够，**hify-web Pod 内部的 nginx 也要配**，否则内层缓冲会让流式变成"等很久后一次性到达"：

```nginx
location /api/ {
    proxy_pass http://hify-api/api/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}
```

**更简单的做法**：让 Ingress 直接把 `/api` 路由到 hify-api，只把 `/` 路由到 hify-web。SSE 就少走一跳 nginx，少一层配置、少一个出错点。代价是需要处理跨域（api 侧配 CORS）。**两种方案二选一，不要"上面配了下面没配"。**

### 13.9 告警阈值

| 指标 | 阈值 | 后果 |
|---|---|---|
| `llm.inflight` / provider | > 40（单副本接近池上限 48） | 即将开始排队 |
| 单用户 inflight | > 3 | 触发限流 |
| `llm.first_token` P95 | > 10s | 用户体感应变差 |
| `hikaricp_connections_pending` | > 0 持续 1min | 连接池打满（多半是 SSE 期间持有连接） |
| `hikaricp_connections_usage` | > 80% | 同上 |
| `llm.circuit_state == OPEN` | 持续 > 2min | 某 provider 不可用 |
| `llm_retries_total` 速率 | 突增 | 上游抖动或惊群 |
| `container_memory_working_set` | > 85% limit（1300Mi） | 逼近 OOMKill |
| `jvm_memory_used` | > 85% heap（435m） | GC 压力 |
| `tomcat_threads_busy` | 长期 > 160（max=200 的 80%） | 有阻塞调用跑在 Tomcat 线程上没 offload |
| `llm_pool_pending` | > 0 持续 30s | 连接池不足 |

### 13.10 一期不做

- **HPA**：SSE 是 I/O 密集，CPU 指标反映不出并发压力；按并发数扩容需要 prometheus-adapter 这套额外部署。50 人固定 3 副本。
- **读写分离**：数据量到千万行才需要。
- **Redis 集群**：单实例足够。

---

## 14. 落地顺序

| 阶段 | 内容 |
|---|---|
| 1 | Docker Compose 开发环境（mysql + pgvector + redis + api + web），`.env` 统一配置 |
| 2 | 生产镜像（多阶段构建，非 root 运行，JVM 参数固定） |
| 3 | K8s 基础：namespace、Secret、ConfigMap、Deployment(hify-api)、Service、Ingress |
| 4 | **SSE 专项**：Ingress 注解、优雅停机、preStop、terminationGracePeriodSeconds、HTTP/2 |
| 5 | 有状态组件接入（优先云托管，否则 StatefulSet + PVC + 备份 CronJob） |
| 6 | 监控：Actuator → Prometheus → Grafana，配置告警 |
| 7 | PDB、NetworkPolicy（限制 hify-api 的 egress 只放行 LLM API 与存储） |
| 8 | 压测：验证 3 副本能承载 50 人峰值并发 SSE |

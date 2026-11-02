# hify-app 模块设计规格

> 阶段 0（咨询模式）的产出物。日期：2026-11-02 · 状态：已确认，直接进入实施
>
> **编号约定**：本文里 `§1.2` / `§2.1` / `§3.4` 指 **CLAUDE.md** 的章节；
> `§D4` 指 `docs/superpowers/specs/2026-10-29-hify-deployment.md` 的条目；
> `§一` / `§二` 指本文自身的章节。

---

## 一、需求

### 1.1 模块定位

按 CLAUDE.md §2.1，`hify-app` 负责 **启动、装配、静态资源与反代配置**，是**唯一可执行模块**。

前六个业务模块（ai / tool / knowledge / mcp / agent / conversation / workflow）都已交付，
剩下的就是装配层。当前 `hify-app` 只有三个文件：启动类、一个健康检查 Controller、一个占位类 ——
**装配层该做的事几乎都没做**。

一期交付：

| # | 交付物 | 说明 |
|---|---|---|
| 1 | 配置补齐 | CLAUDE.md §3.4 / §3.5 / §D13 点名要、但 `application.yml` 里没有的那些项 |
| 2 | 跨域装配 | 部署拓扑方案 A（Ingress 直连 api）要求 api 侧处理 CORS |
| 3 | 静态资源 | 静态产物托管 + 单页应用回落，让「一个 jar 跑起整个平台」成为可能 |
| 4 | 反代配置 | 生产 K8s 清单：Ingress / Deployment / Service / PDB / NetworkPolicy / 存储与备份 |
| 5 | 版本信息 | `/actuator/info` 能看出跑的是哪个版本 |
| 6 | 验收 | 真实起服务逐项打，浏览器看 SPA 回落与跨域 |

### 1.2 明确不做

- **把前端构建打进后端镜像** —— 见 §2.4
- **认证与登录** —— 认证方案还没定（CLAUDE.md §五），本模块不碰
- **调用日志（控制台日志页）** —— 那是一个新领域，要表、要跨模块聚合，属于独立模块的工作量
- **多副本文件上传存储** —— 部署规格 §3.4 已说明「不要用容器本地目录存上传的文件」，属知识库模块的事
- **HPA / 读写分离 / Redis 集群** —— 部署规格 §13.10 明确一期不做

---

## 二、选型（决定了什么 / 为什么 / 替代方案）

### 2.1 跨域放在 api 侧，且默认关闭

**决定**：新增 `CorsConfig`，允许的来源从 `hify.cors.allowed-origins` 读，**默认为空**
（空 = 不注册任何跨域规则，等于关闭）。

**为什么**：部署拓扑有两条互斥的路由方案（CLAUDE.md §2.3）：

- 方案 A：Ingress 把 `/api` 直接路由到 hify-api —— **api 与前端不同源，必须 api 侧配 CORS**
- 方案 B：hify-web 内部的 nginx 反代 `/api` → hify-api —— 同源，不需要 CORS

开发环境走方案 B（`hify-web/nginx.conf` 已经反代好了），所以**默认关闭 CORS**：
不配就是同源行为，不会有人误以为开了 CORS 就等于「谁都能调我的接口」。
生产用方案 A 时设 `HIFY_CORS_ALLOWED_ORIGINS=https://hify.example.com` 即可。

**为什么不用通配符**：令牌走 `Authorization` 头（不是 Cookie），不需要 `allowCredentials`；
通配符 + 凭据是浏览器直接拒绝的组合，配置写上去也不生效。

**代价**：方案 A 下必须记得配这个环境变量，否则前端全量报 CORS 错误。
**缓解**：K8s 的 ConfigMap 里把它写死成一个非注释的示例值，apply 的人会看到。

**替代方案**：用 `allowedOriginPatterns("*")` —— 看着省事，实际把接口开给任意站点，
内部工具的代价是数据被爬走。**已否决。**

### 2.2 静态资源从 classpath 与外部目录读，SPA 回落

**决定**：`StaticResourceConfig` 注册 `/**` 资源处理器，位置来自
`hify.app.static.locations`（默认 `classpath:/static/`），并挂一个回落解析器：
**请求的路径不是静态文件、也不在 `/api` / `/actuator` 下时，返回 `index.html`**。

**为什么**：单页应用的前端路由是 history 模式的 —— 刷新 `/agents` 会向服务器要
`/agents` 这个路径，服务器上并没有这个文件。没有回落就是 404，用户刷新页面就白了。
这是「让 jar 自己托管前端」的前提。

**回落必须排除 `/api` 与 `/actuator`**：否则 `GET /api/v1/agents` 在资源不存在时
会返回一份 HTML，前端 `JSON.parse` 炸掉，报错信息还很难看懂。

**为什么用资源处理器而不是写一个 `@RestController` 兜底**：资源处理器的 order 在
`RequestMappingHandlerMapping` 之后，**`@RestController` 的映射优先命中**，
不会出现「兜底 Controller 把业务接口吃掉」的经典事故。

**代价**：回落是「不认识就返回首页」的语义 —— 拼错的 API 路径会拿到 200 + HTML
而不是 404。**缓解**：回落只在 `/api`、`/actuator` 之外生效，API 前缀下的错误仍然是标准 404 JSON。

**替代方案**：前端改用 hash 路由（`/#/agents`）—— 不需要服务器配合，但 URL 里带 `#`，
分享链接难看，且和 nginx 的 `try_files` 方案重复。**已否决。**

### 2.3 不把前端构建打进后端镜像

**决定**：后端镜像里没有前端产物；前端仍是独立的 nginx 镜像（`hify-web/Dockerfile`）。

**为什么**：CLAUDE.md §4 已经定了「前端不进 Maven reactor —— 前后端的依赖体系、
发布节奏、CI 缓存策略都不同」。把 `npm run build` 塞进后端镜像的构建阶段，等于
把两条工具链焊死在一个生命周期里：改前端要重建整个后端镜像，后端发版也被前端构建卡住。

**那静态资源这条为什么还要做**：因为它服务的是**另一种部署形态** —— 单容器
（`java -jar app.jar` + 前端产物目录），运维不想再维护一个 nginx。
`hify.app.static.locations` 支持指向外部目录，把 `web/` 挂进去就行。

**代价**：多一种部署形态要测。**缓解**：默认 `classpath:/static/` 是空的，
不配置时行为与现在完全一样（找不到静态资源就 404）。

### 2.4 SSE 的异步超时与优雅停机窗口要对齐 K8s

**决定**：

- `spring.mvc.async.request-timeout: 30m`（CLAUDE.md §3.4 点名的项，现在缺）
- `spring.lifecycle.timeout-per-shutdown-phase: 180s`（从 60s 提上来）

**为什么**：两处超时必须比 K8s 的 `terminationGracePeriodSeconds: 180` **更宽松或相等**，
否则容器先杀进程，Spring 自己还没退完 —— 表现为「滚动更新时对话被拦腰截断」。
现在的 60s 配 180s 就是这个错：Spring 在第 60 秒就退出了，
剩下的 120 秒里 SSE 连接被硬切，前端看到的是「流到一半断了」。

**为什么 async timeout 要 30m**：工作流的运行接口用 `DeferredResult`（不是 `SseEmitter`），
没有 emitter 自己设的超时保护，只能靠 Spring MVC 的异步超时。默认 30s 会掐断任何
跑得比 30 秒的工作流。

**替代方案**：给工作流也换成 SSE —— 一期不需要看中间进度，跑完给结果就行（部署规格 §2.8 已定）。

### 2.5 生产清单按「方案 A」写

**决定**：K8s 的 Ingress 把 `/api` 路由到 hify-api、`/` 路由到 hify-web，
**hify-web 的 nginx 不再反代 `/api`**。

**为什么**：部署规格 §13.8 说得很直接 —— 「更简单的做法：让 Ingress 直接把 /api
路由到 hify-api，只把 / 路由到 hify-web。SSE 就少走一跳 nginx，少一层配置、少一个出错点。
代价是需要处理跨域（api 侧配 CORS）」。而 §13.8 同时警告「不要『上面配了下面没配』」——
内层 nginx 也配了 `proxy_buffering off`，但多一跳就多一处可能漏。

**但是**：开发环境的 `hify-web/nginx.conf` 仍然保留反代 —— 那时没有 Ingress，
nginx 是唯一的入口。两套配置服务于两种拓扑，不是「上面配了下面没配」。

**代价**：方案 A 下前端与 api 不同源，需要 CORS（§2.1）。这是明码标价的代价。

### 2.6 版本信息走 actuator 的 build-info

**决定**：`spring-boot-maven-plugin` 加 `build-info` goal，生成
`META-INF/build-info.properties`，`/actuator/info` 直接能读到版本与构建时间。

**为什么**：`actuator` 的 `info` 端点已经在暴露列表里（`health,info,prometheus,metrics`），
加一个 goal 就有了，不用自己写接口、自己存版本号。
`/actuator/info` 是运维接口，不进 `/api/v1` —— 业务前端不需要读它。

**替代方案**：前端侧已经有 `__APP_VERSION__`（从 package.json 注入），后端再存一份
版本号会两处漂移。build-info 从 jar 自身生成，不存在漂移。

---

## 三、数据模型

**不新增任何表。** hify-app 不归属业务表（CLAUDE.md §2.1 里它一栏是「—」）。

- 静态资源、跨域规则、版本信息都是**装配**，不落库
- K8s 的配置与密钥走 ConfigMap / Secret，不进业务库

---

## 四、模块边界

### 4.1 依赖方向

```
hify-app → hify-shared / hify-ai / hify-tool / hify-knowledge / hify-mcp
         → hify-agent / hify-conversation / hify-workflow / hify-demo
```

- `hify-app` 依赖全部业务模块 —— 它是装配层，**唯一**允许这么做的模块
- 业务模块**不得**依赖 `hify-app`（ArchUnit `ModuleBoundaryTest` 已在守这条）

### 4.2 内部落位

| 包 | 内容 |
|---|---|
| `com.hify.app` | 启动类 |
| `com.hify.app.config` | 跨域、静态资源、配置属性 |
| `com.hify.app.controller` | 健康检查 |
| `hify-app/src/main/resources/db` | 建表脚本与迁移脚本（已有） |

**硬约束**：`com.hify.app` 里**不放业务逻辑**。本模块的每个类都只做「装配」。

---

## 五、实现要点

### 5.1 配置补齐清单

| 配置项 | 值 | 出处 |
|---|---|---|
| `spring.mvc.async.request-timeout` | `30m` | CLAUDE.md §3.4 |
| `spring.lifecycle.timeout-per-shutdown-phase` | `180s` | §2.4 |
| `spring.codec.max-in-memory-size` | `2MB` | CLAUDE.md §3.5 |
| `server.forward-headers-strategy` | `framework` | Ingress 后面要拿到真实协议与客户端 IP |
| `server.tomcat.threads.max` | `200` | §D13.1：保持默认 200，写出来是为了别被误改 |
| `hify.cors.allowed-origins` | 空 | §2.1 |
| `hify.app.static.enabled` | `true` | §2.2 |
| `hify.app.static.locations` | `classpath:/static/` | §2.2 |

### 5.2 生产清单的文件划分

`deploy/k8s/` 下按「基础 → 应用 → 流量 → 存储」的顺序编号，`kubectl apply -f deploy/k8s/` 一次应用完：

| 文件 | 内容 |
|---|---|
| `00-namespace.yaml` | 命名空间 |
| `01-configmap.yaml` | 非敏感配置（含 CORS 来源示例） |
| `02-secret.example.yaml` | 密钥模板（**只给占位，不入库真值**） |
| `10-api-deployment.yaml` | 3 副本、探针、preStop、资源限额 |
| `11-api-service.yaml` / `12-api-pdb.yaml` | Service 与 PodDisruptionBudget |
| `20-web-deployment.yaml` / `21-web-service.yaml` | 前端 nginx |
| `30-ingress.yaml` | 方案 A 的路由 + SSE 注解 + TLS |
| `40-networkpolicy.yaml` | api 的出网白名单 |
| `50-statefulset-storage.yaml` | 自建存储（**仅在无云托管时用**） |
| `60-backup-cronjob.yaml` | MySQL / PG 定时 dump |

**api Deployment 的关键项**（每一条都有出处）：

- `terminationGracePeriodSeconds: 180` —— 最长生成时长 + 缓冲（§D6.3）
- `preStop: sleep 10` —— 等 Endpoint 摘除传播，否则滚动更新会有一批 502（§D6.1）
- `liveness` 只查 `livenessState`，`readiness` 查 `readinessState,db` —— 依赖抖动不重启（§D4 坑 5）
- `resources.limits.memory: 1536Mi` —— 堆 512m + native ~566m（CLAUDE.md §1.4）
- `JAVA_TOOL_OPTIONS` 与 Dockerfile 一致 —— 两处镜像的 JVM 参数必须一样

### 5.3 验收方式

| 阶段 | 验证 |
|---|---|
| 配置 | 启动后读 `Environment`，断言关键项生效 |
| 跨域 | 带 `Origin` 头发预检请求，断言有 `Access-Control-Allow-Origin`；不在白名单则没有 |
| 静态资源 | `/agents` 返回 `index.html`；`/api/v1/xxx` 不存在时仍是 JSON 404 |
| 清单 | `kubectl apply --dry-run=client`（本地无集群时至少 YAML 语法正确） |
| 完整验收 | 真实起服务 + curl + 浏览器 |

---

## 六、注意事项

### 依赖与边界

- **本模块只做装配** —— 任何「顺手加的业务」都不该进这里
- **配置用环境变量注入** —— 开发用 Compose、生产用 K8s，两者共用同一套变量名（§D7.1）

### 静态资源的坑

- **回落解析器必须排除 `/api` 与 `/actuator`** —— 见 §2.2
- **`classpath:/static/` 不存在时不报错** —— 资源处理器找不到文件就是 404，不是启动失败

### 部署的坑

- **Ingress 与内层 nginx 不能「上面配了下面没配」** —— 方案 A 下内层 nginx 不碰 `/api`，就没这个问题
- **Secret 是 base64 不是加密** —— 仓库里只放模板
- **不暴露 `env` / `heapdump` / `threaddump` actuator 端点** —— `env` 会泄露环境变量里的密钥（§D9.3）

---

## 七、验证纪律

- **每层验证通过再进下一层**
- **配置项要断言实际生效**，不能只看 yml 文件里写了没有

---

## 八、实现期发现

> （实现过程中遇到的实际问题，实时补记。）

### 8.1 Spring 6.2 的资源解析器签名和 5.x 不一样

**症状**：照着老文章的写法编译不过 —— `ResourceLocation` 这个类找不到，
`resolveResource` 的参数是 `List<? extends Resource>` 而不是 `List<? extends ResourceLocation>`，
而且 `resolveResourceInternal` 不抛 `IOException`。

**做法**：以 `javap` 看实际签名为准，别凭记忆写：

- 覆写点是 `PathResourceResolver#resolveResourceInternal(request, path, locations, chain)`
  （protected，**不抛 IOException** —— 抛了编译不过）
- 位置参数是 `List<? extends Resource>`，不是 `ResourceLocation`
- `CorsRegistry` 在 6.2 里暴露的是 `protected Map<String, CorsConfiguration> getCorsConfigurations()`，
  比 5.x 的 `getCorsMappings()` 更好用 —— 直接就是合并后的配置，不用再反射

**教训**：Spring 的小版本之间 API 会动。写之前先 `javap` 一下，比照着记忆写快得多。

### 8.2 `/**` 匹配不到 `"/"`

**症状**：`GET /` 返回 404，而 `GET /agents` 正常回落。调试发现**根路径根本没进资源处理器**。

**原因**：`/**` 匹配的是「`/` 之后的路径」。给 `"/"` 单独注册一条规则也不行 ——
那条规则传下来的路径是**空串**，资源解析器拿到空串直接返回 null，连回落逻辑都进不去。

**做法**：根路径走**视图控制器**转发：`registry.addViewController("/").setViewName("forward:/index.html")`。
`forward:` 会被 `/**` 那条规则正常命中并返回文件。

**教训**：单页应用的前端路由是 history 模式，刷新任何路径都要能拿到入口页 ——
「根路径」和「任意未知路径」是**两个**问题，解决方式不一样。

### 8.3 裸路径会被当成 servlet 容器的相对路径

**症状**：外部目录传 `/app/web` 时，文件一个都找不到。调试发现位置资源变成了
`ServletContextResource` 而不是 `FileUrlResource`。

**原因**：位置字符串是**按 URL 解析**的。没有协议前缀的路径，
Spring 当成「相对于 servlet 容器的路径」—— 容器里当然没有这个目录。

**做法**：归一化时给没有协议前缀的位置补上 `file:`。补了之后
`/app/web` 与 `file:/app/web` 都能用。

**教训**：classpath 上的位置带 `classpath:` 前缀，外部目录要带 `file:` ——
写配置时得像写 URL 一样想，不能像写路径一样想。

### 8.4 Boot 的默认静态资源映射会顶掉自己那一份

**症状**：单元测试里（纯 `@EnableWebMvc` 上下文）回落是好的，装进真实应用就 404。

**原因**：Spring Boot 的默认静态资源映射也注册在 `/**` 上，而
`AbstractHandlerRegistry` 的注册表是 `Map` —— **后注册的顶掉先注册的**。
用户配置与自动配置的装配顺序不保证谁先，于是行为成了运气。

**做法**：`spring.web.resources.add-mappings: false` 关掉默认那份，
只留 `hify-app` 自己注册的这条；那几个标准位置由 `hify.app.static.locations` 补回来。

**教训**：只要自己也要注册同一个路径模式，就得先把默认的那个关掉 ——
「谁先谁后」不是可以赌的东西。

### 8.5 nginx 配置必须是模板，不能写死上游

**症状**：同一份 `nginx.conf` 在 Compose 里能用（上游是服务名 `api`），
搬到 K8s 就 404（上游是 `hify-api`）。

**做法**：改成 `templates/default.conf.template`，靠官方 nginx 镜像的 `envsubst`
把 `${API_UPSTREAM}` 换成环境变量。Compose 设 `api:8080`，K8s 设 `hify-api:80`。

**顺带确认**：`envsubst` 只替换环境里存在的变量，所以模板里的
`$uri`、`$host`、`$scheme` 这些 nginx 变量不会被误伤 ✅。

### 8.6 prod profile 只写文件，`kubectl logs` 是空的

**症状**：按 CLAUDE.md 的约定，prod profile 的日志写文件（JSON）。
但在 K8s 里排障时 `kubectl logs` 什么都看不到 —— 集群的日志采集走的是 stdout。

**做法**：prod profile 加一个 stdout appender，**同样是 JSON**（不是彩色控制台）。
文件输出按原约定保留，stdout 输出给采集器用。

**教训**：「生产要 JSON 不要彩色控制台」这个要求里，**JSON 到 stdout 是满足的** ——
需要改的不是「要不要写 stdout」，而是「stdout 上用什么格式」。

### 8.7 优雅停机窗口要跟 K8s 的终止宽限期对齐

**症状**：配置里 `timeout-per-shutdown-phase: 60s`，而 K8s 的
`terminationGracePeriodSeconds: 180`。Spring 在第 60 秒就退完，
剩下 120 秒里 SSE 连接被容器强杀 —— 表现为「滚动更新时对话被拦腰截断」。

**做法**：把停机窗口提到 180s，与 K8s 的宽限期相等。

**教训**：两个超时是**同一个问题**的两端，必须一起看。只改一边，
症状就会从「更新时被切断」变成「更新时最后 120 秒的对话被切断」。

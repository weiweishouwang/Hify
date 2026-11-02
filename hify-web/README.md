# hify-web

Hify 前端。Vue 3 + TypeScript + Vite + Element Plus + Pinia。

独立于 Maven 工程，产物是静态文件，由 nginx 托管。开发期 `/api` 走 vite 代理，
生产期有两种拓扑（见 `deploy/k8s/`）：

| 拓扑 | `/api` 走哪 | 前端与 api 是否同源 |
|---|---|---|
| 开发（Docker Compose）/ 直连 Service | web 容器里的 nginx 反代 | 同源，不需要 CORS |
| 生产（K8s，方案 A） | Ingress 直接路由到 hify-api | 不同源，**api 侧要配 CORS** |

两种拓扑下**浏览器侧都是同源或已配好 CORS**，所以代码里不出现绝对地址。

## 命令

```bash
npm install          # 首次
npm run dev          # dev server http://localhost:5173，/api 代理到 localhost:8080
npm run build        # vue-tsc 类型检查 + vite build → dist/
npm test             # vitest
npm run type-check   # 只做类型检查
```

后端没在 8080 上跑时，指定代理目标：

```bash
VITE_API_PROXY_TARGET=http://localhost:8082 npm run dev
```

## 目录

```
src/
├── api/           后端接口模块：health.ts / auth.ts / chat.ts + sse.ts（SSE 客户端）
├── views/         页面：模型管理 / Agent 管理 / 对话（+ 知识库 / 工作流 / 日志 占位页）
├── components/    业务组件：chat/（流式对话面板、消息气泡、工具调用中间态）、PendingPanel
├── layouts/       BlankLayout（独立页面用的全屏容器）
├── stores/        Pinia：user / conversation / connection
├── router/        路由表 + 守卫
├── types/         与后端 DTO 对齐的类型
├── utils/         request.ts（axios 实例 + 拦截器）、token.ts、id.ts
└── styles/        全局样式
```

`templates/default.conf.template` 是 **nginx 配置模板**而不是普通配置：官方 nginx 镜像
启动时会把 `/etc/nginx/templates/*.template` 里的 `${变量}` 换成环境变量。
反代上游因此可以按环境切换 —— Compose 里 `API_UPSTREAM=api:8080`，
K8s 里 `API_UPSTREAM=hify-api:80` —— 同一份配置在两种拓扑下都能跑。
模板里除了 `API_UPSTREAM` 之外的 `$` 变量（`$uri`、`$host` 等）不在环境变量里，
`envsubst` 不会动它们。

`App.vue` 就是布局：左侧 `el-menu` 菜单栏 + 右侧内容区 `router-view`。路由表因此全是顶层路由，
不套带 `children` 的布局路由 —— 套了会出现「App.vue 渲染 App.vue」的递归。

约定见仓库根目录的 `CLAUDE.md`（§2.5 前端结构、§3.6 前端规范）。

## 请求怎么发

统一走 `utils/request.ts`：

```ts
import { API_PREFIX, get, post } from '@/utils/request'

const models = await get<Model[]>(`${API_PREFIX}/models`)
const created = await post<Model>(`${API_PREFIX}/models`, payload)
```

- baseURL 是 `/api`，实际路径要带 `/v1` 前缀，完整地址 `/api/v1/xxx`
- 响应拦截器**自动解包出 `data`**，失败时 `ElMessage.error` 提示后 reject —— 调用方不要再解包、不要再弹一次 toast
- 成功码是 **`code = 0`**（后端 `Result.SUCCESS_CODE`），不是 200；200 也接受，兼容 HTTP 风格返回体

## 两个容易踩的坑

- **TypeScript 锁在 6.x** —— TS 7 的 `package.json` 加了 `exports` 字段，不再导出
  `./lib/tsc`，而 `vue-tsc` 正是靠它挂载 Vue 语言插件，升上去会直接报
  `ERR_PACKAGE_PATH_NOT_EXPORTED`
- **Vite 8 底层是 Rolldown** —— 没有 Rollup 的 `manualChunks` 对象写法，
  手动分包走 `output.codeSplitting.groups`

# hify-web

Hify 前端。Vue 3 + TypeScript + Vite + Element Plus + Pinia。

独立于 Maven 工程，产物是静态文件，由 nginx 托管。开发期 `/api` 走 vite 代理，
生产期 `/api` 走 nginx 反代，**浏览器侧始终同源**，所以代码里不出现绝对地址。

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
├── api/           接口封装：client.ts（axios）+ sse.ts（fetch 流）+ modules/
├── views/         页面：对话 / 模型管理 / Agent 配置 / 知识库 / 工作流 / 日志
├── components/    业务组件
├── layouts/       AppLayout
├── stores/        Pinia：user / conversation / connection
├── router/        路由表 + 守卫
├── types/         与后端 DTO 对齐的类型
├── utils/         小工具
└── styles/        全局样式
```

约定见仓库根目录的 `CLAUDE.md`（§2.5 前端结构、§3.6 前端规范）。

## 两个容易踩的坑

- **TypeScript 锁在 6.x** —— TS 7 的 `package.json` 加了 `exports` 字段，不再导出
  `./lib/tsc`，而 `vue-tsc` 正是靠它挂载 Vue 语言插件，升上去会直接报
  `ERR_PACKAGE_PATH_NOT_EXPORTED`
- **Vite 8 底层是 Rolldown** —— 没有 Rollup 的 `manualChunks` 对象写法，
  手动分包走 `output.codeSplitting.groups`

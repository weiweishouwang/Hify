# Hify 前端的生产镜像
#
# 两段式构建：
#   1. node 阶段装依赖、跑 vite build，产出 dist/（纯静态文件）
#   2. nginx 阶段只放 dist/ 与 nginx.conf，不带 node
#
# 静态文件与反代规则跟开发环境无关，所以本地 docker compose 与
# 生产 K8s 用的是同一个镜像、同一个 nginx.conf。

FROM node:22-alpine AS build

WORKDIR /app

# 先只拷依赖清单，让 npm ci 这一层能被缓存
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# 构建期不需要连后端
RUN npm run build


FROM nginx:1.27-alpine

# nginx 配置：静态资源 + 反代 /api
#
# 配置是模板：官方 nginx 镜像启动时会用 envsubst 把 /etc/nginx/templates/*.template
# 里的 ${变量} 换成容器环境变量里的值，生成 /etc/nginx/conf.d/default.conf。
# 反代上游因此可以按环境切换 —— Compose 里是 api:8080，K8s 里是 hify-api:8080。
#
# 模板里除了 API_UPSTREAM 之外的 $ 变量（$uri、$host 等）不在环境变量里，
# envsubst 不会动它们。
ENV API_UPSTREAM=api:8080
COPY templates/ /etc/nginx/templates/

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

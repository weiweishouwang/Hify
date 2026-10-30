FROM nginx:1.27-alpine

# nginx 配置：静态资源 + 反代 /api
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/index.html

EXPOSE 80

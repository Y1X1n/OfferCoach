# 多阶段构建：镜像内完成 pnpm 安装与 Vite 打包，无需宿主机 Node / pnpm。
# 不写 `# syntax=docker/dockerfile:1`、也不用 RUN --mount=cache：前者需要额外拉取
# dockerfile 前端镜像（Docker Hub 不可达时构建直接失败），后者要求 BuildKit。
FROM node:22-alpine AS build
ENV CI=true \
    COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable
WORKDIR /src
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
# 模板交给官方镜像的 envsubst 入口脚本渲染；过滤器限定只替换 AUTH_PROXY_SECRET，
# 避免把 nginx 自己的 $host / $remote_addr / $uri 之类变量也替换成空值。
ENV NGINX_ENVSUBST_FILTER=AUTH_PROXY_SECRET
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/ /usr/share/nginx/html/
EXPOSE 80

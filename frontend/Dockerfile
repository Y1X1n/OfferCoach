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
# 以非 root（nginx 用户）运行：静态站点与 TLS 都不需要 root，容器一旦被拿下也不该直接
# 拿到宿主机的 root 权限。代价是要把 nginx 运行期会写的目录交给它：pid 文件、缓存、
# 以及入口脚本 envsubst 渲染出来的 conf.d（模板本身仍只读）。
# /var/run 在 alpine 上是指向 /run 的符号链接，chown -R 不会顺着改到目标目录，
# 所以 /run 要单独 chown——否则以 --tmpfs /run 启动时写不了 pid 文件。
RUN chown -R nginx:nginx /var/cache/nginx /etc/nginx/conf.d \
    && mkdir -p /run \
    && chown nginx:nginx /run \
    && touch /run/nginx.pid \
    && chown nginx:nginx /run/nginx.pid
# 容器内监听 8080 而不是 80：Docker 默认把 unprivileged_port_start 设为 0、非 root 也能绑 80，
# 但那依赖运行时 sysctl 默认值；固定用 8080 就不跟这个默认值绑在一起，由 compose 映射到宿主机。
EXPOSE 8080
USER nginx
# 模板交给官方镜像的 envsubst 入口脚本渲染；过滤器限定只替换 AUTH_PROXY_SECRET，
# 避免把 nginx 自己的 $host / $remote_addr / $uri 之类变量也替换成空值。
ENV NGINX_ENVSUBST_FILTER=AUTH_PROXY_SECRET
# 显式给一个空默认值：envsubst 只替换**已定义**的变量，变量没定义时模板里的
# ${AUTH_PROXY_SECRET} 会被原样留下，nginx 解析时报 unknown variable 直接退出。
# compose 一直会传这个变量，但裸 `docker run` 时不该起不来（空值 = 不信任任何代理头）。
ENV AUTH_PROXY_SECRET=""
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
# 安全响应头片段：nginx 的 add_header 不跨 location 继承，多个 location 需要重复引入。
# 放在 snippets/ 而不是 templates/，避免被入口脚本的 envsubst 处理。
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist/ /usr/share/nginx/html/

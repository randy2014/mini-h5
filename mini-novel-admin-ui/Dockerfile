FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
# 主用 npmmirror（构建快），但它对新版本 tarball 同步有延迟：
# 2026-11-04 曾因 rollup 新版在 npmmirror 缺包（cdn 404）导致镜像构建失败、整条部署中断。
# 现已提交 package-lock.json 锁定依赖版本；这里再兜底一次：npmmirror 安装失败就回退官方源重试。
RUN npm config set registry https://registry.npmmirror.com \
 && npm install --no-audit --no-fund \
 || (echo "[deploy] npmmirror 安装失败，回退 registry.npmjs.org 重试" \
     && npm config set registry https://registry.npmjs.org \
     && npm install --no-audit --no-fund)
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# vite base 为 /admin/，静态资源按 /admin/ 路径部署
COPY --from=builder /app/dist /usr/share/nginx/html/admin
EXPOSE 80

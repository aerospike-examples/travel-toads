# --- build stage -----------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- serve stage -------------------------------------------------------------
# nginx-unprivileged, not nginx: plain nginx:alpine's master process runs as root and binds port
# 80 (privileged); this variant is pre-configured (temp/cache/pid paths, non-root-writable) to run
# as a non-root user on port 8080 out of the box, matching nginx.conf's `listen 8080` above.
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Installing wget needs root; the base image otherwise already runs as its non-root user.
USER root
RUN apk add --no-cache wget
USER nginx

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=3s --timeout=3s --retries=30 \
  CMD wget -qO- http://localhost:8080/ >/dev/null 2>&1 || exit 1
CMD ["nginx", "-g", "daemon off;"]

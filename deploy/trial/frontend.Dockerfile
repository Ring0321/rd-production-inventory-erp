FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.15.1 --activate
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build:prod

FROM nginx:1.27-alpine
COPY deploy/trial/nginx.conf /etc/nginx/templates/default.conf.template
COPY deploy/trial/15-validate-public-tenant.sh /docker-entrypoint.d/15-validate-public-tenant.sh
RUN chmod +x /docker-entrypoint.d/15-validate-public-tenant.sh
ENV NGINX_ENVSUBST_FILTER=RD_PUBLIC_TENANT_ID
COPY --from=build /workspace/dist-prod/ /usr/share/nginx/html/
EXPOSE 80

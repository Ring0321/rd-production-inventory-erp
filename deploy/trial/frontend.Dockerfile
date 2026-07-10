FROM node:22-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.15.1 --activate
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build:prod

FROM nginx:1.27-alpine
COPY deploy/trial/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist-prod/ /usr/share/nginx/html/
EXPOSE 80

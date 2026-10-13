FROM nginx:1.27-alpine
COPY deploy/trial/nginx.conf /etc/nginx/templates/default.conf.template
COPY deploy/trial/15-validate-public-tenant.sh /docker-entrypoint.d/15-validate-public-tenant.sh
RUN chmod +x /docker-entrypoint.d/15-validate-public-tenant.sh
ENV NGINX_ENVSUBST_FILTER=RD_PUBLIC_TENANT_ID
COPY frontend/ /usr/share/nginx/html/
EXPOSE 80

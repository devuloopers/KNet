# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim@sha256:dc26ae92c9fb15cf6b1d63e20f0944b2efe0f115b20e00be48fb23fceb061c54 AS build
WORKDIR /workspace/products/website
COPY products/website/package.json products/website/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY products/website/ ./
COPY desktopBrand/ /workspace/desktopBrand/
ARG KNET_SITE_ENV=preview
ARG KNET_SITE_ORIGIN
ARG KNET_ARTWORK_REQUIRE_FRESH=false
ARG KNET_ARTWORK_SOURCE_SHA
ARG KNET_ARTWORK_SOURCE_DIGEST
RUN KNET_SITE_ENV="$KNET_SITE_ENV" KNET_SITE_ORIGIN="$KNET_SITE_ORIGIN" KNET_ARTWORK_REQUIRE_FRESH="$KNET_ARTWORK_REQUIRE_FRESH" KNET_ARTWORK_SOURCE_SHA="$KNET_ARTWORK_SOURCE_SHA" KNET_ARTWORK_SOURCE_DIGEST="$KNET_ARTWORK_SOURCE_DIGEST" npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS runtime
LABEL org.opencontainers.image.source="https://github.com/devuloopers/KNet" \
      org.opencontainers.image.title="KNet website" \
      org.opencontainers.image.description="KNet Desktop public static website"
USER root
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build --chown=101:101 /workspace/products/website/dist/ /usr/share/nginx/html/
COPY --chown=101:101 products/website/deployment/nginx.conf /etc/nginx/nginx.conf
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["nginx", "-g", "daemon off;"]

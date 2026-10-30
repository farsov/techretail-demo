# Imagen del frontend: compila Angular y lo sirve con nginx sin privilegios, reenviando /api/ al backend.
FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build

FROM nginxinc/nginx-unprivileged:1.27-alpine
# Dirección del backend, sin barra final; se puede cambiar al ejecutar.
ENV BACKEND_URL=http://backend:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/app-frontend/browser /usr/share/nginx/html
EXPOSE 8080

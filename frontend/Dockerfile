# Dockerfile pour Angular 17 (production build)
FROM node:20-alpine AS build

WORKDIR /app

# Installer les dépendances
COPY package.json package-lock.json* ./
RUN npm install --legacy-peer-deps

# Copier le code source
COPY . .

# Build de l'application Angular (prod)
RUN npm run build -- --configuration=production

# Étape finale : servir avec Nginx
FROM nginx:1.25-alpine

# Copier le build Angular dans le dossier Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# Copier une config nginx custom si besoin (optionnel)
# COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

# Stage 1 — build with Maven
FROM maven:4.0.0-rc-4-eclipse-temurin-17 AS builder
WORKDIR /build

# Copier d'abord les fichiers de build pour améliorer la mise en cache
COPY pom.xml .
COPY checkstyle.xml .

RUN if [ -f mvnw ] && [ -x mvnw ]; then chmod +x mvnw; fi

# Copier le code source
COPY src ./src

# Compiler le projet (skip tests par défaut pour accélérer en CI ; retirez -DskipTests si besoin)
RUN mvn -B -DskipTests package

# Stage 2 — runtime minimal
FROM eclipse-temurin:17-jre-jammy
LABEL maintainer="anip-kyc-dataviz"

# Créer un utilisateur non-root
RUN groupadd -r app && useradd -r -g app app

# Installer curl pour healthcheck (petite image jammy disponible)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copier le jar produit (prise en charge d'un seul jar dans target/)
COPY --from=builder /build/target/*.jar ./app.jar

# Droits
RUN chown -R app:app /app && chmod 640 /app/app.jar

USER app

# Variables runtime configurables via docker-compose
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE="docker"

EXPOSE 8080

# Healthcheck utilise actuator si exposé ; adapte le path si nécessaire
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]

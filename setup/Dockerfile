# syntax=docker/dockerfile:1
# Image de l'API SMELS (Gradle + Kotlin + Spring Boot).
# Contexte de build = racine du dépôt (voir compose.prod.yaml).

################################################################################
# Dépendances
################################################################################
FROM eclipse-temurin:25-jdk-jammy AS deps

WORKDIR /build

COPY gradlew gradlew
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./

RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon || true

################################################################################
# Build
################################################################################
FROM deps AS package

WORKDIR /build

COPY src/ src/

RUN ./gradlew bootJar --no-daemon -x test \
    && find build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} /build/app.jar \;


################################################################################
# Runtime
################################################################################
FROM eclipse-temurin:25-jre-jammy AS final

ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser

WORKDIR /app

COPY --from=package /build/app.jar app.jar


USER appuser

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]

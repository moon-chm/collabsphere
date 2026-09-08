# Multi-stage Dockerfile for CollabSphere Ktor Backend
# Stage 1: Build application using JDK 17
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy gradle files first for layer caching
COPY gradle /app/gradle
COPY gradlew /app/gradlew
COPY gradle.properties /app/gradle.properties
COPY settings.gradle.kts /app/settings.gradle.kts
COPY build.gradle.kts /app/build.gradle.kts

# Grant execute permissions to gradlew
RUN chmod +x ./gradlew

# Pre-download Gradle wrapper distribution for layer caching
RUN ./gradlew --version || ./gradlew --version

# Copy source code
COPY src /app/src

# Build distribution binary
RUN ./gradlew installDist --no-daemon

# Stage 2: Runtime image using minimal Alpine JRE
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy installed distribution from builder stage
COPY --from=builder /app/build/install/server /app

# Create directory for persistent file uploads
RUN mkdir -p /app/local_files_upload

# Expose default port
EXPOSE 8080

# Run Ktor application
ENTRYPOINT ["/app/bin/server"]

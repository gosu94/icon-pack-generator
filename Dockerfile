# Stage 1: Build the Next.js frontend
FROM node:18-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package.json frontend/yarn.lock ./
RUN yarn install
COPY frontend/. .
RUN yarn build

# Stage 2: Build the Java Spring Boot application with Gradle and Java 21
FROM --platform=linux/amd64 openjdk:21-jdk-slim AS backend-builder
WORKDIR /app/backend

# Copy Gradle files first to leverage Docker cache for dependencies
COPY build.gradle settings.gradle ./
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon

# Copy backend source code first (but exclude static directory to avoid conflicts)
COPY src ./src

# Clean any existing static files and copy fresh frontend build artifacts
RUN rm -rf ./src/main/resources/static/*
COPY --from=frontend-builder /app/frontend/out ./src/main/resources/static

# Build the JAR (which will now include the fresh frontend static files)
RUN ./gradlew bootJar --no-daemon

# Use OpenJDK 21 as base image with Python support (force x86_64 for WebP compatibility)
FROM openjdk:21-jdk-slim

# Install Python, pip, and system dependencies for rembg
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-dev \
    python3-venv \
    wget \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create a virtual environment for Python dependencies
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install rembg and its dependencies in the virtual environment
RUN pip install --no-cache-dir \
    rembg[new] \
    pillow \
    opencv-python-headless \
    click \
    scipy \
    scikit-image \
    pooch \
    pymatting \
    filetype \
    watchdog \
    aiohttp \
    gradio \
    asyncer \
    onnxruntime \
    numpy

# Create app directory
RUN mkdir -p /app

# Set working directory
WORKDIR /app

COPY --from=backend-builder /app/backend/build/libs/*.jar icon-pack-generator.jar

# Create runtime user for security
RUN useradd --create-home --shell /bin/bash app

# Create temp directory for background removal with proper permissions
RUN mkdir -p /tmp/rembg && chown app:app /tmp/rembg
RUN mkdir -p /app/generated-images && chown app:app /app/generated-images

# Create user icons directory with proper permissions
RUN mkdir -p /app/data/user-icons && chown app:app /app/data/user-icons
RUN mkdir -p /app/data/user-illustrations && chown app:app /app/data/user-illustrations
RUN mkdir -p /app/data/user-mockups && chown app:app /app/data/user-mockups
RUN mkdir -p /app/data/user-labels && chown app:app /app/data/user-labels

# Create static directories for file storage (relative paths from application.yaml)
RUN mkdir -p /app/static/user-icons && chown app:app /app/static/user-icons
RUN mkdir -p /app/static/user-illustrations && chown app:app /app/static/user-illustrations
RUN mkdir -p /app/static/user-mockups && chown app:app /app/static/user-mockups
RUN mkdir -p /app/static/user-labels && chown app:app /app/static/user-labels

# Switch to app user
USER app

# Set Java options for optimal container performance
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Set Python path to use virtual environment
ENV PATH="/opt/venv/bin:$PATH"
ENV PYTHONPATH="/opt/venv/lib/python3.11/site-packages"

# Expose the port
EXPOSE 8080

# Health check to ensure both Java app and rembg are working
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health 2>/dev/null || exit 1

# Run the application
# Note: This container uses x86_64 architecture for WebP native library compatibility
# Build with: docker build --platform linux/amd64 -t icon-pack-generator .
# Run with: docker run --platform linux/amd64 -p 8080:8080 icon-pack-generator
CMD ["java", "-jar", "icon-pack-generator.jar"]

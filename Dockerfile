# Use OpenJDK 21 as base image with Python support
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

# Copy Gradle wrapper and build files
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (for faster builds when source changes)
COPY src/main/resources/application.properties src/main/resources/
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew bootJar --no-daemon

# Create runtime user for security
RUN useradd --create-home --shell /bin/bash app

# Create temp directory for background removal with proper permissions
RUN mkdir -p /tmp/rembg && chown app:app /tmp/rembg
RUN mkdir -p /app/generated-images && chown app:app /app/generated-images


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
CMD ["java", "-jar", "build/libs/icon-pack-generator-0.0.1-SNAPSHOT.jar"]

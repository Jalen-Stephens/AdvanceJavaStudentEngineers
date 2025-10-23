# Simple Dockerfile for MetaDetect Service - Development/Testing
FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src
COPY tools ./tools

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the JAR
COPY --from=build /app/target/*.jar app.jar

# Copy the C2PA tool
COPY --from=build /app/tools ./tools

# Make c2patool executable
RUN chmod +x tools/c2patool/c2patool 2>/dev/null || true

# Expose port
EXPOSE 8080

# Run the application
# Note: Use --env-file .env when running the container to load environment variables
ENTRYPOINT ["java", "-jar", "app.jar"]

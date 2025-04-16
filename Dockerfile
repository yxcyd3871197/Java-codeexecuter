# Use a multi-stage build for a smaller final image

# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom.xml first to leverage Docker cache for dependencies
COPY pom.xml .
# Download dependencies
RUN mvn dependency:go-offline -B
# Copy the rest of the source code
COPY src ./src
# Build the application JAR
RUN mvn package -DskipTests

# Stage 2: Create the final runtime image
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# Copy the built JAR file from the build stage
COPY --from=build /app/target/json-fixer-*.jar app.jar
# Expose the port the application runs on (Spring Boot default is 8080)
EXPOSE 8080
# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

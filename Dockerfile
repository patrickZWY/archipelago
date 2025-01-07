# ---------- Stage 1: Build the JAR ----------
FROM maven:3.9.4-eclipse-temurin-17 AS build

WORKDIR /app

# Copy your project files into the container
COPY pom.xml ./
COPY src ./src

# Run the Maven build (this produces the JAR in /app/target/)
RUN mvn clean package -DskipTests

# ---------- Stage 2: Run the JAR ----------
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/archipelago-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port (Spring Boot default)
EXPOSE 8080

# Set the ENTRYPOINT to run your app
ENTRYPOINT ["java", "-jar", "app.jar"]

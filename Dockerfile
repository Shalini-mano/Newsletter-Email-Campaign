# ===============================
# BUILD STAGE
# ===============================
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom and download dependencies first (cache optimization)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy full project
COPY . .

# Build jar
RUN mvn clean package -DskipTests


# ===============================
# RUN STAGE
# ===============================
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Render requires dynamic port → use 8080 internally
EXPOSE 8080

# Run app
ENTRYPOINT ["java", "-jar", "app.jar"]

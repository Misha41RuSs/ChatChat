# ─── Backend Dockerfile (multi-stage) ───────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src/ ./src/
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir uploads

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

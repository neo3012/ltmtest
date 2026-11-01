# ============================================================
# Client Dockerfile — Network Programming Assignment
# ============================================================
# Multi-stage Java 21 build.
# ============================================================

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY src/ ./src/
RUN mvn -q -DskipTests package dependency:copy-dependencies

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/classes ./out
COPY --from=build /build/target/dependency ./lib
CMD ["java", "-cp", "out:lib/*", "caro.client.CliMain"]

# ============================================================
# Server Dockerfile — Network Programming Assignment
# ============================================================
# Multi-stage Java 21 build.
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY src/ ./src/
RUN find src -name '*.java' -print > sources.txt && javac -encoding UTF-8 -d out @sources.txt

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/out ./out
EXPOSE 5000
CMD ["java", "-cp", "out", "caro.server.Main"]

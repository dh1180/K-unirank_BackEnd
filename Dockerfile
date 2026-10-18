FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY backend/src ./src
RUN mvn -B --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S -G appgroup appuser
COPY --from=builder /build/target/kunirank-api-0.1.0-SNAPSHOT.jar /app/kunirank-api.jar
USER appuser
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/kunirank-api.jar"]

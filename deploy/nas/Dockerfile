FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy full source for a reliable multi-module build.
COPY . .
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

# App timezone and JVM defaults can be overridden by docker-compose env.
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx768m"

COPY --from=builder /workspace/gak-start/target/gak-start-1.0.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

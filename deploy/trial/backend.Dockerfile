FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/ ./
RUN mvn -pl yudao-dependencies,yudao-server -am -DskipTests clean package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/yudao-server/target/yudao-server.jar /app/yudao-server.jar
EXPOSE 48080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "/app/yudao-server.jar"]

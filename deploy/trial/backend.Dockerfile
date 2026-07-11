FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/ ./
RUN --mount=type=cache,id=rd-erp-maven-cache,target=/root/.m2/repository \
    mvn -pl rd-erp-server -am -DskipTests clean package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/rd-erp-server/target/rd-erp-server.jar /app/rd-erp-server.jar
EXPOSE 48080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "/app/rd-erp-server.jar"]

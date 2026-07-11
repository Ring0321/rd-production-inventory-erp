FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/rd-erp-server.jar /app/rd-erp-server.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
ENV ARGS=""

EXPOSE 48080

CMD java ${JAVA_OPTS} -jar /app/rd-erp-server.jar ${ARGS}

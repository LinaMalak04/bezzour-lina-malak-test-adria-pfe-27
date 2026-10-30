FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY target/config-service-1.0.0.jar app.jar
EXPOSE 8888
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=10 CMD wget -qO- http://localhost:8888/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

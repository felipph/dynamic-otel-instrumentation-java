FROM eclipse-temurin:21-jdk-alpine as build
WORKDIR /app
COPY sample-projects/sample-microservices/pom.xml ./sample-microservices/
COPY sample-projects/sample-microservices/src ./sample-microservices/src/
RUN cd sample-microservices && ./mvnw clean package -DskipTests || (apk add --no-cache maven && mvn clean package -DskipTests)

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Download OpenTelemetry Java Agent
RUN mkdir -p /opt/otel && \
    apk add --no-cache curl && \
    curl -L -o /opt/otel/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar

# Copy the application JAR
COPY --from=build /app/sample-microservices/target/sample-microservices-1.0.0.jar /opt/app/app.jar

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /opt/app/app.jar"]

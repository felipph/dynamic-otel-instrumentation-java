# ============================================================================
# Dockerfile for JBoss/WildFly with Dynamic OTel Instrumentation Agent
# ============================================================================
#
# Build:
#   docker build -t otel-jboss -f docker/Dockerfile .
#
# Run:
#   docker run -p 8080:8080 -p 9990:9990 otel-jboss
#
# Note: Este Dockerfile é opcional. O docker-compose.yml usa a imagem
# oficial do WildFly diretamente com volumes para mais flexibilidade.
#
# ============================================================================

# Usar WildFly 26 (versão open-source compatível com JBoss EAP 7.4)
FROM quay.io/wildfly/wildfly:26.1.3.Final-jdk11

LABEL maintainer="OpenTelemetry Dynamic Instrumentation"
LABEL description="WildFly 26 with Dynamic OpenTelemetry Instrumentation Agent"
LABEL version="1.0.0"

# Variáveis de ambiente
ENV JAVA_OPTS="-Xms1g -Xmx2g -Djava.net.preferIPv4Stack=true"
ENV INSTRUMENTATION_CONFIG_PATH=/opt/otel/config/instrumentation.json
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
ENV OTEL_SERVICE_NAME=sample-spring-mvc-app
ENV OTEL_EXPORTER_OTLP_TIMEOUT=30000

# Criar diretórios
USER root
RUN mkdir -p /opt/jboss/agents \
    && mkdir -p /opt/otel/config \
    && chown -R jboss:jboss /opt/jboss /opt/otel
USER jboss

# Copiar agente (se existir durante o build)
# Nota: Normalmente montado via volume no docker-compose
COPY --chown=jboss:jboss target/dynamic-instrumentation-agent-1.1.0.jar /opt/jboss/agents/dynamic-instrumentation-agent.jar

# Copiar configuração padrão
COPY --chown=jboss:jboss docker/configs/instrumentation.json /opt/otel/config/instrumentation.json

# Configurar agente via JAVA_OPTS_APPEND
ENV JAVA_OPTS_APPEND="-javaagent:/opt/jboss/agents/dynamic-instrumentation-agent.jar \
    -Dotel.service.name=${OTEL_SERVICE_NAME} \
    -Dotel.exporter.otlp.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT} \
    -Dotel.exporter.otlp.timeout=${OTEL_EXPORTER_OTLP_TIMEOUT} \
    -Dinstrumentation.config.path=${INSTRUMENTATION_CONFIG_PATH}"

# Expor portas
EXPOSE 8080 8443 9990 8787

# Volume para configuração (permite updates em runtime)
VOLUME ["/opt/otel/config"]

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

# Command padrão
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]

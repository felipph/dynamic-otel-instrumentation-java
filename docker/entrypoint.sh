#!/bin/bash
# Entrypoint script for WildFly with OTel Agent
# This script sets up JAVA_OPTS before WildFly's standalone.sh runs

# Add javaagent to JAVA_OPTS (before WildFly adds its own settings)
export JAVA_OPTS="-javaagent:/opt/jboss/agents/dynamic-instrumentation-agent.jar $JAVA_OPTS"

# Execute WildFly standalone
exec /opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 "$@"

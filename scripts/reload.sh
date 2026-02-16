#!/bin/bash

# Reload script for triggering configuration reload via JMX
# This script triggers a configuration reload in a running JVM

set -e
set -u

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Default values
MGMT_PORT=9990
HOST=localhost
USERNAME=admin
PASSWORD=admin
CONTAINER_NAME=otel-jboss

# Usage function
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Trigger configuration reload via JMX.

Options:
    -p, --port PORT         Management port (default: 9990)
    -h, --host HOST         Management host (default: localhost)
    -u, --user USER         Management username (default: admin)
    -P, --pass PASS         Management password (default: admin)
    -c, --container NAME    Docker container name (default: otel-jboss)
    --help                  Show this help message

Examples:
    # Local JBoss with default management port
    $0

    # Remote JBoss
    $0 -h jboss-server -p 9990

    # With authentication
    $0 -u admin -P secret123

    # Specify container name
    $0 -c my-jboss-container

EOF
    exit 1
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -p|--port)
            MGMT_PORT="$2"
            shift 2
            ;;
        -h|--host)
            HOST="$2"
            shift 2
            ;;
        -u|--user)
            USERNAME="$2"
            shift 2
            ;;
        -P|--pass)
            PASSWORD="$2"
            shift 2
            ;;
        -c|--container)
            CONTAINER_NAME="$2"
            shift 2
            ;;
        --help)
            usage
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            ;;
    esac
done

echo "=========================================="
echo "Configuration Reload via WildFly Management"
echo "=========================================="
echo "Host: $HOST"
echo "Port: $MGMT_PORT"
echo "Container: $CONTAINER_NAME"
echo ""

# MBean details
MBEAN_NAME="com.otel.dynamic:type=ConfigManager"
OPERATION="reloadConfiguration"

# Check container is running
check_container() {
    if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "${RED}Container '$CONTAINER_NAME' is not running${NC}"
        return 1
    fi
    return 0
}

# ---------------------------------------------------------------------------
# Method 1: docker exec + JMX Attach API (most reliable)
#   Attaches to the running WildFly JVM from inside the container using
#   the com.sun.tools.attach API. No RMI, no network ports needed.
# ---------------------------------------------------------------------------
try_attach() {
    echo "Method 1: JMX Attach API inside container..."

    check_container || return 1

    # Find the WildFly PID inside the container
    JBOSS_PID=$(docker exec "$CONTAINER_NAME" pgrep -f 'jboss.home' 2>/dev/null | head -1)
    if [ -z "$JBOSS_PID" ]; then
        JBOSS_PID=$(docker exec "$CONTAINER_NAME" pgrep -f 'standalone' 2>/dev/null | head -1)
    fi

    if [ -z "$JBOSS_PID" ]; then
        echo -e "${YELLOW}Could not find JBoss PID inside container${NC}"
        return 1
    fi

    echo "  Found JBoss PID: $JBOSS_PID"

    # Write, compile, and run a Java program that uses the Attach API
    # to connect to the running WildFly JVM and invoke the MBean
    docker exec "$CONTAINER_NAME" bash -c '
        JBOSS_PID='"$JBOSS_PID"'

        # Find tools.jar (only needed for JDK 8; JDK 11+ has jdk.attach module)
        JAVA_HOME_DIR="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(command -v java) 2>/dev/null) 2>/dev/null) 2>/dev/null)}"
        TOOLS_JAR=""
        if [ -n "$JAVA_HOME_DIR" ] && [ -f "$JAVA_HOME_DIR/lib/tools.jar" ]; then
            TOOLS_JAR="$JAVA_HOME_DIR/lib/tools.jar"
        fi

        cat > /tmp/JmxReload.java << '\''JAVAEOF'\''
import javax.management.*;
import javax.management.remote.*;
import com.sun.tools.attach.VirtualMachine;
import java.util.Properties;

public class JmxReload {
    public static void main(String[] args) throws Exception {
        String pid = args[0];
        System.out.println("Attaching to JVM PID: " + pid);

        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            // Ensure the management agent is loaded
            String connectorAddr = vm.getAgentProperties()
                .getProperty("com.sun.management.jmxremote.localConnectorAddress");

            if (connectorAddr == null) {
                // Load the management agent
                String agent = vm.getSystemProperties().getProperty("java.home")
                    + "/lib/management-agent.jar";
                try {
                    vm.loadAgent(agent);
                } catch (Exception e) {
                    // On newer JDKs, the agent may already be loaded or not needed
                    // Try starting local management agent via jcmd-style property
                    vm.startLocalManagementAgent();
                }
                connectorAddr = vm.getAgentProperties()
                    .getProperty("com.sun.management.jmxremote.localConnectorAddress");
            }

            if (connectorAddr == null) {
                System.err.println("ERROR: Could not get JMX local connector address");
                System.exit(1);
            }

            System.out.println("Connecting to: " + connectorAddr);
            JMXServiceURL url = new JMXServiceURL(connectorAddr);
            JMXConnector connector = JMXConnectorFactory.connect(url);
            try {
                MBeanServerConnection mbsc = connector.getMBeanServerConnection();
                ObjectName name = new ObjectName("com.otel.dynamic:type=ConfigManager");

                if (!mbsc.isRegistered(name)) {
                    System.err.println("ERROR: MBean not registered: " + name);
                    // List available MBeans for debugging
                    System.err.println("Available MBeans matching com.otel.*:");
                    for (ObjectName on : mbsc.queryNames(new ObjectName("com.otel.*:*"), null)) {
                        System.err.println("  " + on);
                    }
                    System.exit(1);
                }

                Object result = mbsc.invoke(name, "reloadConfiguration", null, null);
                System.out.println("Reload result: " + result);
            } finally {
                connector.close();
            }
        } finally {
            vm.detach();
        }
    }
}
JAVAEOF

        # Compile - need attach API on classpath
        COMPILE_CP="/tmp"
        if [ -n "$TOOLS_JAR" ]; then
            COMPILE_CP="$COMPILE_CP:$TOOLS_JAR"
        fi

        javac -cp "$COMPILE_CP" /tmp/JmxReload.java -d /tmp 2>&1
        if [ $? -ne 0 ]; then
            echo "ERROR: Failed to compile JmxReload.java"
            exit 1
        fi

        # Run - need attach API on classpath
        RUN_CP="/tmp"
        if [ -n "$TOOLS_JAR" ]; then
            RUN_CP="$RUN_CP:$TOOLS_JAR"
        fi

        java -cp "$RUN_CP" JmxReload "$JBOSS_PID"
        EXIT_CODE=$?
        rm -f /tmp/JmxReload.java /tmp/JmxReload.class
        exit $EXIT_CODE
    '

    return $?
}

# ---------------------------------------------------------------------------
# Method 2: WildFly HTTP Management API (curl, digest auth on port 9990)
# ---------------------------------------------------------------------------
try_wildfly_http() {
    echo "Method 2: WildFly HTTP Management API..."

    if ! nc -z "$HOST" "$MGMT_PORT" 2>/dev/null; then
        echo -e "${YELLOW}Cannot connect to management port $MGMT_PORT on $HOST${NC}"
        return 1
    fi

    MGMT_URL="http://$HOST:$MGMT_PORT/management"

    # WildFly exposes JMX MBeans under /core-service=platform-mbean
    # Custom MBeans can be invoked via the jmx subsystem if the jmx-remoting is configured
    # Try reading the MBean first to verify connectivity
    RESPONSE=$(curl -s --digest -u "$USERNAME:$PASSWORD" \
        -H "Content-Type: application/json" \
        -d '{
            "operation": "read-resource",
            "address": [{"core-service": "platform-mbean"}, {"type": "runtime"}]
        }' \
        "$MGMT_URL" 2>/dev/null)

    if ! echo "$RESPONSE" | grep -qi '"outcome".*:.*"success"'; then
        echo -e "${YELLOW}Could not connect to WildFly management API${NC}"
        return 1
    fi

    echo "  Connected to WildFly management API"

    # WildFly's DMR management only exposes standard platform MBeans
    # Custom MBeans (like ours) require the jmx subsystem with remoting
    # This method won't work for custom MBeans without additional WildFly config
    echo -e "${YELLOW}  WildFly DMR does not expose custom MBeans directly${NC}"
    echo -e "${YELLOW}  (only platform MBeans are accessible via /management)${NC}"
    return 1
}

# ---------------------------------------------------------------------------
# Method 3: docker exec + jboss-cli.sh
# ---------------------------------------------------------------------------
try_jboss_cli() {
    echo "Method 3: jboss-cli.sh inside container..."

    check_container || return 1

    # jboss-cli can invoke JMX MBeans via the jmx subsystem
    RESULT=$(docker exec "$CONTAINER_NAME" \
        /opt/jboss/wildfly/bin/jboss-cli.sh --connect \
        --command="/subsystem=jmx:invoke(mbean=\"$MBEAN_NAME\",operation=\"$OPERATION\")" \
        2>&1) || true

    if echo "$RESULT" | grep -qi '"outcome".*=>.*"success"\|result.*=>.*true'; then
        echo "$RESULT"
        return 0
    fi

    echo -e "${YELLOW}  jboss-cli response: $RESULT${NC}"
    return 1
}

# ---------------------------------------------------------------------------
# Execute: try methods in order of reliability
# ---------------------------------------------------------------------------

# Disable set -e for the try methods (they return non-zero on failure)
set +e

# Method 1: Attach API (most reliable - same JVM, no network)
if try_attach; then
    echo ""
    echo -e "${GREEN}Configuration reload triggered via JMX Attach API!${NC}"
    echo ""
    exit 0
fi
echo -e "${YELLOW}Attach API did not work, trying next method...${NC}"
echo ""

# Method 2: WildFly HTTP Management
if try_wildfly_http; then
    echo ""
    echo -e "${GREEN}Configuration reload triggered via WildFly HTTP Management!${NC}"
    echo ""
    exit 0
fi
echo -e "${YELLOW}HTTP Management did not work, trying next method...${NC}"
echo ""

# Method 3: jboss-cli
if try_jboss_cli; then
    echo ""
    echo -e "${GREEN}Configuration reload triggered via jboss-cli!${NC}"
    echo ""
    exit 0
fi

# All methods failed
echo ""
echo -e "${RED}All reload methods failed.${NC}"
echo ""
echo "Troubleshooting:"
echo "  1. Ensure container '$CONTAINER_NAME' is running: docker ps"
echo "  2. Check if MBean is registered in app logs:"
echo "     docker logs $CONTAINER_NAME 2>&1 | grep ConfigManager"
echo "  3. Verify the extension JAR is loaded:"
echo "     docker logs $CONTAINER_NAME 2>&1 | grep dynamic-instrumentation"
echo ""
exit 1

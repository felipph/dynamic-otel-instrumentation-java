# Guia Rápido - Dynamic OpenTelemetry Instrumentation Agent

## Início Rápido - Stack Completa

### 1. Build do Agente
```bash
cd /home/felipph/DEV/java-otel-instrumentation
./scripts/build.sh
```

### 2. Build da Sample App
```bash
cd /home/felipph/DEV/sample-spring-mvc-app
./scripts/build.sh
```

### 3. Iniciar Stack Completa
```bash
cd /home/felipph/DEV/java-otel-instrumentation
./scripts/start-stack.sh
```

### 4. Testar
```bash
curl http://localhost:8080/sample/api/products
```

### 5. Ver Traces
Abra http://localhost:16686 (Jaeger UI)

---

## URLs da Stack

| Serviço | URL |
|---------|-----|
| **Aplicação** | http://localhost:8080/sample |
| API Produtos | http://localhost:8080/sample/api/products |
| API Clientes | http://localhost:8080/sample/api/customers |
| API Pedidos | http://localhost:8080/sample/api/orders |
| **Jaeger UI** | http://localhost:16686 |
| OTLP gRPC | localhost:4317 |
| OTLP HTTP | localhost:4318 |
| PostgreSQL | localhost:5432 |
| JMX | localhost:9990 |

---

## Comandos Úteis

### Stack Docker

```bash
# Iniciar stack completa
./scripts/start-stack.sh

# Parar stack
./scripts/stop-stack.sh

# Ver logs
docker logs -f otel-jboss

# Ver todos os logs
cd docker && docker-compose logs -f

# Status dos containers
cd docker && docker-compose ps
```

### Testar API

```bash
# Produtos
curl http://localhost:8080/sample/api/products

# Criar cliente
curl -X POST http://localhost:8080/sample/api/customers \
  -H "Content-Type: application/json" \
  -d '{"email":"test@email.com","firstName":"João","lastName":"Silva"}'

# Criar pedido
curl -X POST http://localhost:8080/sample/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"items":[{"productId":1,"quantity":1}],"paymentMethod":"CREDIT_CARD"}'
```

### JMX - Recarregar Config

```bash
# Encontrar PID (geralmente 1 em containers Docker)
docker exec otel-jboss ps aux | grep wildfly

# Recarregar configuração (hot reload)
docker exec otel-jboss jcmd 1 JMX.invoke \
  com.otel.dynamic:type=ConfigManager reloadConfiguration

# Habilitar debug
docker exec otel-jboss jcmd 1 JMX.invoke \
  com.otel.dynamic:type=ConfigManager setDebugEnabled true

# Verificar estado
docker exec otel-jboss jcmd 1 JMX.invoke \
  com.otel.dynamic:type=ConfigManager isDebugEnabled
```

> **Nota:** O registro do MBean é adiado em ~30 segundos após a inicialização da JVM para permitir que servidores de aplicação (WildFly/JBoss) inicializem completamente.

---

## Perfis Docker Compose

### Jaeger (Padrão - Desenvolvimento)
```bash
cd docker
docker-compose up -d
# ou explicitamente:
docker-compose --profile jaeger up -d
```

### Com pgAdmin
```bash
cd docker
docker-compose --profile admin up -d
# pgAdmin: http://localhost:5050
```

---

## Estrutura de Arquivos

```
java-otel-instrumentation/
├── docker/
│   ├── docker-compose.yml      # Stack completa
│   ├── otel-collector-config.yaml
│   ├── Dockerfile
│   └── configs/
│       └── instrumentation.json
├── scripts/
│   ├── build.sh
│   ├── start-stack.sh          # Iniciar tudo
│   ├── stop-stack.sh           # Parar tudo
│   └── reload.sh
├── target/
│   └── dynamic-instrumentation-agent-1.0.0.jar
├── README.md
├── QUICKSTART.md
├── ARCHITECTURE.md
└── ROADMAP.md
```

---

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `INSTRUMENTATION_CONFIG_PATH` | `/opt/otel/config/instrumentation.json` | Caminho do arquivo de configuração |
| `OTEL_SERVICE_NAME` | — | Nome do serviço nos traces |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | Endpoint do collector OTLP |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | Protocolo: `grpc` ou `http/protobuf` |

### Propriedades do Sistema

| Propriedade | Padrão | Descrição |
|-------------|--------|-----------|
| `instrumentation.config.path` | `/opt/otel/config/instrumentation.json` | Caminho do JSON (tem precedência sobre env var) |

---

## Configuração de Instrumentação

Arquivo: `docker/configs/instrumentation.json`

```json
{
  "packages": [
    {
      "packageName": "com.sample.app",
      "recursive": true,
      "annotations": [
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.stereotype.Controller"
      ]
    }
  ],
  "instrumentations": [
    {
      "className": "com.sample.app.ejb.service.IOrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.customer_id" },
        { "argIndex": 0, "methodCall": "getPaymentMethod", "attributeName": "app.payment_method" }
      ]
    },
    {
      "className": "com.sample.app.ejb.repository.OrderRepository",
      "methodName": "save",
      "attributes": [
        { "argIndex": 0, "methodCall": "getId", "attributeName": "app.order.id" },
        { "argIndex": 0, "methodCall": "getOrderNumber", "attributeName": "app.order.number" }
      ]
    }
  ]
}
```

### Hot Reload

Para recarregar a configuração após editar o arquivo (sem reiniciar a aplicação):

```bash
docker exec otel-jboss jcmd 1 JMX.invoke \
  com.otel.dynamic:type=ConfigManager reloadConfiguration
```

> O hot reload funciona retransformando as classes já carregadas com base na nova configuração. Classes podem ser adicionadas ou removidas da instrumentação dinamicamente.

---

## Troubleshooting

| Problema | Solução |
|----------|---------|
| Agente não carrega | Verificar se JAR existe em `target/` |
| Aplicação não responde | Aguardar ~2min, ver logs: `docker logs otel-jboss` |
| Sem traces | Verificar config JSON, habilitar debug via JMX |
| Erro de banco | Verificar se PostgreSQL está saudável |

### Logs

```bash
# Logs do agente
docker logs otel-jboss 2>&1 | grep "DynamicInstrumentation"

# Logs de erro
docker logs otel-jboss 2>&1 | grep -i error

# Logs completos
docker logs -f otel-jboss
```

---

## Próximos Passos

1. Verificar traces no Jaeger
2. Editar `instrumentation.json` para adicionar mais pontos
3. Recarregar configuração via JMX
4. Verificar novos atributos nos traces

---

Documentação completa em [README.md](README.md)

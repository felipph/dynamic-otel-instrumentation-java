# Roadmap - Dynamic OpenTelemetry Instrumentation

## Status Atual (v1.0.0)

### Features Implementadas
- [x] Instrumentação configurável via JSON
- [x] Instrumentação em nível de método (classes específicas)
- [x] Instrumentação em nível de pacote (todas as classes de um pacote)
- [x] Filtro por anotações em nível de pacote
- [x] Suporte a interfaces (instrumenta todas as implementações)
- [x] Extração de atributos customizados via reflexão
- [x] Detecção de interface (`code.instrumented.interface`)
- [x] Hot reload via JMX sem reiniciar a aplicação
- [x] MBean JMX para gerenciamento em runtime
- [x] Logging de debug configurável via JMX
- [x] Compatibilidade com JBoss/WildFly, Tomcat, Spring Boot
- [x] Docker Compose para ambiente de desenvolvimento
- [x] **Captura de Retorno de Métodos**
  - Extrair atributos do valor retornado pelo método
  - Sintaxe:
    ```json
    {
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.order_id" }
      ]
    }
    ```
- [x] **Extração de Atributos em Cadeia**
  - Suporte a chamadas de métodos encadeadas
  - Exemplo: `obj.getMethod1().getMethod2().getMethod3()`
  - Sintaxe: `"methodCall": "getCustomer.getAddress.getCity"`
- [x] **Projetos de Exemplo Spring Boot 3.x**
  - [x] sample-spring-webmvc - Spring WebMVC com JPA, Async, Virtual Threads
  - [x] sample-spring-webflux - Spring WebFlux com R2DBC e Reactive Streams
  - [x] sample-spring-batch - Spring Batch com chunk-oriented processing
  - [x] Scripts de teste para validação da instrumentação
  - [x] Documentação de uso dos projetos de exemplo (USING-SAMPLE-PROJECTS.md)

---

## Próximas Features (Backlog)

### Alta Prioridade

- [ ] Docker compose contendo com profiles para diferentes ambientes de execução. Um para jboss, outro para o springboot. O profile padrão deve incluir somente o jagger. Postgres pode existir para os profiles jboss ou springboot.

### Média Prioridade
- [ ] **Interface Web de Gerenciamento**
  - Dashboard para visualizar configuração atual
  - Editor visual de `instrumentation.json`
  - Botão para recarregar configuração
  - Visualização de classes instrumentadas
  - Endpoint para o instrumentador carregar configuração via HTTP

- [ ] **Configuração via HTTP**
  - Carregar configuração a partir de uma chamada HTTP
  - Suporte a múltiplas fontes (file, HTTP, config server)
  - Polling periódico para atualizações automáticas


### Baixa Prioridade
- [ ] **Filtro de Métodos**
  - Incluir/excluir métodos por padrão (regex)
  - Exemplo: incluir apenas métodos que começam com `get*`

- [ ] **Métricas de Instrumentação**
  - Contador de spans criados
  - Latência média dos spans
  - Erros durante extração de atributos
  - Expor via Prometheus endpoint

- [ ] **Suporte a Spans Assíncronos**
  - Propagar contexto para threads filhas
  - Suporte a @Async, CompletableFuture, etc.

- [ ] **Sampling Configurável**
  - Configurar taxa de sampling por classe/método
  - Reduzir overhead em métodos de alta frequência

---

## Ideias para Discussão

- Suporte a múltiplos arquivos de configuração (merge automático)
- Validação de configuração com feedback de erros
- Exportação de configuração atual via API
- Integração com OpenTelemetry Collector para configuração remota
- Suporte a scripts Groovy/Javascript para extração de atributos complexos

---

## Contribuindo

Sinta-se à vontade para abrir issues com sugestões ou pull requests com implementações.

### Como Priorizamos
1. Impacto na observabilidade
2. Facilidade de implementação
3. Compatibilidade com versões existentes
4. Feedback da comunidade

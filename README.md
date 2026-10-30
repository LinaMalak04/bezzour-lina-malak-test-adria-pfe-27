# Plateforme bancaire — épreuve technique Adria

Microservices Spring Cloud, assistant Spring AI / MCP, Kafka, Keycloak, Angular, détection de fraude en Python.

| Module | Port | Rôle |
| --- | --- | --- |
| `discovery-service` | 8761 | Annuaire Eureka |
| `config-service` | 8888 | Configuration, dont `bank.transaction.max-amount=1000` |
| `gateway-service` | 8080 | Point d'entrée unique, contrôle des JWT Keycloak |
| `account-service` | 8081 | Comptes : REST, GraphQL, MCP |
| `transaction-service` | 8082 | Transactions et virements : REST, GraphQL, MCP, producteur Kafka |
| `transaction-audit-service` | 8083 | Consumer Kafka, journalisation seule |
| `chatbot-service` | 8087 | Agent Spring AI, client des serveurs MCP |
| `bank-frontend` | 4200 | Client Angular |
| `fraud-detection/` | — | Classification de fraude (scikit-learn) |

## Lancer

```bash
mvn -B package -DskipTests
docker compose up -d --build --wait
```

- Application : http://localhost:4200 — Eureka : http://localhost:8761
- Si les ports 8761 ou 8080 sont pris : créer un `.env` avec `EUREKA_HOST_PORT` et `GATEWAY_HOST_PORT`.
- Assistant : `GROQ_API_KEY=... docker compose up -d chatbot-service` (toute API compatible OpenAI via `LLM_BASE_URL`).
- Sécurité : `BANK_SECURITY_ENABLED=true docker compose up -d gateway-service`.

## Tester

```bash
mvn -B verify                   # tests d'intégration (Kafka embarqué)
python e2e/run_e2e.py           # contrôles de bout en bout sur la stack Docker
python fraud-detection/fraud_detection.py [bank_transactions.csv]
```

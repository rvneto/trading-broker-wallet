# Broker Wallet API 💰

Microserviço responsável pela gestão de saldo financeiro e custódia de ativos do ecossistema **My Broker B3**.

## 🚀 Tecnologias
* Java 21 / Spring Boot 3
* MySQL (Persistência de saldo e posições)
* Flyway (Versionamento de banco)
* Redis (Consulta de preços em tempo real - Shared Cache)

## 🏗️ Arquitetura de Dados
Este serviço utiliza uma abordagem de **Shared Cache** para simplificar a PoC:
* Consome dados diretamente do `broker-market-data-cache` (Redis) na porta `6379`.
* Armazena dados transacionais no MySQL `broker-wallet-db` na porta `3307`.

## 🛠️ Como rodar
1. Configure as variáveis no `application.yml` (DB_URL, DB_USER, DB_PASSWORD).
2. Execute o Maven install para baixar as dependências.
3. Inicie a aplicação. O Flyway executará as migrations automaticamente.

## 📖 Documentação da API

A documentação interativa completa está disponível via Swagger em: `http://localhost:8081/swagger-ui.html`

### Principais Endpoints

#### 1. Realizar Depósito
`POST /api/v1/wallet/{userId}/deposit?amount=1000.00`

**Exemplo de Resposta (200 OK):**
```json
{
  "id": 1,
  "userId": "roberto-123",
  "balance": 1000.0000,
  "currency": "BRL",
  "updatedAt": "2024-05-20T10:00:00"
}
```

#### 2. Consultar Resumo da Carteira (Equity)
`GET /api/v1/wallet/{userId}/summary`

Este endpoint consolida o saldo em conta com a valorização dos ativos em tempo real (Market Data).

**Exemplo de Resposta (200 OK):**
```json
{
  "userId": "roberto-123",
  "availableBalance": 1000.0000,
  "positionsValue": 5420.50,
  "totalEquity": 6420.50,
  "currency": "BRL"
}
```
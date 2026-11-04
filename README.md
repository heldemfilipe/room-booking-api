# room-booking-api

![CI](https://github.com/heldemfilipe/room-booking-api/actions/workflows/ci.yml/badge.svg)

API de reservas de salas por horário, com Spring Boot e PostgreSQL: impede que duas pessoas ocupem o mesmo intervalo,
autentica com Keycloak, faz cache em Redis e expõe métricas no Grafana. Tudo roda local com Docker Compose.

> **Status:** projeto de estudo, em construção. Por enquanto só existe o esqueleto gerado (Etapa 0). As seções abaixo
> são preenchidas ao fechar cada etapa; o que ainda não existe está marcado como "a fazer".

## Problema

Reservar uma sala parece um CRUD, mas tem três dificuldades que um CRUD simples não mostra:

1. **Concorrência:** duas pessoas reservam a mesma sala no mesmo horário ao mesmo tempo. "Consultar se há conflito e
   inserir" tem uma janela de corrida; só o banco (ou um lock) fecha essa janela.
2. **Segurança:** quem pode ler, quem pode escrever e quem pode cancelar a reserva de quem. O dono vem do token, nunca do
   corpo da requisição.
3. **Operação:** quando a API fica lenta ou devolve erro, achar a causa (métrica, log e trace) em vez de adivinhar.

O domínio é pequeno de propósito. Sala tem `id`, `nome` e `capacidade`. Reserva tem `id`, `salaId`, `usuarioId` (o `sub`
do JWT), `inicio`, `fim` e `status`. Usuários e senhas ficam só no Keycloak.

## Arquitetura

A fazer (Etapas 1 e 2). O desenho previsto: a API como resource server JWT, o PostgreSQL com o schema versionado em
Flyway, o Keycloak emitindo os tokens, o Redis no cache de salas e o Prometheus e o Grafana nas métricas.

## Tecnologias

| Peça | Escolha |
|---|---|
| Linguagem e framework | Java 25 e Spring Boot 4.1.2 (Web MVC, Data JPA, Bean Validation, Actuator) |
| Banco | PostgreSQL 17, com Flyway para o schema e o Hibernate só validando (`ddl-auto=validate`, a fazer na Etapa 1) |
| Segurança | Spring Security como resource server JWT, com Keycloak (a fazer, Etapa 2) |
| Cache | Redis (a fazer, Etapa 4) |
| Observabilidade | Micrometer, OpenTelemetry, Prometheus e Grafana (a fazer, Etapa 5) |
| Testes | JUnit 5 e Testcontainers com PostgreSQL de verdade, sem mock de banco |
| Build | Maven com wrapper (`./mvnw`), sem instalar o Maven |

## Como rodar

Requisitos: JDK 25 e Docker (o Testcontainers sobe o PostgreSQL dos testes).

```bash
./mvnw -B verify
```

O Docker Compose com o PostgreSQL, o Keycloak, o Redis, o Prometheus e o Grafana entra nas Etapas 1, 2, 4 e 5. Até lá,
não há como subir a API de ponta a ponta.

## Decisões

A fazer, ao fechar cada etapa.

| Decisão | Motivo |
|---|---|
| Spring Boot 4.1.2 (a estável atual do Initializr em 04/11/2026) e não a 4.1.1 do roteiro | O Initializr já oferece a 4.1.2 como padrão |
| Imagem `postgres:17` fixada nos testes | O gerador usa `postgres:latest`, que muda sem aviso e quebraria o teste de um dia para o outro |
| Nome do projeto `room-booking-api` (pacote `dev.heldem.reservas`) | É o nome do repositório; o domínio do código segue em português |

## Experimentos

A fazer. A Etapa 3 (reserva dupla, comparando três soluções), a Etapa 4 (cache) e a Etapa 5 (observabilidade) têm
tabelas de experimento com o resultado observado.

## Custo

**Zero.** Tudo roda na máquina local com Docker; nada é aplicado na AWS. A parte opcional do roteiro que subiria a API
no ECS não será feita.

## Roteiro

- [x] Etapa 0: repositório, projeto gerado e CI
- [ ] Etapa 1: modelo relacional, Flyway e CRUD
- [ ] Etapa 2: autenticação e autorização
- [ ] Etapa 3: concorrência e reserva dupla
- [ ] Etapa 4: cache com Redis
- [ ] Etapa 5: observabilidade
- [ ] Etapa 6: entrega

## Licença

MIT. Veja [LICENSE](LICENSE).

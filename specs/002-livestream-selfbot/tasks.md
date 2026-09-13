# Tasks: Livestream por self-bot

**Input**: Design documents from `/specs/002-livestream-selfbot/` (plan.md, spec.md, research.md, data-model.md, contracts/commands.md, quickstart.md)

**Prerequisites**: plan.md (sidecar Node gerenciado pelo bot, `LivestreamControlPort` HTTP local, `OwnerPolicy`, JaCoCo 90%), spec.md (US1 P1, US2 P2, US3 P2)

**Tests**: INCLUÍDOS — exigidos pela constituição (JaCoCo 90% linha/branch). TDD: teste primeiro (deve FALHAR), depois implementação. `HttpLivestreamSidecarAdapter` e `LivestreamSidecarProcess` são excluídos do JaCoCo (integração externa), mas ainda recebem teste com stub `com.sun.net.httpserver.HttpServer`.

**Organization**: tasks agrupadas por user story; cada story implementável/testável isoladamente. Base package `com.mutrabot`, layout Maven.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência)
- **[Story]**: US1 (join), US2 (leave), US3 (autorização)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: scaffold do sidecar Node, exclusões de cobertura e configuração

- [x] T001 Criar `streamer/package.json` com `@dank074/discord-video-stream@^6` e peer `discord.js-selfbot-v13` (`"type": "module"`), script `start`
- [x] T002 Criar `streamer/index.mjs` esqueleto: servidor HTTP em `127.0.0.1:$LIVESTREAM_SIDECAR_PORT`, checagem `Authorization: Bearer`, rota `GET /health` (retorna `starting` até o login de Y concluir)
- [x] T003 Adicionar exclusões JaCoCo em `pom.xml` para `com/mutrabot/adapter/out/streaming/**` e `com/mutrabot/bootstrap/LivestreamSidecarProcess*`
- [x] T004 [P] Adicionar placeholders em `.env.example`/`.env`: `LIVESTREAM_OWNER_ID`, `LIVESTREAM_USER_TOKEN`, `LIVESTREAM_SIDECAR_PORT=8790`, `LIVESTREAM_SIDECAR_SECRET`, `LIVESTREAM_FFMPEG_PATH`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: domínio, portas, autorização e mensagens — bloqueiam as user stories

**⚠️ CRITICAL**: nenhuma user story começa antes desta fase

- [x] T005 [P] Escrever `OwnerPolicyTest` (dono autorizado; qualquer outro não) em `src/test/java/com/mutrabot/application/OwnerPolicyTest.java` — deve FALHAR
- [x] T006 [P] Adicionar `LivestreamJoinCmd(GuildId, UserId, VoiceChannelId, String window)` e `LivestreamLeaveCmd(GuildId, UserId)` ao selado `src/main/java/com/mutrabot/domain/command/BotCommand.java`
- [x] T007 [P] Criar `LivestreamControlPort` com `Result` selado (`Ok`/`Unavailable`/`Failed`) em `src/main/java/com/mutrabot/application/port/out/LivestreamControlPort.java`
- [x] T008 [P] Criar `LivestreamJoinUseCase` e `LivestreamLeaveUseCase` em `src/main/java/com/mutrabot/application/port/in/`
- [x] T009 Implementar `OwnerPolicy` (`isOwner(UserId)`, `ownerId` do construtor) em `src/main/java/com/mutrabot/application/service/OwnerPolicy.java` até T005 passar
- [x] T010 [P] Adicionar mensagens PT-BR da live em `src/main/java/com/mutrabot/application/service/BotMessages.java` conforme `contracts/commands.md`
- [x] T011 Adicionar casos `livestream-join`/`livestream-leave` em `src/main/java/com/mutrabot/adapter/in/discord/CommandMapper.java` e cobertura em `src/test/java/com/mutrabot/adapter/in/discord/CommandMapperTest.java` (janela obrigatória, canal de voz do X)

**Checkpoint**: domínio e portas compilam; `OwnerPolicyTest` e `CommandMapperTest` verdes; `./mvnw "-Dtest=*ArchitectureTest" test` verde

---

## Phase 3: User Story 1 - Iniciar transmissão (Priority: P1) 🎯 MVP

**Goal**: `/livestream-join <janela>` faz Y entrar no canal de X e transmitir a janela via Go Live (FR-001–004, 009, 011; US1)

**Independent Test**: X em voz + janela aberta → `/livestream-join "Notepad"` → Y no canal de X + Go Live da janela; `desktop` transmite a tela inteira

### Tests for User Story 1 ⚠️

- [x] T012 [P] [US1] Escrever `LivestreamJoinServiceTest` (sucesso, sem voz, `Ok`/`Failed`/`Unavailable`, dono) em `src/test/java/com/mutrabot/application/LivestreamJoinServiceTest.java` — deve FALHAR
- [x] T013 [P] [US1] Escrever `HttpLivestreamSidecarAdapterTest` com `com.sun.net.httpserver.HttpServer` stub (200→`Ok`, 400→`Failed`, timeout/5xx→`Unavailable`) em `src/test/java/com/mutrabot/adapter/out/streaming/HttpLivestreamSidecarAdapterTest.java`

### Implementation for User Story 1

- [x] T014 [US1] Implementar `LivestreamJoinService` (pré-condição de voz, `OwnerPolicy`, mapeamento de `Result` → PT-BR) em `src/main/java/com/mutrabot/application/service/LivestreamJoinService.java` até T012 passar
- [x] T015 [US1] Implementar `HttpLivestreamSidecarAdapter` (`java.net.http.HttpClient`, Bearer, timeouts) em `src/main/java/com/mutrabot/adapter/out/streaming/HttpLivestreamSidecarAdapter.java` até T013 passar
- [x] T016 [US1] Implementar `POST /start` em `streamer/index.mjs`: `prepareStream` com `customInputOptions:["-f","gdigrab","-framerate","30"]`, `includeAudio:false`, `window`→`title=<janela>`/`desktop`; `login`+`joinVoice`+`playStream({type:"go-live"})`; estado single-session com `AbortController`; erros com motivo para 400
- [x] T017 [US1] Criar `LivestreamSidecarProcess` (`ProcessBuilder`, env sem `argv`, aguarda `/health`, shutdown no JVM) em `src/main/java/com/mutrabot/bootstrap/LivestreamSidecarProcess.java`
- [x] T018 [US1] Wiring em `src/main/java/com/mutrabot/bootstrap/BotApplication.java`: `OwnerPolicy`, `LivestreamJoinService`, adapter, spawn do sidecar (degradação graciosa), registro do command no `registerCommands`
- [x] T019 [US1] Estender `JdaCommandListener` em `src/main/java/com/mutrabot/adapter/in/discord/JdaCommandListener.java`: campo `LivestreamJoinUseCase`, `deferReply()` para `LivestreamJoinCmd`, dispatch

**Checkpoint**: `/livestream-join` funcional e testável isoladamente (MVP da feature)

---

## Phase 4: User Story 2 - Encerrar transmissão (Priority: P2)

**Goal**: `/livestream-leave` encerra o Go Live e faz Y sair do canal, idempotente (FR-005, 012; US2)

**Independent Test**: com live ativa → `/livestream-leave` → stream para + Y sai; repetir → `ℹ️ Não há nenhuma live ativa`

### Tests for User Story 2 ⚠️

- [x] T020 [P] [US2] Escrever `LivestreamLeaveServiceTest` (ativo, idle, dono, `Unavailable`) em `src/test/java/com/mutrabot/application/LivestreamLeaveServiceTest.java` — deve FALHAR

### Implementation for User Story 2

- [x] T021 [US2] Implementar `LivestreamLeaveService` em `src/main/java/com/mutrabot/application/service/LivestreamLeaveService.java` até T020 passar
- [x] T022 [US2] Implementar `POST /stop` em `streamer/index.mjs`: `abortController.abort()` + `streamer.leaveVoice()`, idempotente (idle→200)
- [x] T023 [US2] Wiring em `src/main/java/com/mutrabot/bootstrap/BotApplication.java` e dispatch de `LivestreamLeaveCmd` em `JdaCommandListener.java`

**Checkpoint**: US1 e US2 funcionam independentemente (iniciar e encerrar)

---

## Phase 5: User Story 3 - Uso restrito ao dono (Priority: P2)

**Goal**: somente X executa os comandos; demais recebem recusa efêmera e nada chega ao sidecar (FR-006; US3, SC-002)

**Independent Test**: conta que não é X envia os dois comandos → recusa efêmera, sem chamada ao sidecar

### Tests for User Story 3 ⚠️

- [x] T024 [P] [US3] Adicionar casos de não-dono a `LivestreamJoinServiceTest` e `LivestreamLeaveServiceTest` (recusa antes de qualquer chamada a `LivestreamControlPort` — `verifyNoInteractions`)
- [x] T025 [P] [US3] Adicionar caso de autorização ao `CommandMapperTest`/teste de listener (não-dono não dispara efeito)

### Implementation for User Story 3

- [x] T026 [US3] Garantir que ambos os serviços aplicam `OwnerPolicy` antes do port e respondem recusa efêmera em `LivestreamJoinService.java`/`LivestreamLeaveService.java`

**Checkpoint**: US1–US3 independentes e seguros

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T027 [P] Atualizar `README.md` (pré-requisitos Node/FFmpeg, variáveis, aviso de self-bot/burner, comandos, como Y entra)
- [x] T028 Rodar `./mvnw verify` (JaCoCo 90% linha/branch), fechar lacunas e revisar `pom.xml` (exclusões só de integração)
- [x] T029 Rodar `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` (60%) e matar sobreviventes em `OwnerPolicy`/serviços
- [ ] T030 Validar `specs/002-livestream-selfbot/quickstart.md` cenários 1–10 (manual; exige token real, guild e conta Y) — bloquear se faltar credencial

---

## Dependencies & Execution Order

- **Setup (1)**: imediato; T003 e T004 paralelos.
- **Foundational (2)**: após Setup — BLOQUEIA as stories. T005 antes de T009; T006–T008 paralelos.
- **US1 (3)**: após Foundational. T012/T013 (testes) → T014–T019. T019 depende de T014.
- **US2 (4)**: após Foundational; pode seguir US1. T020 → T021 → T022 → T023.
- **US3 (5)**: depende de US1+US2 (testes de recusa); implementação já está nos serviços.
- **Polish (6)**: após as stories.

### Parallel Opportunities

- T005/T006/T007/T008/T010 juntos (arquivos distintos).
- T012/T013 juntos; T016 (Node) em paralelo a T014/T015 (Java).
- T024/T025 juntos; T027 em paralelo com T028/T029.

## Notes

- [P] = arquivos diferentes, sem dependência de tarefa incompleta (`BotApplication.java` e `JdaCommandListener.java` são sequenciais entre fases — nunca paralelos).
- Segredos nunca em `argv`/logs; passar por ambiente.
- Cada story completa antes de avançar (ou em paralelo, se staffed).
- Commits atômicos por grupo lógico.

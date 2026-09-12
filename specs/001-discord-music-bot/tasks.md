# Tasks: Discord Music Bot

**Input**: Design documents from `/specs/001-discord-music-bot/` (plan.md, spec.md, research.md, data-model.md, contracts/commands.md, quickstart.md)

**Prerequisites**: plan.md (Java 25, Maven, JDA 6 + JDAVE, LavaPlayer fork + youtube-source, hexagonal, JaCoCo 90%, PITest 60%), spec.md (US1 P1, US2 P2, US3 P2, US4 P3)

**Tests**: INCLUDED — explicitly required by user constraints (JaCoCo 90% line/branch gate on `./mvnw verify`, PITest 60% mutation gate on `main`/nightly, ArchUnit hexagonal rules). TDD order within each story: tests FIRST (must FAIL), then implementation. Survivor review per research.md hints (`1 sobrevivente = 1 teste faltante`).

**Organization**: Tasks grouped by user story; each story independently implementable and testable. All paths Maven layout, base package `com.mutrabot`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths in every description

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven project init, dependencies, quality gates

- [X] T001 Create Maven project skeleton with hexagonal package layout in `pom.xml`, `.mvn/wrapper/`, `src/main/java/com/mutrabot/`, `src/test/java/com/mutrabot/`
- [X] T002 Declare dependencies and plugin repositories in `pom.xml` (JDA 6.x, JDAVE, lavaplayer fork, youtube-source + `https://maven.lavalink.dev/releases`, JUnit 5, Mockito, AssertJ, ArchUnit, exec-maven-plugin)
- [X] T003 Configure `maven-compiler-plugin` (`release=25`), Surefire (JUnit 5) and `exec-maven-plugin` (`bootstrap.BotApplication`) in `pom.xml`
- [X] T004 Configure `jacoco-maven-plugin` 90% LINE + 90% BRANCH `check` bound to `verify` in `pom.xml`
- [X] T005 Configure `pitest-maven` + `pitest-junit5-plugin` (`mutators=DEFAULTS`, `mutationThreshold=60`, NO lifecycle binding) in `pom.xml`
- [X] T006 [P] Create `.gitignore` (target/, IDE files, `.env`/token exclusion)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain, ports, bootstrap wiring, JDA/VT skeleton — MUST complete before ANY user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T007 Create domain value model in `src/main/java/com/mutrabot/domain/model/` (`Track.java`, `SourceKind.java`, `Requester.java`, `GuildId.java`, `UserId.java`, `VoiceChannelId.java`)
- [X] T008 Create `GuildQueue.java` aggregate + `PlaybackState.java` + `VoiceSession.java` in `src/main/java/com/mutrabot/domain/model/`
- [X] T009 [P] Create `BotCommand.java` sealed interface + command records in `src/main/java/com/mutrabot/domain/command/`
- [X] T010 Create inbound ports in `src/main/java/com/mutrabot/application/port/in/` (`PlayUseCase`, `StopUseCase`, `ResumeUseCase`, `SkipUseCase`, `ListQueueUseCase`, `PingUseCase`)
- [X] T011 [P] Create outbound ports in `src/main/java/com/mutrabot/application/port/out/` (`TrackResolverPort`, `AudioPlaybackPort`, `MusicQueueRepository`, `InteractionResponderPort`)
- [X] T012 Create `InMemoryQueueAdapter.java` (`ConcurrentHashMap` + `ReentrantLock` per guild) in `src/main/java/com/mutrabot/adapter/out/persistence/`
- [X] T013 [P] Create VT `ExecutorConfig.java` singleton (`newThreadPerTaskExecutor`, virtual-thread factory) in `src/main/java/com/mutrabot/bootstrap/`
- [X] T014 Create JDA inbound skeleton in `src/main/java/com/mutrabot/adapter/in/discord/` (`JdaCommandListener.java` with `deferReply` + VT dispatch, `CommandMapper.java`, `JdaResponderAdapter.java`)
- [X] T015 Create bootstrap wiring in `src/main/java/com/mutrabot/bootstrap/` (`BotApplication.java`, `JdaConfig.java` with `DaveSessionFactory`, `AudioConfig.java` with `AudioPlayerManager` + youtube-source registration)
- [X] T016 [P] Create hexagonal `ArchitectureTest.java` (ArchUnit: domain only `java.base`, dependency directions) in `src/test/java/com/mutrabot/architecture/`
- [X] T017 [P] Create PT-BR `BotMessages.java` response/error catalog per `contracts/commands.md` in `src/main/java/com/mutrabot/adapter/in/discord/`

**Checkpoint**: Foundation ready — domain compiles, `ArchitectureTest` passes, user stories can now begin

---

## Phase 3: User Story 1 - Tocar música por link, busca ou playlist (Priority: P1) 🎯 MVP

**Goal**: `/play` com link (YouTube/SoundCloud/Spotify/Tidal), busca textual ou playlist; entra em voz, enfileira, toca sequencialmente (FR-002–006, FR-012–013, FR-016–017)

**Independent Test**: `/play` com (a) link de faixa, (b) termo de busca, (c) link de playlist → áudio no canal + fila ordenada + anúncios com solicitante; entrada ruim não derruba sessão (spec US1 scenarios 1–5, SC-001/SC-002/SC-004)

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T018 [P] [US1] Unit test play/playlist enqueue, order and 100-track truncation in `src/test/java/com/mutrabot/domain/GuildQueuePlayTest.java`
- [X] T019 [P] [US1] Unit test `PlayCommandService` (enqueue, `NotFound`, `LoadFailed` retryable, counts) in `src/test/java/com/mutrabot/application/PlayCommandServiceTest.java`
- [X] T020 [P] [US1] Contract test `/play` responses per `specs/001-discord-music-bot/contracts/commands.md` in `src/test/java/com/mutrabot/contract/PlayCommandContractTest.java`

### Implementation for User Story 1

- [X] T021 [P] [US1] Create `ResolverResult.java` sealed (`ResolvedTrack`/`ResolvedPlaylist`/`NotFound`/`LoadFailed`) in `src/main/java/com/mutrabot/domain/result/`
- [X] T022 [P] [US1] Implement `LavaplayerResolverAdapter.java` (`loadItemOrdered`→`CompletableFuture` on VT, metadata→`ytsearch` fallback, playlist order) in `src/main/java/com/mutrabot/adapter/out/audio/`
- [X] T023 [P] [US1] Implement `LavaplayerPlaybackAdapter.java` + `AudioPlayerSendHandler.java` + `TrackMapper.java` (1 player/guild, non-blocking 20ms Opus) in `src/main/java/com/mutrabot/adapter/out/audio/`
- [X] T024 [US1] Implement `PlayCommandService.java` with voice precondition FR-013 in `src/main/java/com/mutrabot/application/service/` (depends on T021, T022)
- [X] T025 [US1] Wire `/play` (option `query`, `deferReply` + VT dispatch) in `src/main/java/com/mutrabot/adapter/in/discord/JdaCommandListener.java` and `CommandMapper.java`

**Checkpoint**: US1 fully functional and testable independently — music plays from all sources, MVP deployable

---

## Phase 4: User Story 2 - Controlar a reprodução (Priority: P2)

**Goal**: `/stop` pausa, `/resume` retoma do ponto, `/skip` pula p/ próxima; fila vazia → aviso + timer 5min p/ desconectar (FR-007–009, FR-015)

**Independent Test**: fila 2+ → `/stop` pausa, `/resume` continua do ponto, `/skip` troca de faixa com anúncio; última faixa → `A fila acabou` + disconnect após 5min; comandos com nada tocando → `Nada ...` sem erro (spec US2 scenarios 1–4)

### Tests for User Story 2 ⚠️

- [X] T026 [P] [US2] Unit test stop/resume/skip transitions and empty states in `src/test/java/com/mutrabot/domain/GuildQueueControlTest.java`
- [X] T027 [P] [US2] Service test stop/resume/skip + idle-timer start in `src/test/java/com/mutrabot/application/PlaybackControlServiceTest.java`
- [X] T028 [P] [US2] Contract test `/stop` `/resume` `/skip` responses per `specs/001-discord-music-bot/contracts/commands.md` in `src/test/java/com/mutrabot/contract/PlaybackControlContractTest.java`

### Implementation for User Story 2

- [X] T029 [US2] Implement `StopCommandService.java`, `ResumeCommandService.java`, `SkipCommandService.java` in `src/main/java/com/mutrabot/application/service/`
- [X] T030 [US2] Add pause/resume/stop/next ops to `src/main/java/com/mutrabot/adapter/out/audio/LavaplayerPlaybackAdapter.java` (same file as T023, sequential)
- [X] T031 [P] [US2] Implement `IdleDisconnectService.java` (5-min idle countdown, announce + disconnect, reconnect on next play) in `src/main/java/com/mutrabot/application/service/`
- [X] T032 [US2] Wire `/stop` `/resume` `/skip` in `src/main/java/com/mutrabot/adapter/in/discord/JdaCommandListener.java` and `CommandMapper.java`

**Checkpoint**: US1 AND US2 work independently — full playback control with predictable idle behavior

---

## Phase 5: User Story 3 - Consultar a fila (Priority: P2)

**Goal**: `/queue` mostra faixa atual em destaque + próximas ordenadas com solicitante, com paginação e mensagem de vazia (FR-006, FR-010)

**Independent Test**: 3 faixas de 2 usuários → `/queue` lista ordem + solicitante de cada + atual em destaque; fila vazia → mensagem de vazia; fila grande → paginação sem estourar limite (spec US3 scenarios 1–2, SC-006)

### Tests for User Story 3 ⚠️

- [X] T033 [P] [US3] Service/format test (requesters, empty, pagination) in `src/test/java/com/mutrabot/application/ListQueueServiceTest.java`
- [X] T034 [P] [US3] Contract test `/queue` responses per `specs/001-discord-music-bot/contracts/commands.md` in `src/test/java/com/mutrabot/contract/QueueCommandContractTest.java`

### Implementation for User Story 3

- [X] T035 [US3] Implement `ListQueueService.java` with pagination in `src/main/java/com/mutrabot/application/service/`
- [X] T036 [US3] Wire `/queue` in `src/main/java/com/mutrabot/adapter/in/discord/JdaCommandListener.java` and `CommandMapper.java`

**Checkpoint**: US1–US3 work independently — transparent queue with attribution

---

## Phase 6: User Story 4 - Verificar bot online (Priority: P3)

**Goal**: `/ping` → `pong` (+ latência) em poucos segundos, sem canal de voz/fila (FR-011)

**Independent Test**: `/ping` com bot online → `pong` em poucos segundos, isolado de voz/fila (spec US4 scenario 1)

### Tests for User Story 4 ⚠️

- [X] T037 [P] [US4] Unit test `PingService` (pong + gateway/rest latency) in `src/test/java/com/mutrabot/application/PingServiceTest.java`

### Implementation for User Story 4

- [X] T038 [US4] Implement `PingCommandService.java` in `src/main/java/com/mutrabot/application/service/`
- [X] T039 [US4] Wire `/ping` and register all 6 slash commands in `src/main/java/com/mutrabot/adapter/in/discord/JdaCommandListener.java` and `src/main/java/com/mutrabot/bootstrap/JdaConfig.java`

**Checkpoint**: All user stories independently functional — full MVP command set

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates, mutation sweep, extensibility proof, end-to-end validation

- [X] T040 Prove FR-012/SC-005 extensibility: add 7th `help` command as new record + UseCase + registry only in `src/main/java/com/mutrabot/domain/command/`, `src/main/java/com/mutrabot/application/service/`, `src/main/java/com/mutrabot/adapter/in/discord/CommandMapper.java` (no core changes)
- [X] T041 Run `./mvnw verify` to enforce JaCoCo 90% LINE+BRANCH gate, close gaps, review `target/site/jacoco/index.html` and `pom.xml` exclusions (config/DTO only)
- [X] T042 Run `./mvnw test-compile org.pitest:pitest-maven:mutationCoverage` for 60% gate, kill survivors per `specs/001-discord-music-bot/research.md` hints (1 survivor = 1 focused test), review `target/pit-reports/index.html`
- [ ] T043 [P] Run `specs/001-discord-music-bot/quickstart.md` validation scenarios 1–11 end-to-end against test guild. **Bloqueado neste ambiente**: exige token real e guild de teste; cenário 11 (extensibilidade) foi validado automaticamente pelo fluxo `$help` + testes. Demais cenários são manuais.
- [X] T044 [P] Update `README.md` (setup, `DISCORD_TOKEN`, `./mvnw verify`, voice/DAVE prerequisites, PT-BR command list)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. NOTE: T002–T005 all edit `pom.xml` → run SEQUENTIALLY (no [P] among them)
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Stories (Phases 3–6)**: All depend on Foundational completion
  - Then proceed in parallel (if staffed) or sequentially P1 → P2 → P3
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: After Foundational — no dependencies on other stories (MVP)
- **US2 (P2)**: After Foundational — integrates with US1 queue/player but independently testable (T030 edits same file as T023)
- **US3 (P2)**: After Foundational — reads US1/US2 queue state, independently testable
- **US4 (P3)**: After Foundational — fully independent (no voice/queue)

### Within Each User Story

- Tests FIRST (must FAIL) → domain/result → adapters → services → wiring
- T024 sequential after T021+T022; T025 after T024; T029→T030→T032; T035→T036; T038→T039
- Story complete before moving to next priority (sequential) or parallel by developer

### Parallel Opportunities

- Setup: T006 parallel with nothing pending (single-file, independent) — T002–T005 sequential (same `pom.xml`)
- Foundational: T009, T011 ([P] with T010), T013, T016, T017 parallel-safe (different files, deps already complete)
- US1: T018+T019+T020 together; T021+T022+T023 together
- US2: T026+T027+T028 together; T031 parallel with T029–T030 chain
- US3: T033+T034 together
- US4: single test, then sequential impl
- Polish: T043+T044 together after T040–T042

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests together (different files, TDD first):
Task: "Unit test play/playlist in src/test/java/com/mutrabot/domain/GuildQueuePlayTest.java" (T018)
Task: "Unit test PlayCommandService in src/test/java/com/mutrabot/application/PlayCommandServiceTest.java" (T019)
Task: "Contract test /play in src/test/java/com/mutrabot/contract/PlayCommandContractTest.java" (T020)

# Launch US1 adapters + result together (different files, ports already complete):
Task: "Create ResolverResult in src/main/java/com/mutrabot/domain/result/ResolverResult.java" (T021)
Task: "Implement LavaplayerResolverAdapter in src/main/java/com/mutrabot/adapter/out/audio/LavaplayerResolverAdapter.java" (T022)
Task: "Implement playback adapter in src/main/java/com/mutrabot/adapter/out/audio/LavaplayerPlaybackAdapter.java" (T023)
```

## Parallel Example: Foundational

```bash
Task: "Create BotCommand sealed in src/main/java/com/mutrabot/domain/command/BotCommand.java" (T009)
Task: "Create outbound ports in src/main/java/com/mutrabot/application/port/out/" (T011)
Task: "Create VT ExecutorConfig in src/main/java/com/mutrabot/bootstrap/ExecutorConfig.java" (T013)
Task: "Create ArchitectureTest in src/test/java/com/mutrabot/architecture/ArchitectureTest.java" (T016)
Task: "Create BotMessages catalog in src/main/java/com/mutrabot/adapter/in/discord/BotMessages.java" (T017)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T006)
2. Complete Phase 2: Foundational (T007–T017, CRITICAL)
3. Complete Phase 3: US1 `/play` (T018–T025)
4. **STOP and VALIDATE**: quickstart scenarios 1–3 + 8–9; `./mvnw verify` green
5. Deploy/demo if ready (music plays from all sources)

### Incremental Delivery

1. Setup + Foundational → foundation ready (`ArchitectureTest` green)
2. + US1 → test independently → Deploy/Demo (MVP!)
3. + US2 → control + idle timer → Deploy/Demo
4. + US3 → transparent queue → Deploy/Demo
5. + US4 → health check → Deploy/Demo
6. Polish → JaCoCo 90% + PITest 60% + survivor sweep → release

### Parallel Team Strategy

1. Team completes Setup + Foundational together (watch `pom.xml` conflicts T002–T005, and T023/T030 same-file sequence)
2. Once Foundational done:
   - Developer A: US1 (T018–T025)
   - Developer B: US2 (T026–T032, after T023 exists for T030)
   - Developer C: US3 + US4 (T033–T039)
3. Stories integrate via shared `GuildQueue`/`MusicQueueRepository` without breaking independence

---

## Notes

- [P] = different files, no dependencies on incomplete tasks (`pom.xml` T002–T005 and `LavaplayerPlaybackAdapter.java` T023/T030 are the two same-file sequences — never parallel)
- [Story] label maps every story-phase task to US1–US4 for traceability
- Each story independently completable/testable per its Checkpoint
- Tests written first and verified FAIL before implementation (TDD per quality gates)
- `stop` = pause only (Q1=A), open permissions (Q2=A), 5-min idle disconnect (Q3=B)
- Commit after each task or logical group; stop at any checkpoint to validate
- Avoid: vague tasks, same-file parallel edits, cross-story deps that break independence

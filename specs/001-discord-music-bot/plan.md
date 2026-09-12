# Implementation Plan: Discord Music Bot

**Branch**: `001-discord-music-bot` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-discord-music-bot/spec.md` + restrições do usuário: "aplicação java com jda, utilize virtual threads e as features recentes do java onde possível como records, utilize arquitetura hexagonal, coverage unitária/branch 90% e testes de mutação com pitest com 60% de coverage, verifique os mutantes sobreviventes em busca de hints" + updates: "java 25", "troque gradle por maven"

## Summary

Bot Discord MVP em Java 25 + JDA 6 com 6 slash commands (`play`, `stop`, `resume`, `skip`, `queue`, `ping`), playback multi-fonte (YouTube via `youtube-source`, SoundCloud/HTTP via LavaPlayer, Spotify/Tidal via metadados + fallback de busca) e playlists. Domínio puro (fila por guild, `Track` record, `BotCommand`/`ResolverResult` sealed) isolado de adapters via arquitetura hexagonal; borda JDA despacha interações em Virtual Threads; `stop` apenas pausa (Q1=A), permissões abertas (Q2=A), desconexão após 5min ocioso (Q3=B). Qualidade: JaCoCo 90% linha/branch como gate de PR + PITest 60% mutação como gate de `main`/nightly, com leitura de sobreviventes como hints. Detalhes em [research.md](research.md), [data-model.md](data-model.md), [contracts/commands.md](contracts/commands.md), [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**: Java 25 LTS (Maven `maven-compiler-plugin` `release=25`; Virtual Threads + records/sealed/pattern-matching estáveis; FFM API estável p/ voz DAVE via JDAVE; Structured Concurrency/Scoped Values disponíveis como evolução)

**Primary Dependencies**: `net.dv8tion:JDA:6.x` + `JDAVE` (`DaveSessionFactory` via FFM, exige Java 25) p/ voz E2EE DAVE, LavaPlayer fork `dev.arbjerg:lavaplayer:2.2.x` embarcado, `dev.lavalink.youtube:youtube-source:1.18.x`, JUnit 5 + Mockito + AssertJ + ArchUnit; build `Maven (pom.xml único + maven-wrapper, versão com suporte a release 25)`

**Storage**: N/A (fila em memória por guild: `ConcurrentHashMap<GuildId, GuildQueue>`; sem DB no MVP)

**Testing**: JUnit 5 (`./mvnw test` via Surefire), AssertJ/Mockito, ArchUnit (regras hexagonais), JaCoCo `0.8.13+` (versão com suporte a bytecode 25; `jacoco:check`: `LINE≥0.90 + BRANCH≥0.90` no `verify`), PITest `1.19.x+` (versão com suporte a Java 25, `pitest-maven` + `pitest-junit5-plugin`; `mutators=DEFAULTS`, `mutationThreshold=60` bloqueante em `main`/nightly via `mutationCoverage`, sem binding no lifecycle padrão — diff-only informativo no PR)

**Target Platform**: JVM 25+ (Linux/Windows server ou container Docker), processo único (sem Lavalink externo no MVP)

**Project Type**: Discord bot / aplicação JVM (serviço com arquitetura hexagonal, sem Spring no MVP — wiring manual)

**Performance Goals**: ack de `/play` via `deferReply()` <3s (limite Discord); início do áudio em poucos segundos (SC-001 ≤1min); `/ping→pong` em poucos segundos; timer de ociosidade 5min (FR-015)

**Constraints**: voz exige DAVE via JDAVE em Java 25 (`DaveSessionFactory` FFM, senão loop reconnect); slash global propaga em até ~1h (usar guild commands em dev); YouTube instável (cipher/IPv4/idade) → erro amigável sem bypass DRM; respostas PT-BR; fila isolada por guild; playlist truncada em 100 faixas; `AudioSendHandler`/threads gateway-áudio nunca bloqueadas; `domain` só `java.base` (ArchUnit); token via env, nunca commitado

**Scale/Scope**: 1 guild no MVP (multiguild por isolamento pronto); 6 comandos; playlist até 100/pedido; `queue` verificada até 20 faixas com paginação além

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- `.specify/memory/constitution.md` é template placeholder (sem princípios ratificados) → nenhum gate aplicável; **PASS vazio** antes da Phase 0.
- Re-check pós-Phase 1: o design respeita o espírito de qualidade/test-first implícito (hexagonal testável, JaCoCo 90% + PITest 60% como gates automatizados, ArchUnit impondo dependências) e não introduz violação que exija justificativa em Complexity Tracking.
- Decisões impostas pelo usuário (JDA, hexagonal, Command pattern, VT, records, 90%/60%) tratadas como requisitos, documentadas em [research.md](research.md).

## Project Structure

### Documentation (this feature)

```text
specs/001-discord-music-bot/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── commands.md      # slash schemas + respostas PT-BR + ports
├── checklists/
│   └── requirements.md  # spec quality gate (16/16)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Greenfield Maven, módulo único, pacotes hexagonais:

```text
pom.xml
.mvn/wrapper/
src/
├── main/java/com/mutrabot/
│   ├── domain/
│   │   ├── model/        # Track.java, GuildQueue.java, SourceKind.java, Requester.java, PlaybackState.java
│   │   ├── command/      # BotCommand.java (sealed + records PlayCmd/StopCmd/ResumeCmd/SkipCmd/QueueCmd/PingCmd)
│   │   └── result/       # ResolverResult.java (sealed ResolvedTrack/ResolvedPlaylist/NotFound/LoadFailed)
│   ├── application/
│   │   ├── port/in/      # PlayUseCase, StopUseCase, ResumeUseCase, SkipUseCase, ListQueueUseCase, PingUseCase
│   │   ├── port/out/     # TrackResolverPort, AudioPlaybackPort, MusicQueueRepository, InteractionResponderPort
│   │   └── service/      # PlayCommandService etc. (switch exaustivo, sem default)
│   ├── adapter/
│   │   ├── in/discord/   # JdaCommandListener, CommandMapper, JdaResponderAdapter
│   │   └── out/
│   │       ├── audio/    # LavaplayerPlaybackAdapter, LavaplayerResolverAdapter, AudioPlayerSendHandler, TrackMapper
│   │       └── persistence/ # InMemoryQueueAdapter (ConcurrentHashMap + ReentrantLock)
│   └── bootstrap/        # BotApplication, JdaConfig, ExecutorConfig (VT singleton), AudioConfig
└── test/java/com/mutrabot/
    ├── domain/           # GuildQueueTest (transições, borda limite/playlist)
    ├── application/      # service tests (NotFound/LoadFailed, pausa/resume/skip/idle)
    └── architecture/     # ArchitectureTest (regras hexagonais ArchUnit)

target/site/jacoco/             # gate 90% (PR, via ./mvnw verify)
target/pit-reports/             # gate 60% (main/nightly) + hints de sobreviventes
```

**Structure Decision**: módulo Maven único (overhead de multi-módulo injustificado p/ MVP de 6 comandos); separação hexagonal por pacotes + ArchUnit em vez de módulos físicos; wiring manual em `bootstrap` (sem Spring). Java 25 habilita JDAVE/FFM + evoluções (StructuredTaskScope/Scoped Values) como melhoria posterior p/ fan-out de playlist; evolução: extrair Lavalink remoto como novo `adapter/out` sem tocar domínio.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — (nenhuma; constitution é placeholder sem gates) | — | — |

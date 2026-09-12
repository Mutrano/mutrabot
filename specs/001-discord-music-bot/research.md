# Research: Discord Music Bot (001-discord-music-bot)

**Data**: 2026-09-12
**Spec**: [spec.md](spec.md)
**Input extra do usuário (plan)**: "aplicação java com jda, utilize virtual threads e as features recentes do java onde possível como records, utilize arquitetura hexagonal, coverage unitária/branch 90% e testes de mutação com pitest com 60% de coverage, verifique os mutantes sobreviventes em busca de hints" + updates: "java 25" (baseline migrado de 21 → 25 LTS), "troque gradle por maven" (build migrado de Gradle Kotlin DSL → Maven)

Todos os `NEEDS CLARIFICATION` do Technical Context foram resolvidos abaixo. Nenhum permanece aberto.

## 1. Stack Java + JDA + áudio multi-fonte

- **Decision**: Java 25 LTS (Maven `maven-compiler-plugin`, `release=25`) + JDA 6.x + JDAVE (`DaveSessionFactory` via FFM estável) + LavaPlayer fork `dev.arbjerg:lavaplayer:2.2.x` embarcado + `dev.lavalink.youtube:youtube-source` para YouTube. SoundCloud/HTTP via LavaPlayer nativo. Spotify/Tidal via resolução de metadados → fallback de busca (ex.: `ytsearch:`), sem promessa de stream direto DRM.
- **Rationale**: JDA sozinho não faz playback; LavaPlayer embarcado entrega `AudioPlayer → queue → TrackScheduler → AudioPlayerSendHandler` pronto sem operar servidor externo (ideal MVP single-process). O suporte built-in a YouTube do LavaPlayer clássico foi removido/deprecated por quebras de cipher; o fix oficial é externalizar para `youtube-source` (client InnerTube atualizável). Spotify/Tidal não oferecem stream direto confiável sem DRM/login — padrão da comunidade é resolver ISRC/artista-título e tocar melhor correspondência.
- **Alternatives considered**:
  - Lavalink server + `LavaSrc`/`youtube-plugin` (melhor p/ produção multi-node/sharding, isola quebras do YouTube, dá Spotify/Apple/Deezer/Tidal/search nativos) — rejeitado p/ MVP por custo operacional (+1 JVM, `application.yml`, client `Lavalink4J`).
  - `yt-dlp + FFmpeg` manual — robusto p/ YouTube mas reimplementa Opus/`AudioSendHandler`, sem fila pronta; manter apenas como fallback futuro.
  - `com.sedmelluq:lavaplayer` original — abandonado; usar fork `lavalink-devs/lavaplayer`.
- **Armadilhas**: Discord exige voz E2EE DAVE desde 03/2026 — JDA 5.x sem `DaveSessionFactory` entra em loop reconnect. Em Java 25 usar JDAVE (FFM estável; `libdave-jvm` era o fallback p/ 17/21). YouTube pode falhar com `Video returned ... isn't what was requested` (bloqueio IPv4) ou age-restricted/login — MVP responde erro amigável, não tenta bypass. Atualizar `youtube-source` agressivamente. 1 `AudioPlayer`/`SendHandler` por guild (`Map<GuildId, Manager>`), nunca compartilhar. Nunca bloquear thread de evento JDA nem `provide20MsAudio()` com `loadItem`/IO. Build exige Maven com suporte a `release=25` (Maven 3.9+/4 + `maven-compiler-plugin` atual).

## 2. Slash commands + padrão Command extensível

- **Decision**: Slash commands registrados via `jda.updateCommands()` (produção) / `guild.updateCommands()` (dev, instantâneo). Cada comando de domínio é um `record` dentro de `sealed interface BotCommand`; adapter JDA (`JdaCommandListener` + `CommandMapper`) converte `SlashCommandInteractionEvent → BotCommand` e despacha ao `application service`. Novo comando = novo `record` + `UseCase` + registro no mapper/registry, sem tocar núcleo.
- **Rationale**: Atende FR-012 (adicionar 7º comando sem modificar núcleo) e SC-005. `sealed + switch` pattern-matching exaustivo sem `default` faz o compilador quebrar todos os `switch` ao adicionar variante — detector de impacto gratuito.
- **Alternatives considered**: framework `Kaktushose/jda-commands` (bom p/ 20+ comandos, overkill p/ 6); prefixo `!` legado (rejeitado; slash é padrão moderno, prefixo só se compatibilidade exigir).
- **Regra de interação**: `/play` sempre `deferReply().queue()` primeiro (ack <3s limite Discord), depois processamento assíncrono + `hook.sendMessage().queue()`.

## 3. Virtual Threads + Java 25 moderno (records, sealed, pattern matching)

- **Decision**: Java 25 baseline. Um único `ExecutorService` de Virtual Threads compartilhado (`Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("cmd-",0).factory())`), usado **apenas** na borda de entrada JDA para despachar interações para os `application services`. Nunca substituir `gatewayPool`, `audioPool`, `rateLimitScheduler` (são `ScheduledExecutorService`, sensíveis a jitter) nem o loop `AudioSendHandler`/LavaPlayer. Domínio usa `record` (ex.: `Track`), `sealed interface + permits` (ex.: `BotCommand`, `ResolverResult`), `switch` exaustivo, `ReentrantLock`/`ConcurrentHashMap`/`ConcurrentLinkedQueue`. Structured Concurrency / Scoped Values (estáveis/maduros no 25) reservados como evolução p/ fan-out de playlist, não base do MVP.
- **Rationale**: Comandos são I/O-bound curta duração — VT permite estilo bloqueante (`future.get()`) sem esgotar pool de plataforma. JDA gateway heartbeat e `provide20MsAudio()` exigem latência previsível; VT neles causa `Missed heartbeats` / áudio gaguejando. Em Java 25 o pinning de `synchronized` + I/O em VT está mitigado frente ao 21, mas a regra (preferir `ReentrantLock`, diagnosticar com `-Djdk.tracePinnedThreads=short` / JFR `jdk.VirtualThreadPinned`) é mantida por previsibilidade.
- **Alternatives considered**: só callbacks `queue()` encadeados (válido p/ bot pequeno, mas aninha callbacks); `setEventPool(fixedPlatformPool)` (exige dimensionar, estoura sob pico HTTP); StructuredTaskScope já na base (adiado como evolução p/ não inflar o MVP).
- **Bridge LavaPlayer**: singleton `AudioPlayerManager`, `loadItemOrdered` + callback→`CompletableFuture`, bloqueio só dentro da VT. `AudioPlayerSendHandler.canProvide/provide20MsAudio` só faz `poll()` de fila pré-preenchida, nunca I/O.

## 4. Arquitetura hexagonal (ports & adapters)

- **Decision**: Módulo Maven único (`pom.xml`) com pacotes `domain` / `application/port/in|out + service` / `adapter/in/discord + out/audio + out/persistence` / `bootstrap` (wiring manual, sem Spring no MVP). Dependências só para dentro; mapeamento na borda (`CommandMapper`, `TrackMapper AudioTrackInfo→Track`). Regras impostas com ArchUnit nos testes.
- **Rationale**: Isola regras testáveis (ordenação, limite por guild, `Playlist→N Tracks`, `NotFound/LoadFailed(retryable)`) de JDA/LavaPlayer. Trocar LavaPlayer embarcado por Lavalink remoto = só novo `adapter/out` implementando `TrackResolverPort`/`AudioPlaybackPort`.
- **Alternatives considered**: Gradle Kotlin DSL (`build.gradle.kts` + `libs.versions.toml`) — equivalente; escolher Maven por decisão do usuário (config declarativa `jacoco-maven-plugin`/`pitest-maven`, `mvnw` amplamente disponível em CI). Gradle multi-módulo/maven multi-módulo (`model`, `application`, `adapter`, `bootstrap`) — overhead injustificado p/ MVP de 6 comandos. Spring Boot — rejeitado p/ MVP (overhead, startup, DI desnecessária; wiring manual basta).
- **Regras (ArchUnit)**:
  1. `domain` importa só `java.base` — sem `net.dv8tion.*`, lavaplayer, Spring/JPA/Jackson.
  2. `application` importa só `domain` — nunca `adapter`/`bootstrap`.
  3. `adapter/in` importa `application.port.in + domain` — nunca `adapter/out`.
  4. `adapter/out` importa `application.port.out + domain` — nunca tipos JDA em assinatura de port.
  5. `bootstrap` é o único que conhece concretos; injeção via construtor, sem lógica.

## 5. Qualidade: JaCoCo 90% linha/branch + PITest 60% mutação

- **Decision**: JaCoCo `0.8.13+` (versão com suporte a bytecode Java 25) gate duro no PR (`jacoco-maven-plugin:check`, `LINE≥0.90 + BRANCH≥0.90` no `verify`). PITest `1.19.x+` (`pitest-maven` + `pitest-junit5-plugin`, versão com suporte a Java 25), `mutators=DEFAULTS`, `mutationThreshold=60`: informativo/diff-only no PR, bloqueante no `main`/nightly (`./mvnw test-compile org.pitest:pitest-maven:mutationCoverage`, sem binding no lifecycle padrão). `threads=CPUs`, `withHistory=true`, `timestampedReports=false`, `targetClasses` restrito ao pacote próprio, excluir config/DTO/generated.
- **Rationale**: JaCoCo é rápido/determinístico e pega código sem teste, mas `LINE` sozinho é burlável com teste sem `assert` — por isso exigir `BRANCH` junto. PITest mede qualidade do assert, mas custa 5–20x o tempo do `test` (cada mutante roda subset de testes); full no PR trava o fluxo. `DEFAULTS` cobre ~80% dos bugs reais com poucos equivalentes; `ALL` gera mutantes experimentais/lentos. Filosofia oficial PIT: `run frequently against only changed code`.
- **Alternatives considered**: só JaCoCo sem PIT (rápido mas falso-positivo com `assertTrue(true)`); PIT `STRONGER/ALL + 85%` no PR (ideal teórico, PR de ~1h, muitos equivalentes); Sonar como único gate (depende de servidor externo).
- **Workflow mutantes sobreviventes (hints)**: abrir `target/pit-reports/index.html` (rosa = `SURVIVED`/`NO_COVERAGE`); regra `1 sobrevivente = 1 teste faltante`:
  - `CONDITIONALS_BOUNDARY` (`>→>=`) → falta teste de borda (`limit` vs `limit+1` com `assertEquals` exato).
  - `NEGATE_CONDITIONALS` (`==→!=`) → branch `else` não verificado → testar os dois lados.
  - `TRUE/FALSE/NULL/PRIMITIVE_RETURNS` → assert fraco → trocar `assertTrue(x>0)` por `assertEquals` exato + edges (`null`, `0`, exceção).
  - `VOID_METHOD_CALLS` (removeu `save/notify`) → falta `verify(mock)` / `verifyNoInteractions`.
  - `MATH` (`+→-`) → assert inexato → `assertEquals` exato + negativos/zero.
  - `NO_COVERAGE` em massa → código morto/config/DTO → excluir do gate (`excludes`, `avoidCallsTo`), não inflar teste inútil.
  - Equivalente legítimo (`log.debug`, `toString`, Lombok `@Generated`) → excluir, não testar.
- **Mitigações tempo**: `threads=max`, cachear histórico PIT no CI, `timeoutFactor=1.25`, `jvmArgs=-Xmx2g`, isolar PIT primeiro em `domain`/`application service` puro (integração lenta explode tempo). Nunca bindar `mutationCoverage` ao `test/verify` (PIT remove o agent do JaCoCo e conflita com `argLine` do Surefire).

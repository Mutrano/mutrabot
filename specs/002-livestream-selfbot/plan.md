# Implementation Plan: Livestream por self-bot

**Branch**: `002-livestream-selfbot` | **Date**: 2026-09-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-livestream-selfbot/spec.md` + decisões: janela = título digitado (`desktop` = tela inteira); Y entra no canal de X; o bot Java gerencia o sidecar; self-bot é pré-requisito imposto pelo Discord (vídeo de bot é bloqueado).

## Summary

Dois slash commands (`/livestream-join <janela>` e `/livestream-leave`) restritos ao dono (X) que orquestram uma **conta de usuário Y** (self-bot) para fazer Go Live de uma janela do computador. O bot JDA permanece hexagonal: um `LivestreamControlPort` em `application` é implementado por `HttpLivestreamSidecarAdapter` (JDK HttpClient, sem dependência Maven nova), que fala HTTP local autenticado com um sidecar Node (`streamer/`) baseado em `@dank074/discord-video-stream` v6. O sidecar captura com `ffmpeg -f gdigrab -i title=<janela>` (ou `desktop`), faz login de Y, entra no canal de X e chama `playStream(..., {type:"go-live"})`; `/livestream-leave` aborta o stream e chama `leaveVoice()`. Autorização (`OwnerPolicy`) é regra de negócio na camada de aplicação. Detalhes em [research.md](research.md), [data-model.md](data-model.md), [contracts/commands.md](contracts/commands.md), [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**: Java 25 (inalterado) no bot; **Node.js 20+** (sidecar) + TypeScript/ESM.

**Primary Dependencies** (bot): nenhuma nova — `java.net.http.HttpClient` (JDK) e `ProcessBuilder`. **(sidecar)**: `@dank074/discord-video-stream@6.x`, peer `discord.js-selfbot-v13`, nativos `node-av`/`node-datachannel`; FFmpeg de sistema com `gdigrab`.

**Storage**: N/A. Estado da live no sidecar (memória); seleção de janela por título.

**Testing**: JUnit 5 + Mockito + AssertJ + ArchUnit (inalterado). Serviços e `OwnerPolicy` sob gates JaCoCo 90% linha/branch; adapter HTTP e `LivestreamSidecarProcess` excluídos do JaCoCo (integração com processo/serviço externo). Fluxo HTTP validável com `com.sun.net.httpserver.HttpServer` stub nos testes.

**Target Platform**: Windows (captura `gdigrab`). JVM 25+ no bot; Node no sidecar. Linux/macOS como evolução.

**Project Type**: bot JVM hexagonal existente + um sidecar Node no mesmo repositório (`streamer/`).

**Performance Goals**: confirmação de `/livestream-join` em ~15s (login + join + primeiro frame); `/livestream-leave` < 5s (abort + leave). `/livestream-join` ack via `deferReply()` < 3s (limite Discord).

**Constraints**: apenas X pode usar (FR-006); Y nunca é o bot; token de Y só em `.env`/ambiente, passado por env ao processo filho (nunca em `argv`/logs); HTTP local só em `127.0.0.1` + Bearer; falha do sidecar não derruba o bot/música; vídeo sem áudio; uma live por vez.

**Scale/Scope**: 1 guild / 1 sidecar / 1 live por vez (MVP), 2 comandos, 1 janela por vez.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Domínio puro e dependências unidirecionais** — PASSA. Novos variants de `BotCommand` são records sem deps externas; `application` só conhece `domain` e portas; `adapter/out/streaming` usa apenas `java..`; `bootstrap` é o único que conhece o processo concreto. `ArchitectureTest` permanece verde.
- **II. Comandos e portas explícitos** — PASSA. Cada comando é um novo variant selado + nova porta de entrada + serviço + registro; os `switch` exaustivos sem `default` apontam os pontos de impacto. Nenhuma lógica de comando existente é alterada (apenas adição de casos/registro).
- **III. Qualidade verificável por build** — PASSA. Serviços e `OwnerPolicy` cobertos por testes; `HttpLivestreamSidecarAdapter` e `LivestreamSidecarProcess` entram nas exclusões JaCoCo por serem integração com processo externo (permitido). PITest segue em `domain`/`application`.
- **IV. Segredos fora do repositório** — PASSA. `LIVESTREAM_USER_TOKEN` e `LIVESTREAM_SIDECAR_SECRET` apenas em `.env`/ambiente; passados por env ao filho; nunca logados nem em mensagens. `.env` já ignorado; gitleaks no pre-push.
- **V. Java moderno e fronteiras de thread** — PASSA. Records/sealed/pattern matching; a chamada HTTP local roda na VT do `commandExecutor` (nunca em thread de gateway/áudio do JDA nem no loop do LavaPlayer).
- **VI. PT-BR, erros amigáveis e fila resiliente** — PASSA. Todas as respostas em PT-BR e efêmeras; falha de live nunca toca a fila/reprodução (FR-007).

Nenhuma violação exige Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-livestream-selfbot/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── commands.md      # slash schemas + contrato HTTP do sidecar + ports
├── checklists/
│   └── requirements.md  # spec quality gate
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
src/main/java/com/mutrabot/
├── domain/command/BotCommand.java                      # + LivestreamJoinCmd / LivestreamLeaveCmd
├── application/
│   ├── port/in/LivestreamJoinUseCase.java              # NOVO
│   ├── port/in/LivestreamLeaveUseCase.java             # NOVO
│   ├── port/out/LivestreamControlPort.java             # NOVO (Result selado Ok/Unavailable/Failed)
│   └── service/
│       ├── LivestreamJoinService.java                  # NOVO
│       ├── LivestreamLeaveService.java                 # NOVO
│       ├── OwnerPolicy.java                            # NOVO
│       └── BotMessages.java                            # + mensagens PT-BR da live
├── adapter/
│   ├── in/discord/CommandMapper.java                   # + 2 casos
│   ├── in/discord/JdaCommandListener.java              # + 2 use cases, dispatch, deferReply no join
│   └── out/streaming/HttpLivestreamSidecarAdapter.java # NOVO (java.net.http)
└── bootstrap/
    ├── BotApplication.java                             # wiring + registerCommands
    ├── LivestreamSidecarProcess.java                   # NOVO (spawn/env/shutdown)
    └── DotEnv.java                                     # sem mudança (reuso)

streamer/                                                # NOVO sidecar Node
├── package.json                                         # @dank074/discord-video-stream, discord.js-selfbot-v13
├── index.mjs                                            # HTTP server + Streamer + prepareStream/playStream + gdigrab
└── README.md

src/test/java/com/mutrabot/
├── application/LivestreamJoinServiceTest.java          # NOVO
├── application/LivestreamLeaveServiceTest.java         # NOVO
├── application/OwnerPolicyTest.java                    # NOVO
├── adapter/in/discord/CommandMapperTest.java           # + casos livestream
└── architecture/ArchitectureTest.java                  # inalterado (regras já cobrem)

pom.xml                                                 # + exclusões JaCoCo: adapter/out/streaming/**, bootstrap/LivestreamSidecarProcess*
.env.example / README.md                                # + variáveis e pré-requisitos (Node, FFmpeg, burner Y)
```

**Structure Decision**: mantém o módulo Maven único e a separação hexagonal por pacotes; o sidecar Node fica em `streamer/` como processo externo (não entra no build Maven). A fronteira bot↔sidecar é um port de saída, então trocar o sidecar por outra implementação (ou outro provedor de vídeo) não toca domínio/serviços.

## Complexity Tracking

> Sem violações da constituição.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — (nenhuma) | — | — |

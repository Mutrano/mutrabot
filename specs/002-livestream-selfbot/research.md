# Research: Livestream por self-bot (002-livestream-selfbot)

**Data**: 2026-09-12
**Spec**: [spec.md](spec.md)
**Input extra do usuário**: "isso deve viver em um comando /livestream-join que recebe a janela ... /livestream-leave para que o usuário Y saia da sala, apenas usuário X(eu) pode utilizar" + decisões de clarificação: janela = só título digitado (`desktop` = tela inteira), Y entra no canal de X, o bot Java gerencia o sidecar, planejamento via speckit.

Todos os `NEEDS CLARIFICATION` foram resolvidos abaixo. Nenhum permanece aberto.

## 1. Por que self-bot e não bot

- **Decision**: a transmissão usa uma **conta de usuário Y** (self-bot) via `@dank074/discord-video-stream` + `discord.js-selfbot-v13`. O bot JDA nunca transmite vídeo.
- **Rationale**: o FAQ oficial da lib responde textualmente *"Does this library work with bot tokens? No, Discord blocks video from bots which is why this library uses a selfbot library as peer dependency. You must use a user token"*. JDA/LavaPlayer só fazem áudio; o protocolo de vídeo/Go Live não é exposto à API de bots.
- **Alternatives considered**:
  - Implementar o protocolo de voz/RTP de vídeo direto em Java (JDA não expõe; reimplementação enorme) — rejeitado.
  - Usar a conta do próprio bot para stream — bloqueado pelo Discord — inviável.
  - Provedor externo de screen share (não-Discord) — muda a experiência; rejeitado.
- **Risco**: self-bot viola o ToS e pode banir a conta. Mitigação: **Y é burner**; nunca usar a conta principal; aviso no README e nas mensagens internas.

## 2. API e ciclo de vida no sidecar Node (verificado no fonte da lib)

Fonte: `Discord-RE/Discord-video-stream`, branch `master`, v6.0.0.

- **Decision (sessão)**:
  ```ts
  const streamer = new Streamer(new Client());          // discord.js-selfbot-v13
  await streamer.client.login(process.env.LIVESTREAM_USER_TOKEN);
  await streamer.joinVoice(guildId, channelId);          // entra no canal de X
  ```
  `Streamer.joinVoice(guild_id, channel_id)` cria a `VoiceConnection` com um `WebRtcConnWrapper`.

- **Decision (captura + encode)**:
  ```ts
  const { output } = prepareStream(windowInput, {
    customInputOptions: ["-f", "gdigrab", "-framerate", "30"],
    includeAudio: false,
    width: -2, height: 720, videoCodec: Utils.normalizeVideoCodec("H264"),
  });
  await playStream(output, streamer, { type: "go-live" });
  ```
  No fonte (`src/media/newApi.ts`), `prepareStream` chama `command.input(input)` e insere os `customInputOptions` **antes** do `-i`, então `-f gdigrab -framerate 30 -i title=<janela>` é exatamente o resultado. `windowInput` é `title=<título>` ou `desktop`.

- **Decision (`includeAudio: false`)**: desliga `-map 0:a` e o filtro `azmq`/zeromq. Com isso **não precisamos do FFmpeg com `libzmq`** (a exigência do README só vale com áudio ligado) e não capturamos áudio do sistema (fora do escopo do MVP).

- **Decision (parar)**: `playStream(...)` retorna `{ promise, controller }` e aceita um `AbortSignal`; abortar dispara o cleanup interno (`stopStream()` + `setSpeaking(false)`). O encerramento completo é:
  ```ts
  abortController.abort();
  streamer.leaveVoice();   // streamer.stopStream() é chamado no cleanup do abort
  ```
  `Streamer` expõe `stopStream()`, `leaveVoice()`, `signalLeaveVoice()`.

- **Alternatives considered**:
  - Passar um `Readable` de um ffmpeg `gdigrab` externo para `prepareStream` (duas camadas de ffmpeg, dobro de CPU) — rejeitado; `customInputOptions` resolve numa passada.
  - `noTranscoding: true` — exigiria stream já compatível (keyframe de 1s, sem B-frames), o que o gdigrab cru não é — rejeitado.

## 3. Captura de janela no Windows (gdigrab)

- **Decision**: `ffmpeg -f gdigrab -framerate 30 -i title="<título>"` para uma janela; `-i desktop` para a tela inteira. O parâmetro `janela` do slash command vira `title=<valor>`; o valor `desktop` (case-insensitive) vira `desktop`.
- **Rationale**: gdigrab é nativo do FFmpeg, sem instalação de extensão; captura janela por título. Fonte: documentação de gdigrab e wiki FFmpeg (ver Referências).
- **Armadilhas**: (a) a correspondência por título pode falhar/ambiguar com janelas de mesmo nome — comportamento exato a confirmar na implementação; (b) janela minimizada/oclusa pode capturar vazio; (c) apenas FFmpeg de Windows tem gdigrab.
- **Alternatives considered**: enumerar janelas por handle (`HWND`) exigiria JNI/PowerShell e um mapeamento por id — rejeitado no MVP (decisão: só título digitado).

## 4. Transporte bot Java ↔ sidecar

- **Decision**: sidecar é um **servidor HTTP local** em `127.0.0.1:<LIVESTREAM_SIDECAR_PORT>` com header `Authorization: Bearer <LIVESTREAM_SIDECAR_SECRET>`. Endpoints:
  - `POST /start` `{ "guildId": "...", "channelId": "...", "window": "..." }`
  - `POST /stop` `{ "guildId": "..." }`
  - `GET /health` (usado no startup)
  O adapter Java (`HttpLivestreamSidecarAdapter`) usa `java.net.http.HttpClient` (JDK, **sem nova dependência Maven**).
- **Rationale**: HTTP local é simples, testável com um `com.sun.net.httpserver.HttpServer` stub nos testes, e mantém o sidecar independente do processo Java. Segredo compartilhado evita que outro processo local controle a conta Y.
- **Alternatives considered**: JSON-lines via stdin/stdout de um `ProcessBuilder` (mais acoplado e frágil a restart); named pipes; sockets Unix (não portável no Windows) — rejeitados.

## 5. Ciclo de vida do sidecar

- **Decision**: `BotApplication` dá spawn no processo Node via `ProcessBuilder`, passa as variáveis por **ambiente** (nunca em `argv`, para não vazar no process listing), espera `/health` e registra o shutdown do processo no `addShutdownHook`. Se o sidecar não subir, o bot continua e os comandos respondem "sidecar indisponível".
- **Rationale**: premissa "o bot Java gerencia o sidecar" (decisão do usuário). Degradação graciosa protege a feature de música (FR-015).
- **Alternatives considered**: subir o sidecar manualmente/serviço separado (mais operacional); subir por comando (latência de login a cada uso) — rejeitados.

## 6. Arquitetura hexagonal (encaixe no repo existente)

- **Decision**: seguir os padrões observados em `001`:
  - `domain/command/BotCommand.java`: adicionar `LivestreamJoinCmd(GuildId, UserId, VoiceChannelId, String window)` e `LivestreamLeaveCmd(GuildId, UserId)`.
  - `application/port/in`: `LivestreamJoinUseCase`, `LivestreamLeaveUseCase`.
  - `application/port/out`: `LivestreamControlPort` (`start(guild, channel, window)` / `stop(guild)` / resultado selado `Ok`/`Unavailable`/`Failed`).
  - `application/service`: `LivestreamJoinService`, `LivestreamLeaveService`, `OwnerPolicy` (autorização é regra de negócio).
  - `adapter/out/streaming`: `HttpLivestreamSidecarAdapter`.
  - `adapter/in/discord`: `CommandMapper` (+2 casos) e `JdaCommandListener` (+2 campos, dispatch, `deferReply()` no join).
  - `bootstrap`: wiring, registro dos slashes e `LivestreamSidecarProcess`.
- **Rationale**: mantém `domain` puro e `application` sem JDA/HTTP; o `switch` selado força o compilador a apontar os pontos de impacto (constituição II). O adapter HTTP só usa `java..`, então o `ArchitectureTest` permanece verde.
- **Qualidade**: os serviços (`OwnerPolicy`, join/leave, mapeamento de resultados → mensagens) ficam sob os gates; `HttpLivestreamSidecarAdapter` e `LivestreamSidecarProcess` entram nas exclusões JaCoCo por serem integração com processo/serviço externo (permitido pela constituição III). O sidecar Node não é coberto por JaCoCo.

## 7. Configuração e segredos

- **Decision**: novas variáveis no `.env` (ignorado pelo Git, já coberto por gitleaks):
  - `LIVESTREAM_OWNER_ID` — id do X.
  - `LIVESTREAM_USER_TOKEN` — token do Y (**segredo**).
  - `LIVESTREAM_SIDECAR_PORT` (default `8790`).
  - `LIVESTREAM_SIDECAR_SECRET` — segredo compartilhado local.
  - `LIVESTREAM_FFMPEG_PATH` (opcional, default `ffmpeg` no PATH).
- **Rationale**: constituição IV — segredos fora do repositório; nunca logar o token.
- **Alternatives considered**: arquivo `config.json` versionado no sidecar (risco de commit de segredo) — rejeitado.

## Referências

- `@dank074/discord-video-stream` (v6.0.0) — README e fonte: `src/media/newApi.ts`, `src/client/Streamer.ts` — https://github.com/Discord-RE/Discord-video-stream
- npm — https://www.npmjs.com/package/@dank074/discord-video-stream
- FFmpeggrab (Windows) — https://ffmpeg.org/ffmpeg-devices.html#gdigrab e https://trac.ffmpeg.org/wiki/Capture/Desktop
- `discord.js-selfbot-v13` (peer dependency) — https://www.npmjs.com/package/discord.js-selfbot-v13

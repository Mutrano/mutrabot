# Contracts: Livestream (slash commands + sidecar)

**Spec**: [../spec.md](../spec.md) | **Modelo**: [../data-model.md](../data-model.md) | **Research**: [../research.md](../research.md)

## Convenções

- Respostas em **PT-BR**, efêmeras (usam `followUpEphemeral`). Autorização: **apenas X** (FR-006).
- `/livestream-join` sempre faz `deferReply()` antes de falar com o sidecar (login + join + ffmpeg podem passar dos 3s de ack do Discord). `/livestream-leave` responde direto.
- Nenhuma falha da feature afeta a música/fila (FR-007).

## Slash Commands

### /livestream-join `janela: STRING (obrigatório)` — FR-001/002/003/009/011

Inicia um Go Live da janela informada (ou tela inteira) na conta Y, no canal de voz em que X está.

| Caso | Resposta (PT-BR) |
|------|------------------|
| Sucesso | `🔴 Live iniciada: transmitindo **{janela}** no canal de voz. Use /livestream-leave para encerrar.` (janela `desktop` → `a tela inteira`) |
| Não é o dono | `🚫 Só o dono pode usar este comando.` |
| X fora de canal de voz | `🔇 Entre em um canal de voz primeiro e tente de novo.` |
| Já havia live ativa | encerra a anterior e responde com o sucesso acima |
| Janela não encontrada | `🪟 Não encontrei uma janela "{janela}". Confira o título ou use \`desktop\` para a tela inteira.` |
| Y fora da guild / token inválido | `⚠️ A conta de transmissão não está disponível. Verifique o convite e o token (LIVESTREAM_USER_TOKEN).` |
| FFmpeg/nativos ausentes | `⚠️ Não consegui iniciar a captura: {motivo}. Confira o FFmpeg e as dependências do sidecar.` |
| Sidecar indisponível | `📡 O serviço de transmissão está fora do ar. A música segue normal; tente de novo em instantes.` |

### /livestream-leave — FR-005/012

| Estado | Resposta |
|--------|----------|
| Havia live → encerrada | `⏹ Live encerrada e a conta saiu do canal de voz.` |
| Nada transmitindo | `ℹ️ Não há nenhuma live ativa no momento.` |
| Não é o dono | `🚫 Só o dono pode usar este comando.` |
| Sidecar indisponível | `📡 O serviço de transmissão está fora do ar. Tente de novo em instantes.` |

## Contrato HTTP bot ↔ sidecar (FR-014)

Bind em `127.0.0.1:<LIVESTREAM_SIDECAR_PORT>`; todas as rotas exigem header `Authorization: Bearer <LIVESTREAM_SIDECAR_SECRET>` (senão `401`).

### `GET /health`
- `200 {"status":"ready"}` quando o sidecar está pronto para receber comandos.
- `503 {"status":"starting"|"error","detail":"..."}` caso contrário.

### `POST /start` — body `{ "guildId": string, "channelId": string, "window": string }`
- `200 {"status":"streaming"}` — Y entrou no canal e o Go Live começou.
- `400 {"code":"...","reason":"<motivo>"}` — janela inexistente (`WINDOW_NOT_FOUND`), voz/login (`TRANSMITTER_UNAVAILABLE`), ffmpeg (`CAPTURE_FAILED`).
- `503 {"code":"...","reason":"..."}` — sidecar não pronto/iniciando (→ `Unavailable`).
- Se já houver live, o sidecar encerra a anterior antes de iniciar (FR-011). Antes de transmitir, o sidecar valida a janela com um probe de 1 frame do ffmpeg.

### `POST /stop` — body `{ "guildId": string }`
- `200 {"status":"idle","wasActive":true|false}` — parado e Y saiu; `wasActive=false` quando já estava idle (idempotente, FR-012).
- `503 {"reason":"..."}` — sidecar não pronto (→ `Unavailable`).

## Mapeamento de resposta → port

```
2xx + wasActive=true|false  -> LivestreamControlPort.Result.Ok(changed)
400/409 + {code,reason}     -> Result.Failed(FailureKind(code), reason)
401                         -> Result.Failed(UNKNOWN, "não autorizado pelo sidecar")
ConnectException/timeout/5xx-> Result.Unavailable(detail)
```

`FailureKind`: `WINDOW_NOT_FOUND`, `TRANSMITTER_UNAVAILABLE`, `CAPTURE_FAILED`, `UNKNOWN`.

## Ports hexagonais correspondentes

```
adapter/in/discord JdaCommandListener --BotCommand.LivestreamJoinCmd/LeaveCmd--> application.port.in
    (LivestreamJoinUseCase, LivestreamLeaveUseCase)
application.service LivestreamJoinService/LivestreamLeaveService --OwnerPolicy--> autorização (X)
application.service --start/stop--> LivestreamControlPort
    (adapter/out/streaming HttpLivestreamSidecarAdapter) --HTTP local--> Sidecar Node (@dank074/discord-video-stream)
application.service --respostas--> InteractionResponderPort (adapter/in/discord JdaResponderAdapter)
bootstrap LivestreamSidecarProcess --spawn/env--> Node sidecar (streamer/)
```

## Registro dos comandos

Adicionados em `BotApplication.registerCommands`, no mesmo lote dos 6 existentes (guild em dev, global em prod):

```java
Commands.slash("livestream-join", "Transmite uma janela do computador (só o dono)")
        .addOptions(new OptionData(OptionType.STRING, "janela",
                "Título da janela ou 'desktop' para a tela inteira", true)),
Commands.slash("livestream-leave", "Encerra a transmissão (só o dono)")
```

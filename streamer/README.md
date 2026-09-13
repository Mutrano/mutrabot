# mutrabot livestream sidecar

Processo Node que faz **Go Live de uma janela** numa conta de usuário (self-bot) a pedido do bot
`mutrabot`. O bot JDA fala com este processo por HTTP local autenticado.

> [!CAUTION]
> Self-bot viola o ToS do Discord e pode banir a conta usada. Use **somente uma conta burner**.
> O Discord bloqueia vídeo vindo de contas de bot, por isso é necessária uma conta de usuário.

## Requisitos

- Node.js 20+ e `npm`.
- FFmpeg no PATH (ou `LIVESTREAM_FFMPEG_PATH`). No Windows a captura usa `gdigrab`.
- Conta Y na guild, com permissões de Conectar/Falar/Transmitir.

## Instalação

```bash
cd streamer
npm install
```

## Variáveis de ambiente

| Variável | Obrigatória | Default | Uso |
|----------|-------------|---------|-----|
| `LIVESTREAM_USER_TOKEN` | sim | — | token da conta Y (segredo) |
| `LIVESTREAM_SIDECAR_SECRET` | sim | — | segredo compartilhado com o bot |
| `LIVESTREAM_SIDECAR_PORT` | não | `8790` | porta local |
| `LIVESTREAM_FFMPEG_PATH` | não | `ffmpeg` | binário do FFmpeg |

Normalmente o bot sobe este sidecar sozinho (`bootstrap.LivestreamSidecarProcess`). Para rodar à mão:

```bash
LIVESTREAM_USER_TOKEN=... LIVESTREAM_SIDECAR_SECRET=... npm start
```

## Endpoints

- `GET /health` → `200 {"status":"ready"}` quando a conta Y está conectada.
- `POST /start` `{guildId, channelId, window}` → inicia o Go Live (`window` = título da janela ou `desktop`).
- `POST /stop` `{guildId}` → encerra o Go Live e sai do canal (idempotente).

Todas as rotas exigem `Authorization: Bearer <LIVESTREAM_SIDECAR_SECRET>`.

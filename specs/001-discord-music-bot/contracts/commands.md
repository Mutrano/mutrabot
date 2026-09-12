# Contracts: Slash Commands (Discord)

**Spec**: [../spec.md](../spec.md) | **Modelo**: [../data-model.md](../data-model.md)
**Registro**: `jda.updateCommands()` (prod, propagação até ~1h) / `guild.updateCommands()` (dev, instantâneo). Ver [research.md](../research.md).

## Convenções

- Idioma das respostas: PT-BR. Permissões: modelo aberto (qualquer membro que pode enviar mensagens usa todos os comandos — FR-014).
- `/play` sempre faz `deferReply()` antes de resolver (ack <3s). Demais comandos respondem direto (rápidos).
- Erros seguem catálogo em [Erro Responses](#erro-responses-fr-016) — nunca derrubam fila/reprodução atual.

## Comandos

### /play `query: STRING (obrigatório)` — FR-002/003/004/005/013

Aceita link de faixa (YouTube/SoundCloud/Spotify/Tidal), link de playlist ou texto de busca.

| Caso | Resposta (PT-BR) |
|------|------------------|
| Fila vazia, começa a tocar | `▶ Tocando agora: **{título}** ({fonte}) — pedido por {solicitante}` |
| Já tocando, enfileirado | `➕ Adicionado à fila (#{posição}): **{título}** — pedido por {solicitante}` |
| Playlist | `➕ Playlist adicionada: **{n}** faixas (ordem original){, {ignoradas} ignoradas pelo limite de 100}` |
| Sem resultados | `🔍 Nada encontrado para "{query}". Tente outro nome ou link.` |
| Link inválido/privado/restrito/fonte fora do ar | `⚠️ Não consegui carregar "{query}": {motivo}. A fila atual foi mantida.` |
| Usuário sem canal de voz e sem sessão alvo | `🔇 Entre em um canal de voz primeiro e tente de novo.` |

### /stop — FR-007 (apenas pausa)

| Estado | Resposta |
|--------|----------|
| Tocando → pausa | `⏸ Pausado: **{título}**. Use /resume para continuar.` |
| Já pausado / nada tocando | `ℹ️ Nada tocando no momento.` |

Nunca limpa fila nem desconecta (decisão Q1=A).

### /resume — FR-008

| Estado | Resposta |
|--------|----------|
| Pausado → retoma | `▶ Continuando: **{título}**.` |
| Tocando já / nada pausado | `ℹ️ Nada pausado no momento.` |

### /skip — FR-009

| Estado | Resposta |
|--------|----------|
| Havia próxima | `⏭ Pulado. ▶ Tocando agora: **{título}** — pedido por {solicitante}` |
| Era a última | `⏭ Pulado. 📭 A fila acabou.` (+ inicia timer 5 min FR-015) |
| Nada tocando | `ℹ️ Nada para pular.` |

### /queue — FR-006/010

```
🎵 Tocando agora: {título} — pedido por {solicitante} [{duração|ao vivo}]
📋 Próximas ({total}):
1. {título} — {solicitante}
2. {título} — {solicitante}
... e mais {x} (paginação)
```

Fila vazia + nada tocando → `📭 A fila está vazia. Use /play para adicionar músicas.`

### /ping — FR-011

Resposta: `🏓 pong` (podendo incluir `gateway: {n}ms`).

## Erro Responses (FR-016)

- Todas as mensagens de erro são PT-BR, não-técnicas, efêmeras quando possível.
- Falha de uma entrada nunca interrompe reprodução atual nem limpa fila (SC-004).
- Perda de voz (expulso/queda): fila mantida; ao retomar, anunciar estado. Ociosidade 5 min com fila vazia → desconecta e informa (FR-015).

## Ports hexagonais correspondentes

```
adapter/in/discord JdaCommandListener --BotCommand--> application.port.in (PlayUseCase, StopUseCase, ResumeUseCase, SkipUseCase, ListQueueUseCase, PingUseCase)
application.service --ResolverResult--> TrackResolverPort (adapter/out/audio LavaplayerResolverAdapter)
application.service --play/pause/resume/stop--> AudioPlaybackPort (adapter/out/audio LavaplayerPlaybackAdapter)
application.service --load/save--> MusicQueueRepository (adapter/out/persistence InMemoryQueueAdapter)
application.service --reply--> InteractionResponderPort (adapter/in/discord JdaResponderAdapter)
```

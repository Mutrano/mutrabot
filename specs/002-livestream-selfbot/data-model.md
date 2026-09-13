# Data Model: Livestream por self-bot

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)
**Estilo**: Java 25 `record` + `sealed interface` (imutável através das threads JDA → VT → HTTP local).

## Entidades

### 1. Comandos (sealed `BotCommand` — extensão da feature 001)

```java
sealed interface BotCommand permits PlayCmd, StopCmd, ResumeCmd, SkipCmd, QueueCmd, PingCmd,
                                      HelpCmd, LivestreamJoinCmd, LivestreamLeaveCmd {
    record LivestreamJoinCmd(GuildId guild, UserId user, VoiceChannelId voiceChannel, String window)
            implements BotCommand {}
    record LivestreamLeaveCmd(GuildId guild, UserId user) implements BotCommand {}
}
```

- **Validação**: `window` não-blank (trim); `desktop` (case-insensitive) é o valor especial de tela inteira. `voiceChannel` pode ser `null` (tratado pelo serviço como pré-condição FR-009).
- **Extensibilidade**: novo comando = novo `record` + `UseCase` + registro no `CommandMapper`/`JdaCommandListener`; os `switch` exaustivos sem `default` quebram a compilação até tratar.

### 2. LivestreamControlPort (sealed result — saída da aplicação)

```java
interface LivestreamControlPort {
    Result start(GuildId guild, VoiceChannelId channel, String window);
    Result stop(GuildId guild);

    enum FailureKind { WINDOW_NOT_FOUND, TRANSMITTER_UNAVAILABLE, CAPTURE_FAILED, UNKNOWN }

    sealed interface Result {
        record Ok(boolean changed) implements Result {}          // changed = havia live ativa (stop)
        record Unavailable(String detail) implements Result {}   // sidecar inacessível/health falhou
        record Failed(FailureKind kind, String detail) implements Result {} // janela, login, voz, ffmpeg...
    }
}
```

- `Unavailable` (transporte) e `Failed` (negócio/execução) mapeiam para mensagens PT-BR distintas (FR-007).
- O adapter HTTP converte `2xx` → `Ok(wasActive)`, `400/409` com `code` → `Failed(kind, reason)`, `401`/`5xx`/timeout/`ConnectException` → `Unavailable`.

### 3. OwnerPolicy (regra de autorização)

```java
final class OwnerPolicy {
    private final UserId ownerId;              // injetado do bootstrap (LIVESTREAM_OWNER_ID)
    boolean isOwner(UserId user);              // user.equals(ownerId)
}
```

- Sem dependência externa; testável isoladamente. Aplicado nos dois serviços antes de qualquer chamada ao port.

### 4. Estado da sessão (dentro do sidecar Node — não no domínio Java)

```ts
type LivestreamState =
  | { status: "idle" }
  | { status: "starting"; guildId: string; channelId: string; window: string }
  | { status: "streaming"; guildId: string; channelId: string; window: string; cancel: AbortController }
  | { status: "stopping"; guildId: string };
```

- **Invariantes**: no máximo uma sessão ativa (MVP single-guild); `streaming` exige `cancel` vivo; `start` com sessão ativa primeiro executa `stop` (FR-011); `stop` em `idle` é no-op idempotente (FR-012).
- **Transições**:
  ```
  idle --start--> starting --join+play ok--> streaming
  starting --falha--> idle (leaveVoice + logout best-effort)
  streaming --stop/abort--> stopping --leaveVoice--> idle
  idle --stop--> idle (no-op)
  ```

### 5. Configuração (`.env`/ambiente)

| Variável | Obrigatória | Default | Uso |
|----------|-------------|---------|-----|
| `LIVESTREAM_OWNER_ID` | sim (para a feature) | — | id do X (autorização FR-006) |
| `LIVESTREAM_USER_TOKEN` | sim | — | token da conta Y (segredo FR-013) |
| `LIVESTREAM_SIDECAR_PORT` | não | `8790` | porta local do sidecar |
| `LIVESTREAM_SIDECAR_SECRET` | sim | — | segredo compartilhado (FR-014) |
| `LIVESTREAM_FFMPEG_PATH` | não | `ffmpeg` | binário do FFmpeg |

- Se `LIVESTREAM_OWNER_ID`/`LIVESTREAM_USER_TOKEN`/`LIVESTREAM_SIDECAR_SECRET` faltarem, a feature fica **desabilitada** com aviso no boot; o bot de música continua normal (FR-015 / SC-004).

## Relacionamentos

```
Usuario X (ownerId) ──autoriza──> LivestreamUseCases
BotCommand.LivestreamJoinCmd ──> LivestreamJoinService ──> LivestreamControlPort ──HTTP──> Sidecar Node
Sidecar Node ──login/join/play──> Conta Y (self-bot) ──Go Live──> Canal de voz de X
LivestreamControlPort.Result ──> BotMessages (PT-BR) ──> InteractionResponderPort
```

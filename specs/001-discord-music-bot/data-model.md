# Data Model: Discord Music Bot

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)
**Estilo**: Java 25 `record` + `sealed interface` (imutável através de threads JDA → VT → áudio).

## Entidades

### 1. Track (record — valor imutável)

```java
record Track(TrackId id, String title, String author, String sourceUrl,
             Duration duration, SourceKind source, Requester requestedBy) {}
record TrackId(String value) {}
enum SourceKind { YOUTUBE, SOUNDCLOUD, SPOTIFY, TIDAL, SEARCH_RESULT, HTTP }
record Requester(UserId userId, String displayName) {}
record UserId(String value) {}
```

- **Origem**: `source` preserva origem declarada (FR-002/FR-003); `SEARCH_RESULT` quando veio de busca textual; Spotify/Tidal guardam URL original mesmo quando áudio resolve via fallback (assumption spec).
- **Validação**: `title` não-blank (fallback `"Faixa desconhecida"`); `sourceUrl` blank só se `NotFound` (não cria `Track`); `duration` pode ser `UNKNOWN` (live) — `queue` exibe `ao vivo`.
- **Mapeamento**: `TrackMapper` no `adapter/out/audio` converte `AudioTrackInfo → Track`; nunca vaza `AudioTrack` para o domínio.

### 2. GuildQueue (agregado puro — sem IO, sem JDA)

```java
class GuildQueue {
  GuildId guildId;
  Deque<Track> upcoming;      // FIFO, ordem de chegada
  Track current;              // nullable
  PlaybackState state;        // IDLE | PLAYING | PAUSED
  Instant becameIdleAt;       // p/ FR-015 (5 min)
}
enum PlaybackState { IDLE, PLAYING, PAUSED }
record GuildId(String value) {}
```

- **Invariantes**:
  - `PLAYING` exige `current != null`; `IDLE` exige `current == null && upcoming.isEmpty()`.
  - `PAUSED` só a partir de `PLAYING` (via `stop`); `resume` só de `PAUSED` → `PLAYING` (FR-007/FR-008).
  - `skip`: descarta `current`, promove `upcoming.poll()` → `current` (`PLAYING`) ou `IDLE` + `becameIdleAt=now()` se vazia (FR-009).
  - `play` com fila vazia + `IDLE` → `current` direto; senão `upcoming.offer()` (FR-005).
  - Playlist preserva ordem do resolver (FR-004); truncar em `MAX_PLAYLIST_TRACKS=100` e reportar `added vs ignored` (assumption spec → detalhado no plan).
- **Concorrência**: `ConcurrentHashMap<GuildId, GuildQueue>` no repository + `ReentrantLock` por guild (nunca `synchronized` + IO em VT). Resolução de comandos concorrentes (`play`/`skip` simultâneos) mantém FIFO sem perda/duplicação (edge case spec).
- **Transições**:
  ```
  IDLE --play--> PLAYING
  PLAYING --stop--> PAUSED --resume--> PLAYING
  PLAYING --skip[upcoming não-vazia]--> PLAYING (nova current)
  PLAYING --skip[fila vazia]--> IDLE (inicia timer 5min)
  PAUSED --skip--> PLAYING | IDLE (mesma regra)
  IDLE --5min ocioso--> desconectado (limpa becameIdleAt ao novo play)
  ```

### 3. BotCommand (sealed — extensibilidade FR-012)

```java
sealed interface BotCommand permits PlayCmd, StopCmd, ResumeCmd, SkipCmd, QueueCmd, PingCmd {
  record PlayCmd(GuildId guild, UserId user, String query) implements BotCommand {}
  record StopCmd(GuildId guild, UserId user) implements BotCommand {}
  record ResumeCmd(GuildId guild, UserId user) implements BotCommand {}
  record SkipCmd(GuildId guild, UserId user) implements BotCommand {}
  record QueueCmd(GuildId guild, UserId user) implements BotCommand {}
  record PingCmd() implements BotCommand {}
}
```

- Novo comando = novo `record` em `permits` + novo `UseCase`; `switch` exaustivo sem `default` quebra em compilação até tratar (SC-005).

### 4. ResolverResult (sealed — saída do `TrackResolverPort`)

```java
sealed interface ResolverResult
  permits ResolvedTrack, ResolvedPlaylist, NotFound, LoadFailed {
  record ResolvedTrack(Track track) implements ResolverResult {}
  record ResolvedPlaylist(List<Track> tracks, int totalReported, int ignored) implements ResolverResult {}
  record NotFound(String query) implements ResolverResult {}
  record LoadFailed(String query, String reason, boolean retryable) implements ResolverResult {}
}
```

- `NotFound` (busca sem resultados) e `LoadFailed` (privado/excluído/idade/região/fonte fora do ar) viram mensagem amigável sem tocar fila atual (FR-016, edge cases).

### 5. VoiceSession (por guild — estado de conexão)

```java
record VoiceSession(GuildId guild, VoiceChannelId channel, Instant connectedAt, Instant lastActivityAt) {}
```

- Uma sessão por guild; `play` sem usuário em voz e sem sessão determinável → recusa orientando (FR-013). Fim da fila → `lastActivityAt` inicia contagem FR-015.

## Relacionamentos

```
GuildQueue (1) ──current/upcoming──> (*) Track
Track (*) ──requestedBy──> (1) Requester
VoiceSession (1:1) GuildQueue por GuildId
BotCommand ──executado contra──> GuildQueue (+ VoiceSession p/ play)
TrackResolverPort ──produz──> ResolverResult ──vira──> Track(s) em GuildQueue
```

## Regras de exibição (`queue`)

- `queue` mostra `current` em destaque + `upcoming` ordenada com `posição, título, solicitante(displayName)` (FR-006/FR-010, SC-006).
- Fila vazia + `IDLE` → mensagem explícita de vazia (não lista vazia/erro).
- Paginação/resumo além de N (ex.: 10–20 por página + `... e mais X`) para não estourar limite de mensagem Discord (edge case).

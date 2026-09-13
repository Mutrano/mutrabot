# Quickstart: validação end-to-end (002-livestream-selfbot)

**Spec**: [spec.md](spec.md) | **Contratos**: [contracts/commands.md](contracts/commands.md) | **Modelo**: [data-model.md](data-model.md)

Guia de validação, não implementação. Comandos exatos podem variar após `tasks.md`; a ordem de validação não muda.

> **Aviso**: a transmissão usa uma **conta de usuário (self-bot)**. Isso viola o ToS do Discord e pode banir a conta. Use **somente uma conta burner** como Y, nunca a principal.

## Pré-requisitos

- Tudo do bot de música (JDK 25, `./mvnw`, bot criado com `GUILD_VOICE_STATES`, `DISCORD_TOKEN`).
- **Node.js 20+** e `npm` instalados.
- **FFmpeg** no PATH (build de Windows com `gdigrab`), ou caminho em `LIVESTREAM_FFMPEG_PATH`.
- Conta **Y** (burner) já na guild de teste, com permissões de Conectar, Falar e Transmitir; token obtido e colocado em `LIVESTREAM_USER_TOKEN`.
- Id do X (dono) em `LIVESTREAM_OWNER_ID`; `LIVESTREAM_SIDECAR_SECRET` definido.
- Uma janela de teste aberta (ex.: Bloco de Notas) com título conhecido.

## Setup e execução

```bash
cd streamer && npm install && cd ..   # instala deps do sidecar (nativos node-av/node-datachannel)
./mvnw verify                         # gates de qualidade do bot
./mvnw exec:java                      # sobe o bot; ele inicia o sidecar e aguarda /health
```

## Cenários de validação (mapeados p/ acceptance da spec)

1. **US1 join janela**: X entra no canal de voz, abre a janela, envia `/livestream-join "Bloco de Notas"` → Y aparece no canal de X e a janela aparece como Go Live em ~15s (SC-001).
2. **US1 desktop**: `/livestream-join desktop` → tela inteira transmitida.
3. **US1 sem voz**: X fora de qualquer canal → `/livestream-join` recusa com `🔇 Entre em um canal de voz...` (FR-009).
4. **US1 janela inexistente**: `/livestream-join "janela-que-nao-existe"` → erro amigável e Y não fica preso em voz (FR-007).
5. **US2 leave**: com live ativa → `/livestream-leave` → stream encerra e Y sai do canal (SC-003); repetir o comando → `ℹ️ Não há nenhuma live ativa`.
6. **US3 só o dono**: outra conta envia `/livestream-join`/`/livestream-leave` → recusa efêmera `🚫 Só o dono...` e nenhum efeito no sidecar (SC-002).
7. **Edge sidecar fora do ar**: derrube o processo Node → `/livestream-join` responde `📡 ...`; `/play` e a fila continuam funcionando (SC-004).
8. **Edge substituição**: com live ativa, novo `/livestream-join "Outra Janela"` → a anterior é encerrada e a nova inicia (FR-011).
9. **Edge shutdown**: com live ativa, encerre o bot (`Ctrl+C`) → o sidecar é encerrado e Y sai do canal.
10. **Regressão música**: os 6 comandos de música continuam funcionando junto com a feature.

## Qualidade (gates do plan)

```bash
./mvnw verify                                                  # PR: <90% LINE e <90% BRANCH falha (jacoco:check)
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage   # nightly/main: <60% mutação falha
./mvnw "-Dtest=*ArchitectureTest" test                         # regras hexagonais (domain puro; application só domain)
open target/site/jacoco/index.html
open target/pit-reports/index.html
```

## Contrato do sidecar (smoke manual)

```bash
# com o bot/sidecar no ar:
curl -H "Authorization: Bearer $LIVESTREAM_SIDECAR_SECRET" http://127.0.0.1:8790/health
# -> {"status":"ready"}

curl -X POST -H "Authorization: Bearer $LIVESTREAM_SIDECAR_SECRET" -H "Content-Type: application/json" \
  -d '{"guildId":"<id>","channelId":"<id>","window":"desktop"}' http://127.0.0.1:8790/start
# -> {"status":"streaming"}

curl -X POST -H "Authorization: Bearer $LIVESTREAM_SIDECAR_SECRET" -H "Content-Type: application/json" \
  -d '{"guildId":"<id>"}' http://127.0.0.1:8790/stop
# -> {"status":"idle"}
```

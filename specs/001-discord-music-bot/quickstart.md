# Quickstart: validação end-to-end (001-discord-music-bot)

**Spec**: [spec.md](spec.md) | **Contratos**: [contracts/commands.md](contracts/commands.md) | **Modelo**: [data-model.md](data-model.md)

Guia de validação, não implementação. Comandos exatos de build podem variar após `tasks.md`; a ordem de validação não muda.

## Pré-requisitos

- JDK 25 (`java -version`), Maven wrapper (`./mvnw -version`, versão com suporte a `release=25`) no repo root.
- Bot criado no [Discord Developer Portal](https://discord.com/developers/applications) com intents `GUILD_VOICE_STATES`, permissões Conectar + Falar + Enviar Mensagens; token em variável de ambiente (nunca commitado), ex.: `DISCORD_TOKEN`.
- Guild de teste + canal de voz; cliente Discord desktop/mobile.

## Setup e execução

```bash
./mvnw verify                  # compila + testes + JaCoCo check 90% (falha se <90% linha/branch)
./mvnw exec:java                # sobe o bot (bootstrap.BotApplication, wiring manual; requer exec-maven-plugin configurado)
# dev: registrar slash só na guild de teste p/ propagação instantânea (ver research.md)
```

## Cenários de validação (mapeados p/ acceptance da spec)

1. **US1 play link**: entre em voz → `/play <link YouTube>` → espera áudio + `▶ Tocando agora ...` em <1min (SC-001).
2. **US1 play busca**: `/play <nome música + artista>` (sem link) → melhor resultado enfileira/toca + anuncia título/fonte.
3. **US1 play playlist**: `/play <link playlist>` → anuncia `N faixas`, ordem preservada (SC-002 ≥95% das públicas).
4. **US2 stop/resume**: tocando → `/stop` (pausa + `⏸ Pausado`) → `/resume` (continua do ponto).
5. **US2 skip**: fila 2+ → `/skip` → atual encerra, próxima começa + anúncio; `/skip` na última → `📭 A fila acabou` + desconecta após 5min ocioso.
6. **US3 queue**: 3 faixas de 2 usuários → `/queue` mostra atual em destaque + ordem + solicitante de cada (SC-006); fila vazia → mensagem de vazia.
7. **US4 ping**: `/ping` → `🏓 pong` em poucos segundos.
8. **Edge play sem voz**: fora de canal de voz + bot sem sessão → `/play` recusa com `🔇 Entre em um canal ...`.
9. **Edge entrada ruim**: link inválido/privado/busca vazia → erro amigável específico, fila/reprodução intactas (SC-004).
10. **Edge comandos vazios**: `/stop|/resume|/skip|/queue` sem nada tocando → `ℹ️ Nada ...` (sem stacktrace).
11. **Extensibilidade SC-005**: adicionar comando exemplo (`help`) = novo `record` + UseCase + registro; `git diff --stat` não toca arquivos do núcleo; `./mvnw test` + ArchUnit passam.

## Qualidade (gates do plan)

```bash
./mvnw verify                                                  # PR: falha se <90% LINE e <90% BRANCH (jacoco:check)
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage   # nightly/main: falha se mutation <60%
# PR mutation diff-only (informativo): restringir targetClasses ao pacote alterado,
# publicar target/pit-reports/index.html como artefato; ver research.md p/ ler sobreviventes como hints
open target/site/jacoco/index.html
open target/pit-reports/index.html                             # rosa = SURVIVED/NO_COVERAGE → escrever 1 teste focado por mutante
./mvnw -Dtest='*ArchitectureTest' test                         # regras hexagonais (domain sem JDA/LavaPlayer etc.)
```

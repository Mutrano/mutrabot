# mutrabot

Bot de música para Discord em Java 25. Usa JDA 6 para interações e voz, LavaPlayer com youtube-source para áudio e arquitetura hexagonal (domínio puro, portas e adaptadores).

## Requisitos

- JDK 25 no PATH (`java -version`).
- Maven Wrapper no repositório (`./mvnw -version` usa Maven 3.9.15).
- Voz do Discord exige DAVE. O projeto inclui JDAVE (`club.minnced:jdave-api` e nativos para Windows/Linux x86-64) e configura `DaveSessionFactory` no `JdaConfig`. Sem isso o bot entra em loop de reconexão de voz.
- Bot criado no Discord Developer Portal com o intent `GUILD_VOICE_STATES` e permissões de Conectar, Falar e Enviar Mensagens.
- Token em variável de ambiente. Nunca versione o token.

## Configuração

Crie o `.env` na raiz do projeto (já existe com placeholders) e cole o token:

```dotenv
DISCORD_TOKEN=seu-token
DISCORD_GUILD_ID=id-do-servidor-de-teste
```

O bot lê o `.env` na inicialização. Variáveis de ambiente do sistema têm prioridade sobre o arquivo. O `.env` está no `.gitignore`.

`DISCORD_GUILD_ID` é opcional e registra os slash commands na guild, com propagação instantânea. Sem ele o bot registra globalmente, e a propagação pode levar até uma hora. Em desenvolvimento use a variável da guild.

## Executar

```powershell
.\mvnw.cmd verify          # compila, roda testes e falha se a cobertura cair abaixo de 90% de linha ou branch
.\mvnw.cmd exec:java       # inicia o bot (classe bootstrap.BotApplication)
```

No Linux/macOS use `./mvnw`.

## Git hooks

Os hooks ficam versionados em `.githooks`. Ative uma vez por clone:

```powershell
git config core.hooksPath .githooks
```

O hook `pre-push` roda gitleaks nos commits que estão sendo enviados e bloqueia o push se encontrar segredos. Instale o gitleaks antes (`winget install gitleaks`, `brew install gitleaks` ou os binários em https://github.com/gitleaks/gitleaks#installing). Em clones Linux, garanta o bit de execução: `chmod +x .githooks/pre-push`.

## Comandos

- `/play <link ou nome>`: toca um link de faixa (YouTube, SoundCloud), um termo de busca ou um link de playlist. Spotify e Tidal são resolvidos por metadados e tocados pela melhor correspondência no YouTube.
- `/stop`: pausa a faixa atual e mantém a fila.
- `/resume`: retoma do ponto onde parou.
- `/skip`: encerra a faixa atual e inicia a próxima; com a fila vazia, anuncia o fim e desconecta após 5 minutos ociosos.
- `/queue`: mostra a faixa atual e as próximas com o nome de quem pediu, com paginação a partir de 20 faixas.
- `/ping`: responde `pong` com latência do gateway e da API REST.
- `/help`: lista os comandos.

As respostas são em português. Qualquer membro que possa enviar mensagens usa todos os comandos.

## Livestream (transmissão de tela)

Dois comandos restritos ao dono (`LIVESTREAM_OWNER_ID`) transmitem uma janela do computador como "Go Live" numa conta de usuário separada (self-bot, chamada Y). O Discord bloqueia vídeo de contas de bot, por isso a transmissão sai de uma conta de usuário.

> ⚠️ Self-bot viola o ToS do Discord e pode banir a conta. Use **somente uma conta burner**, nunca a principal.

- `/livestream-join <janela>`: a conta Y entra no mesmo canal de voz do dono e transmite a janela pelo título; use `desktop` para a tela inteira. Sem áudio.
- `/livestream-leave`: encerra a transmissão e faz Y sair do canal.

Pré-requisitos: Node.js 20+, FFmpeg no PATH (captura via `gdigrab` no Windows) e a conta Y na guild com permissão de Conectar, Falar e Transmitir. O bot sobe e encerra o sidecar em `streamer/` automaticamente; na primeira vez rode `cd streamer; npm install`.

Variáveis no `.env`:

```dotenv
LIVESTREAM_OWNER_ID=seu-user-id
LIVESTREAM_USER_TOKEN=token-da-conta-burner
LIVESTREAM_SIDECAR_SECRET=um-segredo-local-qualquer
LIVESTREAM_SIDECAR_PORT=8790
# LIVESTREAM_FFMPEG_PATH=C:\ffmpeg\bin\ffmpeg.exe
```

Sem essas variáveis a feature fica desabilitada e o bot de música continua normal. Detalhes de planejamento em `specs/002-livestream-selfbot/`.

## YouTube

Vídeos comuns tocam direto pelo `youtube-source`. Alguns vídeos que o YouTube marca com "requires login" (conteúdo restrito ou bloqueio anti-bot) falham nesse extrator. Para esses casos o bot usa o `yt-dlp` como resolvedor preferido quando ele está instalado:

```powershell
python -m pip install -U --user yt-dlp
```

O bot detecta `yt-dlp` no PATH, depois `python -m yt_dlp`, e usa Node como runtime JS se disponível. Se o executável estiver em outro lugar, preencha `YTDLP_PATH` no `.env`. Sem yt-dlp, o bot continua funcionando com o `youtube-source` e avisa quando um vídeo não puder ser tocado.

Alternativa para vídeos restritos: OAuth com conta burner.

```powershell
.\mvnw.cmd exec:java "-Dexec.args=--youtube-oauth"
```

O log mostra uma URL e um código. Autorize em https://www.google.com/device (conta burner, não a principal) e cole o `YOUTUBE_REFRESH_TOKEN=...` impresso no `.env`. OAuth não é necessário se o yt-dlp resolver seus vídeos.

## Qualidade

```powershell
.\mvnw.cmd verify                                                  # gate de PR: JaCoCo 90% linha e 90% branch
.\mvnw.cmd test-compile org.pitest:pitest-maven:mutationCoverage   # gate de main/nightly: mutação 60%
.\mvnw.cmd "-Dtest=*ArchitectureTest" test                         # regras hexagonais com ArchUnit
```

Relatórios: `target/site/jacoco/index.html` e `target/pit-reports/index.html`.

Classes de wiring e integração ficam fora do gate JaCoCo (`bootstrap`, listener JDA, adaptador de playback, send handler, lookup de metadados externos). Veja a lista em `pom.xml`.

## Estrutura

```text
src/main/java/com/mutrabot/
├── domain/            # modelo, comandos selados, resultados de resolução (apenas java.base)
├── application/       # portas de entrada/saída e serviços de caso de uso
├── adapter/in/discord # listener JDA, mapeamento e respostas
├── adapter/out/audio  # LavaPlayer, youtube-source, registro de áudio
├── adapter/out/persistence # fila em memória por guild
└── bootstrap/         # wiring manual, JDA, executores e comandos
```

O domínio não conhece JDA nem LavaPlayer: o `ArchitectureTest` falha se alguém importar essas bibliotecas fora dos adaptadores.

## Limitações conhecidas

- Spotify e Tidal não têm stream direto. O bot busca o título via oEmbed e toca a melhor correspondência; se a consulta de metadados falhar, responde com erro amigável.
- YouTube pode bloquear vídeos por idade, região ou verificação de bot. O bot informa o motivo e mantém a fila.
- Faixas com duração desconhecida (lives) aparecem como `ao vivo` no `/queue`.
- Playlists são limitadas a 100 faixas por pedido; o excedente é anunciado.

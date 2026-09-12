# mutrabot

Bot de música para Discord em Java 25. Usa JDA 6 para interações e voz, LavaPlayer para áudio (YouTube via `yt-dlp`) e arquitetura hexagonal (domínio puro, portas e adaptadores).

## Requisitos

- JDK 25 no PATH (`java -version`).
- Maven Wrapper no repositório (`./mvnw -version` usa Maven 3.9.15).
- `yt-dlp` instalado para tocar YouTube (links, buscas e playlists): `python -m pip install -U --user yt-dlp`.
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

## YouTube

O YouTube é resolvido inteiramente pelo `yt-dlp` (vídeo único, busca e playlist). Para vídeo único, o bot baixa a faixa (`bestaudio`) para um arquivo temporário antes de tocar e reproduz o arquivo local. O download usa requisições em partes (`--http-chunk-size 10M`), o que evita o throttling de conexão única do YouTube — streaming direto costuma ser limitado a ~2x o tempo real e causa cortes quando o buffer esgota. Se o download falhar, o bot volta para streaming direto. Lives não são baixadas.

```powershell
python -m pip install -U --user yt-dlp
```

O cache fica em `%TEMP%\mutrabot-audio` (ou o equivalente no Linux/macOS), é limpo na inicialização e os arquivos são removidos quando a faixa sai de reprodução.

O bot detecta `yt-dlp` no PATH, depois `python -m yt_dlp`, e usa Node como runtime JS se disponível. Se o executável estiver em outro lugar, defina a variável de ambiente `YTDLP_PATH`. Sem o `yt-dlp`, o bot avisa que o resolvedor de YouTube não está disponível e mantém a fila.

Playlists são resolvidas em uma única chamada ao `yt-dlp`, com limite de 100 faixas por pedido. Um link `watch?v=...&list=RD...` (mix do YouTube) toca só o vídeo; um `list=PL...` enfileira a playlist.

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
├── adapter/out/audio  # LavaPlayer, yt-dlp, registro de áudio
├── adapter/out/persistence # fila em memória por guild
└── bootstrap/         # wiring manual, JDA, executores e comandos
```

O domínio não conhece JDA nem LavaPlayer: o `ArchitectureTest` falha se alguém importar essas bibliotecas fora dos adaptadores.

## Limitações conhecidas

- Spotify e Tidal não têm stream direto. O bot busca o título via oEmbed e toca a melhor correspondência; se a consulta de metadados falhar, responde com erro amigável.
- YouTube pode bloquear vídeos por idade, região ou verificação de bot. O bot informa o motivo e mantém a fila.
- Faixas com duração desconhecida (lives) aparecem como `ao vivo` no `/queue`.
- Playlists são limitadas a 100 faixas por pedido; o excedente é anunciado.

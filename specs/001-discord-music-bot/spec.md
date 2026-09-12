# Feature Specification: Discord Music Bot

**Feature Branch**: `001-discord-music-bot`

**Created**: 2026-09-12

**Status**: Draft

**Input**: User description: "Bot de discord para uso no meu servidor, ele deve utilizar a pattern Command pra conseguir escalar os comandos. Features comandos mvp: play - toca qualquer link de música, do soundcloud, youtube, spotify, tidal, também busca pra dar queue. deve aceitar playlists também; stop - pausa a música; resume - resume na música; skip - pula essa música na fila; queue - músicas na fila e quem colocou na fila; ping - devolve pong"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tocar música por link, busca ou playlist (Priority: P1)

Um membro do servidor entra em um canal de voz e pede ao bot para tocar uma música informando um link (YouTube, SoundCloud, Spotify, Tidal), um nome para busca, ou um link de playlist. O bot entra no canal de voz do solicitante (ou usa o canal onde já está), adiciona a(s) faixa(s) à fila e inicia a reprodução se nada estiver tocando.

**Why this priority**: É o valor central do bot. Sem isso nenhuma outra função faz sentido. Entrega sozinha um MVP utilizável (ouvir música juntos).

**Independent Test**: Pode ser totalmente testado pedindo `play` com (a) link de faixa única, (b) termo de busca textual e (c) link de playlist, e verificando que o áudio toca no canal de voz e a fila é preenchida corretamente. Entrega valor imediato: música tocando no canal.

**Acceptance Scenarios**:

1. **Given** usuário está em um canal de voz e nada está tocando, **When** usuário envia `play` com link de faixa válida do YouTube, **Then** o bot entra no canal de voz do usuário e inicia a reprodução dessa faixa em até alguns segundos e confirma qual faixa começou a tocar.
2. **Given** usuário está em um canal de voz, **When** usuário envia `play` com texto de busca (ex.: nome da música + artista) sem link, **Then** o bot seleciona o melhor resultado correspondente, adiciona à fila e anuncia título/fonte da faixa enfileirada.
3. **Given** usuário está em um canal de voz, **When** usuário envia `play` com link de playlist, **Then** o bot enfileira todas as faixas reconhecidas da playlist, mantém a ordem original e anuncia quantas faixas foram adicionadas.
4. **Given** uma faixa já está tocando, **When** outro usuário envia `play` com qualquer entrada válida, **Then** a nova faixa (ou faixas) é adicionada ao fim da fila sem interromper a reprodução atual.
5. **Given** usuário envia `play` de qualquer fonte suportada (YouTube, SoundCloud, Spotify, Tidal), **When** a entrada é válida, **Then** o bot trata da mesma forma do ponto de vista do usuário (enfileira e toca), independentemente da fonte de origem.

---

### User Story 2 - Controlar a reprodução atual (Priority: P2)

Membros do canal controlam a reprodução em andamento: pausam temporariamente com `stop`, retomam com `resume` e pulam a faixa atual com `skip` (iniciando automaticamente a próxima da fila).

**Why this priority**: Controle básico é essencial para uso diário — sem pausar/retomar/pular, a experiência de fila trava no primeiro imprevisto.

**Independent Test**: Pode ser testado com uma fila de 2+ faixas: executar `stop` e verificar que o áudio pausa, `resume` e verificar que continua do mesmo ponto, `skip` e verificar que a faixa atual é encerrada e a próxima começa. Entrega valor de controle da sessão.

**Acceptance Scenarios**:

1. **Given** uma faixa está tocando, **When** usuário envia `stop`, **Then** a reprodução é pausada no ponto atual e o bot confirma o estado pausado.
2. **Given** a reprodução está pausada, **When** usuário envia `resume`, **Then** a reprodução continua do ponto onde pausou.
3. **Given** há faixa atual + pelo menos 1 na fila, **When** usuário envia `skip`, **Then** a faixa atual é encerrada, removida da posição atual e a próxima faixa começa a tocar com anúncio da nova faixa.
4. **Given** a fila está vazia e a última faixa termina ou sofre `skip`, **When** não há próxima faixa, **Then** o bot informa que a fila acabou e inicia contagem de 5 minutos de ociosidade antes de desconectar-se (FR-015).

---

### User Story 3 - Consultar a fila e saber quem pediu o quê (Priority: P2)

Qualquer membro quer ver o que está tocando agora, o que vem a seguir e quem colocou cada música na fila, usando `queue`.

**Why this priority**: Transparência da fila evita conflitos ("quem colocou essa música?") e é barata de entregar uma vez que a fila existe.

**Independent Test**: Com 3 faixas enfileiradas por 2 usuários diferentes, executar `queue` e verificar que a resposta lista ordem, títulos e solicitante de cada faixa mais a faixa atual. Entrega valor organizacional imediato.

**Acceptance Scenarios**:

1. **Given** há faixa atual + faixas enfileiradas de usuários distintos, **When** usuário envia `queue`, **Then** o bot exibe a faixa atual em destaque e a lista ordenada das próximas com título e nome/apelido de quem solicitou cada uma.
2. **Given** a fila está vazia e nada está tocando, **When** usuário envia `queue`, **Then** o bot informa claramente que a fila está vazia em vez de mostrar lista vazia ou erro técnico.

---

### User Story 4 - Verificar que o bot está online (Priority: P3)

Um membro ou administrador quer confirmar rapidamente que o bot está ativo e responsivo usando `ping`.

**Why this priority**: Diagnóstico trivial que ajuda a distinguir "bot offline" de "problema de voz/fila". Prioridade menor porque não entrega valor musical, mas é padrão de MVP de bot.

**Independent Test**: Enviar `ping` com o bot online e verificar resposta `pong` em poucos segundos. Testável isoladamente sem canal de voz ou fila.

**Acceptance Scenarios**:

1. **Given** o bot está online, **When** usuário envia `ping`, **Then** o bot responde com `pong` (podendo incluir latência) em poucos segundos.

---

### Edge Cases

- O que acontece quando o usuário envia `play` sem estar em nenhum canal de voz e o bot não está em nenhum canal? O bot deve recusar com mensagem orientando a entrar em um canal de voz primeiro.
- Como o sistema lida com link inválido, vídeo privado/excluído, restrição de idade/região ou busca sem resultados? Deve informar falha específica daquela entrada sem derrubar a fila ou a reprodução atual.
- Como o sistema lida com playlist muito grande (ex.: centenas de faixas)? Deve enfileirar até um limite documentado e informar quantas foram adicionadas vs. ignoradas.
- O que acontece quando `stop`/`resume`/`skip`/`queue` são usados sem nada tocando e com fila vazia? Deve responder mensagem de estado ("nada tocando") em vez de erro técnico.
- O que acontece quando o bot perde conexão com o canal de voz (expulso, queda, troca de canal)? Deve tratar a interrupção sem travar a fila e informar o estado ao retomar.
- Como o sistema lida com comandos enviados em rápida sequência (ex.: vários `play`/`skip` simultâneos de usuários diferentes)? A ordem da fila deve permanecer consistente sem duplicar ou perder faixas.
- O que acontece quando o usuário pede fonte suportada mas indisponível no momento (ex.: API/fora do ar)? Deve informar indisponibilidade da fonte e manter a fila intacta.
- Como o sistema evita que a resposta do `queue` estoure limites de mensagem quando há muitas faixas? Deve paginar/resumir (ex.: mostrar primeiras N + total restante).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE oferecer os seis comandos MVP com os nomes definidos: `play`, `stop`, `resume`, `skip`, `queue` e `ping`.
- **FR-002**: O sistema DEVE aceitar no comando `play` links de faixas individuais das fontes YouTube, SoundCloud, Spotify e Tidal.
- **FR-003**: O sistema DEVE aceitar no comando `play` entrada de busca textual (sem link) e selecionar o melhor resultado correspondente para enfileirar.
- **FR-004**: O sistema DEVE aceitar no comando `play` links de playlists e enfileirar as faixas reconhecidas preservando a ordem original.
- **FR-005**: O sistema DEVE manter uma fila de reprodução ordenada por servidor (guild), com reprodução sequencial automática (ao terminar uma faixa, inicia a próxima sem intervenção).
- **FR-006**: O sistema DEVE registrar e exibir o solicitante de cada faixa (quem colocou na fila) de forma visível no comando `queue` e nos anúncios de faixa.
- **FR-007**: O comando `stop` DEVE apenas pausar a reprodução atual no ponto atual, mantendo fila e posição (decisão Q1: opção A). Retomada ocorre exclusivamente via `resume`; `stop` nunca limpa a fila nem desconecta do canal de voz.
- **FR-008**: O comando `resume` DEVE retomar a reprodução pausada do ponto onde parou; se nada estiver pausado, deve informar o estado atual em vez de reiniciar ou falhar.
- **FR-009**: O comando `skip` DEVE encerrar a faixa atual e iniciar imediatamente a próxima da fila quando existir; quando a fila estiver vazia, deve encerrar a reprodução e informar que a fila acabou.
- **FR-010**: O comando `queue` DEVE exibir a faixa atual (se houver) e a lista ordenada das próximas faixas com título e solicitante; com fila vazia, deve informar explicitamente que está vazia.
- **FR-011**: O comando `ping` DEVE responder com `pong` quando o bot estiver online, em tempo hábil (poucos segundos).
- **FR-012**: O sistema DEVE organizar todos os comandos usando o padrão Command de forma que adicionar um novo comando não exija alterar o núcleo do bot (novo comando = nova classe/módulo autocontido registrado em um ponto único de registro/descoberta).
- **FR-013**: O sistema DEVE validar pré-condições de voz: se o solicitante não está em canal de voz e o bot não tem canal alvo determinável, o comando `play` deve ser recusado com mensagem orientando o usuário.
- **FR-014**: Qualquer membro do servidor com permissão de enviar mensagens no canal de comandos PODE usar todos os comandos MVP sem restrição de cargo ou canal de voz (decisão Q2: opção A — modelo aberto).
- **FR-015**: Quando a fila termina e o bot fica ocioso (sem reprodução e sem novos pedidos), o sistema DEVE desconectar-se automaticamente do canal de voz após 5 minutos de ociosidade e informar o estado (decisão Q3: opção B). Qualquer novo `play` reconecta normalmente.
- **FR-016**: O sistema DEVE informar erros de forma amigável e não-técnica (entrada inválida, sem resultados, fonte indisponível, sem permissão de voz) sem interromper a reprodução atual nem limpar a fila.
- **FR-017**: O sistema DEVE anunciar transições importantes no canal de texto (faixa que começou, faixa adicionada, playlist adicionada com contagem, fila vazia) com título da faixa e solicitante.

### Key Entities

- **Faixa**: Representa uma música individual; atributos: título, fonte de origem (YouTube/SoundCloud/Spotify/Tidal/resultado de busca), duração, solicitante, posição na fila. É a unidade de reprodução e de listagem.
- **Fila de Reprodução (por servidor)**: Sequência ordenada de faixas pendentes + referência à faixa atual e estado (tocando/pausado/parado); pertence a um servidor; governada por ordem de chegada salvo `skip`.
- **Solicitante**: Membro do Discord que pediu a faixa via `play`; atributo exibido junto a cada faixa para atribuição e moderação social.
- **Comando**: Unidade extensível de interação (play, stop, resume, skip, queue, ping e futuros); cada comando tem nome, descrição, validação de entrada e execução; registrado/descoberto sem alterar o núcleo.
- **Sessão de Voz do Servidor**: Vínculo entre o bot, o canal de voz ativo e a fila em reprodução em um servidor; determina onde o áudio toca e qual fila está ativa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Membros conseguem colocar uma música para tocar (via link ou busca) e ouvir o áudio no canal de voz em até 1 minuto a partir do envio do comando, na primeira tentativa, em condições normais de rede.
- **SC-002**: Pedidos de playlist adicionam corretamente 95% ou mais das faixas públicas reconhecíveis preservando a ordem, com anúncio da quantidade adicionada.
- **SC-003**: 90% ou mais dos usuários testados conseguem pausar, retomar, pular e consultar a fila sem ajuda externa após ver a lista de comandos uma vez.
- **SC-004**: Em 100% dos casos de entrada inválida ou fonte indisponível, o bot responde com mensagem compreensível e a reprodução atual/fila permanece intacta (zero derrubadas de sessão por entrada ruim em testes).
- **SC-005**: Adicionar um sétimo comando de exemplo (ex.: `help`) exige criar apenas um novo módulo de comando + registro, sem modificar arquivos do núcleo existente — verificado por revisão da mudança.
- **SC-006**: O comando `queue` exibe corretamente título e solicitante de cada faixa em 100% das verificações com filas de até 20 faixas montadas por múltiplos usuários.

## Assumptions

- Idioma das respostas do bot: português brasileiro (PT-BR), já que o pedido foi feito em português para servidor próprio.
- Interface de comandos: comandos de barra (slash commands) do Discord como padrão moderno; prefixo textual (`!`) somente se necessário para compatibilidade — decisão de implementação posterior.
- Fontes Spotify/Tidal: quando não houver streaming direto permitido, o bot resolve os metadados (título/artista) e reproduz o melhor áudio correspondente disponível, informando a origem original. O usuário percebe o mesmo fluxo de `play` para todas as fontes.
- Limite de playlist: assume-se um teto razoável (ex.: 100 faixas por pedido) com mensagem de truncamento; valor exato a definir na fase de planejamento.
- Escopo inicial: um servidor (o servidor do solicitante), mas fila e sessão são isoladas por servidor para permitir expansão multiserver sem retrabalho.
- Usuário tem permissões básicas: bot possui permissões de Conectar, Falar e Enviar Mensagens nos canais relevantes; configuração de permissões no portal do Discord é pré-requisito documentado.
- Erros de rede/voz são transitórios: o bot não deleta a fila em caso de queda breve de voz; reconexão ou aviso explícito é suficiente para o MVP.
- Padrão Command: interpretado como requisito de extensibilidade (cada comando encapsulado, registro centralizado), não como prescrição de linguagem ou biblioteca.
- Decisões de clarificação (2026-09-12, Q1=A, Q2=A, Q3=B): `stop` apenas pausa; permissões abertas a todos os membros; desconexão automática após 5 minutos de ociosidade com fila vazia.

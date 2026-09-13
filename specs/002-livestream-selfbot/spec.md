# Feature Specification: Livestream por self-bot (janela)

**Feature Branch**: `002-livestream-selfbot`

**Created**: 2026-09-12

**Status**: Draft

**Input**: User description: "isso deve viver em um comando /livestream-join que recebe a janela para fazer live, e também é necessário um comando /livestream-leave para que o usuário Y saia da sala, apenas usuário X(eu) pode utilizar esse comando" + decisões: "janela = só título digitado (com 'desktop' para tela inteira)", "Y entra no canal de voz do X", "o bot Java gerencia o sidecar".

## Contexto e decisão central

O Discord **bloqueia vídeo/Go Live vindo de conta de bot**. A transmissão só é possível por uma **conta de usuário** (self-bot) — chamada aqui de **Y** — via biblioteca não oficial (`@dank074/discord-video-stream`, Node), que conversa com o gateway de voz e envia RTP de vídeo. O bot Java (mutrabot) **não transmite**: ele apenas **orquestra** Y por meio de um **sidecar Node** local. O usuário **X** (dono, configurado por id) comanda tudo pelos slash commands.

Por isso esta feature cruza a fronteira do ToS do Discord (self-bot). O risco é explícito e aceito pelo dono: **Y deve ser uma conta burner**, nunca a conta principal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Iniciar transmissão de uma janela (Priority: P1)

X está em um canal de voz, digita `/livestream-join` informando o título de uma janela (ou `desktop`). O bot manda o sidecar Node fazer a conta Y entrar nesse mesmo canal de voz e iniciar um Go Live mostrando a janela escolhida.

**Why this priority**: É o valor central da feature — sem isso não há live.

**Independent Test**: Pode ser testado com X em voz e uma janela aberta: enviar `/livestream-join "Notepad"` e verificar que Y aparece no canal de X e que a janela aparece como Go Live; sem depender do comando de saída.

**Acceptance Scenarios**:

1. **Given** X está em um canal de voz e existe uma janela com título compatível, **When** X envia `/livestream-join` com esse título, **Then** Y entra no canal de voz de X e a janela é transmitida como Go Live em poucos segundos, com confirmação PT-BR.
2. **Given** X está em um canal de voz, **When** X envia `/livestream-join desktop`, **Then** a tela inteira é transmitida.
3. **Given** não existe janela com o título informado, **When** X envia `/livestream-join`, **Then** o bot responde erro PT-BR amigável e Y não fica preso em voz.
4. **Given** o solicitante (X) não está em nenhum canal de voz, **When** envia `/livestream-join`, **Then** o bot recusa orientando a entrar em um canal de voz primeiro.

---

### User Story 2 - Encerrar transmissão (Priority: P2)

X usa `/livestream-leave` para encerrar o Go Live e fazer Y sair do canal de voz.

**Why this priority**: Sem isso Y fica preso transmitindo/na sala. Fecha o ciclo de vida.

**Independent Test**: Com uma live ativa, enviar `/livestream-leave` e verificar que a transmissão para e Y sai do canal em poucos segundos.

**Acceptance Scenarios**:

1. **Given** há uma transmissão ativa, **When** X envia `/livestream-leave`, **Then** o Go Live é encerrado e Y sai do canal de voz, com confirmação PT-BR.
2. **Given** não há transmissão ativa, **When** X envia `/livestream-leave`, **Then** o bot informa que nada está sendo transmitido, sem erro técnico.

---

### User Story 3 - Uso restrito ao dono (Priority: P2)

Somente X (id configurado) pode usar `/livestream-join` e `/livestream-leave`. Qualquer outro membro recebe recusa efêmera e Y não é acionado.

**Why this priority**: A feature usa uma conta de usuário e um segredo; expor isso a qualquer membro seria abuso e risco de ban.

**Independent Test**: Enviar qualquer um dos comandos com uma conta que não é X e verificar recusa efêmera e ausência de efeito no sidecar.

**Acceptance Scenarios**:

1. **Given** um membro que não é X, **When** envia `/livestream-join` ou `/livestream-leave`, **Then** recebe mensagem efêmera de sem permissão e nada é executado no sidecar.
2. **Given** X, **When** envia os comandos, **Then** a autorização é concedida.

---

### Edge Cases

- **Janela não encontrada**: título sem correspondência → erro PT-BR, nenhuma conexão de voz fica pendurada.
- **Títulos duplicados**: múltiplas janelas com o mesmo título → transmite a primeira correspondência e documenta a limitação.
- **Y não está na guild** (não aceitou convite) → erro claro.
- **Token de Y inválido/expirado** → o login do sidecar falha; erro amigável e o bot continua funcionando.
- **Sidecar indisponível/morto** → `/livestream-join` e `/livestream-leave` respondem erro amigável; a música/fila permanece intacta.
- **FFmpeg ou dependências nativas ausentes** → sidecar falha ao iniciar a captura; erro amigável.
- **Já existe transmissão ativa** e novo `/livestream-join` → encerra a anterior e inicia a nova (idempotente).
- **Bot desligado com live ativa** → o shutdown encerra o sidecar e Y sai do canal.
- **X cai do canal de voz durante a live** → a transmissão continua até um `/livestream-leave` (não há reação a isso no MVP).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE oferecer `/livestream-join` com parâmetro obrigatório `janela` (string) e `/livestream-leave` sem parâmetros.
- **FR-002**: `/livestream-join` DEVE fazer a conta de usuário Y entrar no **mesmo canal de voz em que X está** no momento do comando.
- **FR-003**: O sistema DEVE capturar a janela cujo título corresponde ao parâmetro `janela`; o valor literal `desktop` (case-insensitive) DEVE transmitir a tela inteira.
- **FR-004**: A transmissão DEVE ser do tipo **Go Live** (screencast) na conta Y, em vídeo (sem áudio no MVP).
- **FR-005**: `/livestream-leave` DEVE encerrar a transmissão e fazer Y sair do canal de voz, de forma idempotente.
- **FR-006**: Apenas o dono (id em `LIVESTREAM_OWNER_ID`) PODE usar os dois comandos; qualquer outro usuário DEVE receber recusa efêmera em PT-BR e nenhum efeito no sidecar.
- **FR-007**: Toda falha (janela inexistente, Y fora da guild, sidecar indisponível, ffmpeg/nativos ausentes, falha de voz) DEVE virar mensagem PT-BR amigável, sem afetar a reprodução de música nem a fila.
- **FR-008**: Os comandos DEVEM seguir o padrão Command: novos variants no selado `BotCommand`, novas portas de entrada, novos serviços e registro no mapper/listener, **sem alterar a lógica dos comandos existentes**.
- **FR-009**: Se X não estiver em canal de voz, `/livestream-join` DEVE recusar orientando a entrar em um canal primeiro.
- **FR-010**: O bot DEVE confirmar sucesso/falha de cada operação de forma efêmera no canal de texto.
- **FR-011**: Se já houver transmissão ativa, `/livestream-join` DEVE encerrar a anterior antes de iniciar a nova.
- **FR-012**: `/livestream-leave` sem transmissão ativa DEVE informar que nada está sendo transmitido, não falhar.
- **FR-013**: O token de Y e o id de X DEVEM vir de configuração/segredo fora do repositório (`.env`/ambiente); o token MUST NOT aparecer em código, logs, mensagens do bot ou histórico.
- **FR-014**: A comunicação bot↔sidecar DEVE ser local (`127.0.0.1`) e autenticada por segredo compartilhado.
- **FR-015**: O bot DEVE gerenciar o ciclo de vida do sidecar (iniciar no boot, encerrar no shutdown); falha ao iniciar o sidecar MUST NOT impedir o bot de subir (degradação graciosa).

### Key Entities

- **Dono (X)**: membro do Discord cujo id é a única autorização para os comandos; definido por `LIVESTREAM_OWNER_ID`.
- **Transmissor (Y)**: conta de usuário (self-bot) que efetivamente entra em voz e transmite; token em `LIVESTREAM_USER_TOKEN`. Distinta do bot.
- **Alvo da transmissão (janela)**: título de janela do sistema operacional ou o valor especial `desktop`.
- **Sessão de transmissão**: estado por guild (inativa / ativa), com o canal de voz alvo e a janela atual.
- **Sidecar**: processo Node que mantém a conexão de Y, monta o ffmpeg e faz o Go Live; controlado por HTTP local autenticado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com X em voz e a janela aberta, a live aparece no canal em até ~15 segundos na primeira tentativa, em condições normais.
- **SC-002**: 100% das tentativas por não-donos são recusadas sem qualquer chamada ao sidecar.
- **SC-003**: `/livestream-leave` encerra stream e presença de Y em 100% dos casos e é seguro repetir (idempotente).
- **SC-004**: Indisponibilidade do sidecar ou entrada ruim em `/livestream-join` produz mensagem compreensível em 100% dos casos, com fila/reprodução de música intactas.
- **SC-005**: A mudança não altera a lógica dos comandos existentes (verificável por revisão e pelo `ArchitectureTest`).

## Assumptions

- Alvo inicial **Windows** (captura via `ffmpeg -f gdigrab -i title=...`); Linux/macOS ficam como evolução (`x11grab`/`avfoundation`).
- **Vídeo sem áudio** no MVP; capturar áudio de sistema no Windows é bem mais complexo e fica fora do escopo.
- Y é uma conta burner já presente na guild, com permissões de Conectar, Falar e Transmitir.
- Node.js, FFmpeg e as dependências nativas (`node-av`, `node-datachannel`) são pré-requisitos de ambiente, documentados no README.
- Um único sidecar e uma única live ativa por vez (MVP single-guild, alinhado ao bot atual).
- O uso de self-bot viola o ToS do Discord; risco aceito explicitamente pelo dono.

<!--
Sync Impact Report
- Version change: template não ratificado -> 1.0.0 (adoção inicial)
- Modified principles: [PRINCIPLE_1..5] -> 6 princípios concretos (I-VI)
- Added sections: Restrições técnicas; Fluxo de desenvolvimento e gates
- Removed sections: nenhuma
- Follow-up TODOs: nenhum
-->

# mutrabot Constitution

## Core Principles

### I. Domínio puro e dependências unidirecionais (NÃO NEGOCIÁVEL)

O pacote `domain` MUST depender apenas de `java.base`. `application` MUST depender apenas de
`domain` e dela mesma. Adaptadores de entrada e saída MUST depender de `application` e `domain`,
nunca um do outro. Apenas `bootstrap` conhece implementações concretas. As regras do
`ArchitectureTest` MUST permanecer verdes; uma violação exige justificativa no Complexity Tracking
do plano da feature.

Rationale: regras de negócio testáveis sem JDA, LavaPlayer ou rede; trocar um adaptador (por
exemplo, LavaPlayer embarcado por Lavalink) não pode tocar o domínio.

### II. Comandos e portas explícitos

Toda interação MUST ser mapeada para um `BotCommand` selado e atendida por uma porta de entrada em
`application/port/in`. Adicionar um comando MUST ser um novo variant do selado, uma nova porta e um
serviço, mais o registro no mapper e no listener; MUST NOT alterar a lógica de comandos existentes.
`switch` sobre tipos selados MUST ser exaustivo e sem `default`.

Rationale: o compilador aponta todo ponto de impacto ao adicionar uma variante; o padrão Command é
requisito de extensibilidade, não detalhe de implementação.

### III. Qualidade verificável por build (NÃO NEGOCIÁVEL)

`./mvnw verify` MUST passar com JaCoCo de no mínimo 90% de linha e 90% de branch. O PITest MUST
rodar com limiar de 60% de mutação em `main` e nightly. Mutante sobrevivente MUST virar um teste
focado; mutante equivalente MUST ser excluído com justificativa, nunca ignorado em silêncio. As
exclusões de cobertura no `pom.xml` MUST se limitar a wiring, configuração e integração com
processos ou serviços externos. O CI MUST executar os gates em todo push e pull request para
`main`.

Rationale: cobertura sem assert é falsa segurança; mutação mede a qualidade do teste. Os gates
precisam ser automáticos para não dependerem de disciplina manual.

### IV. Segredos e credenciais fora do repositório (NÃO NEGOCIÁVEL)

Token do Discord e quaisquer credenciais MUST vir de variável de ambiente ou do arquivo `.env`,
que é ignorado pelo Git. Segredos MUST NOT aparecer em código, histórico, logs, mensagens do bot ou
testes. O hook `pre-push` com gitleaks MUST permanecer versionado e ativo, e o CI MUST rodar a
varredura de segredos. Segredo detectado MUST ser rotacionado antes de qualquer limpeza de
histórico.

Rationale: um vazamento em repositório público é irreversível na prática; a barreira precisa
existir antes do push.

### V. Java moderno com fronteiras de thread respeitadas

O projeto MUST permanecer em Java 25 e usar records, sealed interfaces e pattern matching onde
couber. Virtual Threads MUST ficar restritas à borda de entrada dos comandos. As threads do
gateway e de áudio do JDA e o loop do LavaPlayer MUST NOT executar I/O, espera bloqueante ou
operações de fila com lock.

Rationale: bloqueio nessas threads causa heartbeats perdidos e áudio picotado; o custo do erro é
visível para o usuário final.

### VI. Português do Brasil, erros amigáveis e fila resiliente

Toda mensagem ao usuário MUST ser em PT-BR, não técnica e acionável. Falha de entrada, fonte
indisponível ou playback MUST NOT derrubar a reprodução atual nem limpar a fila; o bot MUST
anunciar o motivo e seguir para a próxima faixa ou encerrar a sessão com aviso. Toda transição
relevante (faixa iniciada, adicionada, playlist, fila vazia, falha) MUST ser anunciada no canal de
texto.

Rationale: o usuário precisa entender o que aconteceu sem ler logs; a sessão de voz é o produto e
não pode morrer por um vídeo ruim.

## Restrições técnicas

- Stack: Java 25, Maven com wrapper, JDA 6 com DAVE via JDAVE para voz, LavaPlayer com
  youtube-source para extração, yt-dlp como resolvedor preferido do YouTube quando disponível.
- Sem banco de dados: a fila vive em memória, isolada por guild. Trocar o armazenamento MUST ser
  um novo adaptador de `MusicQueueRepository`.
- Playlists MUST ser limitadas a 100 faixas por pedido, com o excedente informado.
- Dependências frágeis (youtube-source, yt-dlp, JDAVE) MUST ser atualizáveis sem alterar domínio,
  portas ou serviços; quebra da fonte externa MUST virar mensagem amigável.
- O áudio enviado ao Discord MUST ser Opus 48 kHz estéreo em frames de 20 ms, com passthrough
  quando a fonte já for Opus.

## Fluxo de desenvolvimento e gates

- `./mvnw verify` MUST ser rodado antes de todo commit que altere comportamento.
- Hooks versionados em `.githooks` MUST estar ativos (`core.hooksPath`); o `pre-push` executa
  gitleaks nos commits enviados.
- O CI MUST rodar testes com JaCoCo em pull requests e adicionar PITest nos pushes para `main`.
- Commits MUST ser atômicos e descrever o comportamento alterado; mudanças de dependência frágil
  MUST registrar a versão anterior e a nova.
- Toda decisão de design relevante MUST ser registrada em `specs/` (research, plan ou contracts)
  antes de virar código.

## Governance

Esta constituição prevalece sobre práticas anteriores. Emendas MUST ser propostas em pull request
com justificativa e plano de migração quando houver impacto em trabalho existente. A versão segue
semver: MAJOR para remoção ou redefinição de princípio, MINOR para novo princípio ou orientação
materialmente ampliada, PATCH para clareza e correção sem mudança semântica. Toda revisão MUST
verificar conformidade com os princípios; desvios MUST ser justificados no Complexity Tracking do
plano da feature ou resolvidos antes do merge. A data da última emenda MUST ser atualizada aqui.

**Version**: 1.0.0 | **Ratified**: 2026-09-12 | **Last Amended**: 2026-09-12

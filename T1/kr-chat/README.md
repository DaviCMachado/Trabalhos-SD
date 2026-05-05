# kr-chat

Implementação do Trabalho 1 de Sistemas Distribuídos: chat em Java usando gRPC e Protocol Buffers. O projeto segue o contrato definido em `src/main/proto/contrato-chat.proto`.

## Resumo

Aplicação de chat com um servidor central que mantém uma única sala. Usuários registram-se com nomes únicos, enviam mensagens que são transmitidas a todos os participantes, e recebem mensagens por meio de um stream contínuo.

## Requisitos

- Java 17
- Maven 3.8+

## Build

Compile o projeto com:

```bash
mvn compile
```

O plugin protobuf do Maven gera automaticamente as classes Java a partir de `src/main/proto/contrato-chat.proto`.

## Execução

Iniciar o servidor:

```bash
mvn "exec:java" "-Dexec.mainClass=kiri.chat.Main" "-Dexec.args=server 50051"
```

Executar um cliente (ex.: `alice`):

```bash
mvn "exec:java" "-Dexec.mainClass=kiri.chat.Main" "-Dexec.args=client alice localhost 50051"
```

## Uso

- Modo: `server` ou `client`.
- No cliente, o primeiro argumento após `client` é o `username`.
- Comandos suportados:
   - `/exit` : encerra a sessão do cliente (gera notificação de saída para os demais).
   - `/list` : exibe usuários online (retorna mensagem privada do servidor).

## Testes automatizados

Os testes unitários estão em `src/test/java/kiri/chat` e cobrem os cenários principais (registro, envio, stream, notificações e validações adicionadas de timestamp e ordem por remetente).

Executar:

```bash
mvn test
```

## Pipeline de Validação das RFAs

Apresentamos comandos para validar cada Requisito Funcional Absoluto (RFA) de forma objetiva.

Passo 0 — Preparação

```bash
# Compilar e gerar fontes protobuf
mvn compile

# Executar todos os testes automatizados (cobertura principal)
mvn test
```

RFA01 — Registro com nome único

Automático:
```bash
mvn -Dtest=ChatServiceImplTest#registerRejectsDuplicateUsers test
```

Manual (alternativa):
1. Iniciar servidor:
```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main server 50051
```
2. Abrir dois clientes em terminais distintos:
```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client alice localhost 50051
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client alice localhost 50051
```
Resultado esperado: o segundo registro deve falhar.

RFA02 — Uma única sala (escopo global)

Manual: iniciar servidor e conectar 3 clientes; enviar mensagem de um cliente e confirmar que todos os demais recebem a mesma mensagem.

RFA03 — Broadcast de mensagens

Automático:
```bash
mvn -Dtest=ChatServiceImplTest#receiveMessagesStreamsBroadcastsForRegisteredUser test
```

Manual: use o mesmo fluxo do RFA02 e valide que mensagens de um remetente são recebidas pelos outros.

RFA04 — Timestamp em `ChatMessage`

Automático:
```bash
mvn -Dtest=ChatServiceImplTest#chatMessageContainsTimestamp test
```

Manual: enviar mensagem e verificar que a mensagem recebida contém um `timestamp` válido.

RFA05 — Stream contínuo (entrega em tempo real)

Automático: coberto pelo mesmo teste de broadcast
```bash
mvn -Dtest=ChatServiceImplTest#receiveMessagesStreamsBroadcastsForRegisteredUser test
```

RFA06 — Ordem por remetente preservada

Automático:
```bash
mvn -Dtest=ChatServiceImplTest#messagesPreserveOrderBySender test
```

Manual: enviar uma sequência de mensagens de um remetente e confirmar que os receptores recebem na mesma ordem.

RFA07 — Notificações `SYSTEM` de entrada/saída

Automático:
```bash
mvn -Dtest=ChatRoomTest#leaveBroadcastsToRemainingSessions test
```

Manual: conectar e desconectar clientes; validar mensagens `SYSTEM` anunciando entrada e saída.


## Cobertura de RFAs (Requisitos Funcionais Absolutos)

| RFA | Descrição | Teste Automático | Teste Manual |
|-----|-----------|-----------------|--------------|
| **RFA01** | Registro com nome único; rejeita duplicatas | `ChatServiceImplTest.registerRejectsDuplicateUsers` | Tentar conectar dois clientes com o mesmo username |
| **RFA02** | Uma única sala criada pelo servidor | Implícito nos testes | Todos os clientes veem as mesmas mensagens |
| **RFA03** | Mensagens encaminhadas a todos os usuários | `ChatServiceImplTest.receiveMessagesStreamsBroadcastsForRegisteredUser`<br>`ChatRoomTest.leaveBroadcastsToRemainingSessions` | Enviar mensagem de um cliente e validar recepção nos demais |
| **RFA04** | `ChatMessage` com `from`, `content`, `timestamp` | `ChatServiceImplTest.chatMessageContainsTimestamp` | Verificar que cada mensagem contém timestamp válido |
| **RFA05** | Stream contínuo para entrega em tempo real | `ChatServiceImplTest.receiveMessagesStreamsBroadcastsForRegisteredUser` | Mensagens chegam enquanto o cliente está conectado |
| **RFA06** | Ordem por remetente preservada | `ChatServiceImplTest.messagesPreserveOrderBySender` | Enviar sequência de mensagens por um remetente e validar ordem de chegada |
| **RFA07** | Notificações de entrada/saída (SYSTEM) | `ChatRoomTest.leaveBroadcastsToRemainingSessions` | Conectar/Desconectar e validar mensagens `SYSTEM` |

## Comportamento implementado

- Registro de usuários com nome único via RPC `Register`.
- Envio de mensagens via RPC `SendMessage`.
- Recebimento de mensagens por stream em `ReceiveMessages`.
- Notificações de entrada e saída da sala enviadas como mensagens `SYSTEM`.
- Resposta a comandos `/list` e `/exit`.
- `ChatMessage` inclui `timestamp` e a implementação preserva ordem por remetente.

## Observações

- Os arquivos gerados a partir de `.proto` ficam em `target/generated-sources` e não devem ser editados manualmente.
- Para qualquer ajuste ou demonstração adicional, os testes em `src/test/java` fornecem exemplos de uso da API.

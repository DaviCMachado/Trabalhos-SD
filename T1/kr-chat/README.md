# kr-chat

Implementação do Trabalho 1 de Sistemas Distribuídos: um chat em Java com gRPC e Protocol Buffers, seguindo o contrato definido em `src/main/proto/contrato-chat.proto`.

## Requisitos

- Java 17
- Maven 3.8+

## Build

```bash
mvn compile
```

O Maven gera automaticamente as classes Java a partir do arquivo `.proto` durante a compilação.

## Execução

Inicie o servidor:

```bash
mvn exec:java -Dexec.mainClass="kiri.chat.Main" -Dexec.args="server 50051"
```

Ou, se preferir executar a classe principal diretamente após compilar:

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main server 50051
```

Inicie um cliente:

```bash
mvn exec:java -Dexec.mainClass="kiri.chat.Main" -Dexec.args="client alice localhost 50051"
```

Ou diretamente:

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client alice localhost 50051
```

## Uso

- O primeiro argumento define o modo: `server` ou `client`.
- No cliente, o primeiro parâmetro após `client` é o `username`.
- Use `/exit` para encerrar a sessão do cliente.

## Roteiro rapido de teste

1. Rode os testes automatizados:

```bash
mvn test
```

2. Inicie o servidor em um terminal:

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main server 50051
```

3. Inicie dois clientes em terminais separados:

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client alice localhost 50051
```

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client bob localhost 50051
```

4. Validacoes esperadas:
- Bob deve receber mensagem `SYSTEM` informando que entrou na sala.
- Mensagens enviadas por Alice devem aparecer para Bob (e vice-versa).
- Se tentar abrir outro cliente com username repetido, o registro deve falhar.
- Ao usar `/exit` em um cliente, o outro deve receber mensagem `SYSTEM` de saida.

## Script de apresentacao (2 minutos)

1. Fala inicial (10s):
- "Este projeto implementa um chat distribuido com gRPC e Protocol Buffers, com registro unico de usuarios e recebimento por stream."

2. Validacao automatizada (15s):

```bash
mvn test
```

- "Aqui eu valido os cenarios principais automaticamente: registro, envio e stream."

3. Subir servidor (15s):

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main server 50051
```

- "Servidor iniciado na porta 50051."

4. Abrir cliente 1 (15s):

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client alice localhost 50051
```

- "Alice entrou no chat."

5. Abrir cliente 2 (15s):

```bash
java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client bob localhost 50051
```

- "Bob entrou e o sistema notifica entrada por mensagem SYSTEM."

6. Troca de mensagens (30s):
- No terminal da Alice, enviar: `oi bob`.
- Mostrar no terminal do Bob a mensagem recebida.
- No terminal do Bob, responder: `oi alice`.
- Mostrar no terminal da Alice a resposta.

7. Regra de nome unico (10s):
- Tentar abrir um terceiro cliente com `alice`.
- "O registro falha porque o username precisa ser unico."

8. Encerramento limpo (10s):
- Em um cliente, executar `/exit`.
- "O outro cliente recebe mensagem SYSTEM de saida."

9. Fechamento (10s):
- "Fluxo completo validado: registro, envio, stream, notificacoes de sistema e saida limpa."

## Cola de prova (8 linhas)

1. `mvn test` -> "Valida registro, envio e stream automaticamente."
2. `java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main server 50051` -> "Servidor no ar."
3. `java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client alice localhost 50051` -> "Alice conectada."
4. `java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main client bob localhost 50051` -> "Bob conectou e recebeu SYSTEM."
5. Alice envia `oi bob` -> "Bob recebe no stream."
6. Bob responde `oi alice` -> "Alice recebe no stream."
7. Tentar cliente com username `alice` de novo -> "Registro rejeitado (nome unico)."
8. Em um cliente, `/exit` -> "Outro cliente recebe SYSTEM de saida."

## Comportamento implementado

- Registro de usuários com nome único via chamada unary `Register`.
- Envio de mensagens via `SendMessage`.
- Recebimento de mensagens por stream em `ReceiveMessages`.
- Notificações de entrada e saída da sala enviadas como mensagens `SYSTEM`.

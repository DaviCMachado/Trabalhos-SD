---
description: "Instrucoes de teste para o chat gRPC kr-chat"
applyTo: "**/src/test/java/**/*,**/pom.xml"
---

# Instrucoes de teste

- Sempre executar `mvn test` antes de validar qualquer mudanca no chat.
- Se a mudanca afetar geracao de codigo, executar tambem `mvn compile`.
- Para teste manual, subir o servidor com `java -cp target/classes;target/generated-sources/protobuf/java;target/generated-sources/protobuf/grpc kiri.chat.Main server 50051`.
- Abrir pelo menos dois clientes com usuarios diferentes para validar entrada, envio e recepcao de mensagens.
- Confirmar que nomes duplicados sao rejeitados.
- Confirmar que mensagens `SYSTEM` aparecem quando um usuario entra ou sai da sala.
- Usar `/exit` no cliente para encerrar a sessao de forma limpa.

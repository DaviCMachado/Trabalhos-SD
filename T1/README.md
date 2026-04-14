# O trabalho 1 consiste em estudar e implementar em Java uma aplicação distribuída de Chat com gRPC e Protobuf

Especificação do Trabalho I – ELC1018 Sistemas Distribuídos

Programação de uma aplicação Chat utilizando Java, gRPC e Protobuff

Trabalho DEVE ser realizado em dupla

O trabalho consiste em implementar uma aplicação distribuía de Chat com suporte a uma única sala (room do servidor) utilizando as seguintes tecnologias: linguagem Java, comunicação gRPC com “contrato” via Protocol Buffers.

A aplicação deve conter dois tipos de processos: Servidor de Chat (ServerChat) e Cliente de Chat (ClientChat).

O projeto da aplicação deve garantir que:
• sejam suportados múltiplos usuários em uma única sala
• clientes e servidores devem comunicar-se exclusivamente via gRPC
• a solução respeite rigorosamente um contrato definido no arquivo contrato-chat.proto
• sejam explorados diferentes modelos de comunicação (unary + streaming)
• haja interoperabilidade entre implementações independentes

Todos os alunos devem utilizar o arquivo .proto, fornecido. Qualquer alteração implicará em nota zero.

Requisitos funcionais Absolutos:

RFA01 – Um usuário deve se registrar com um nome único (username) através de uma chamada de procedimento remoto Register(User) returns (RegisterResponse). A resposta deve indicar sucesso (success = 1) ou erro (success = 0) e o nome do usuário. Nomes de usuário replicados devem ser rejeitados (erro).

RFA02 – A sala deve ser criada pelo servidor, devendo haver uma única que aceita múltiplos usuários.

RFA03 – As mensagens dos usuários devem ser encaminhadas a todos os usuários da sala via de uma chamada de procedimento remoto SendMessage(ChatMessage) returns (Ack).

RFA04 – Cada mensagem ChatMessage deve conter remetente from, conteúdo content. e informação de data/horário timestamp.

RFA05 – Para receber mensagens cada usuário deve manter uma conexão ativa (stream) com o servidor através de ReceiveMessages(User) returns (stream ChatMessage).

RFA06 – As mensagens recebidas devem preservar a ordem por remetente (ordem de envio do emissor).

RFA07 – A aplicação deve emitir eventos de notificação (envio de ChatMessage) a todos os membros quando um usuário ingressa ou deixa a sala.

Restrições:
não é permitido usar sockets diretamente;
não é permitido alterar o .proto;
não é permitido usar REST/HTTP alternativo;
não é permitido usar bibliotecas externas de chat.

Referências com materiais de ajuda:
protobuf.dev/getting-started/javatutorial/
protobuf.dev/programming-guides/proto3/

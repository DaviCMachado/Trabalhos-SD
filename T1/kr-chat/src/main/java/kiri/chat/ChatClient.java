package kiri.chat;

import elc1018.grpc.chat.protos.Ack;
import elc1018.grpc.chat.protos.ChatMessage;
import elc1018.grpc.chat.protos.ChatServiceGrpc;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

public class ChatClient {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: client <username> [host] [port]");
            return;
        }

        String username = args[0];
        String host = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 50051;

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            ChatServiceGrpc.ChatServiceBlockingStub blockingStub = ChatServiceGrpc.newBlockingStub(channel);
            RegisterResponse registerResponse = blockingStub.register(User.newBuilder().setUsername(username).build());
            if (!registerResponse.getSuccess()) {
                System.out.println("Nome de usuário já está em uso ou é inválido.");
                return;
            }

            ChatServiceGrpc.ChatServiceStub asyncStub = ChatServiceGrpc.newStub(channel);
            CountDownLatch done = new CountDownLatch(1);

            StreamObserver<ChatMessage> receiveObserver = new StreamObserver<>() {
                @Override
                public void onNext(ChatMessage value) {
                    System.out.println(format(value));
                }

                @Override
                public void onError(Throwable t) {
                    System.out.println("Conexão encerrada: " + t.getMessage());
                    done.countDown();
                }

                @Override
                public void onCompleted() {
                    done.countDown();
                }
            };

            asyncStub.receiveMessages(User.newBuilder().setUsername(username).build(), receiveObserver);
            System.out.println("Conectado como " + username + ". Digite mensagens e pressione Enter. Use /exit para sair.");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null || "/exit".equalsIgnoreCase(line.trim())) {
                        break;
                    }

                    Ack ack = blockingStub.sendMessage(ChatMessage.newBuilder()
                            .setFrom(username)
                            .setContent(line)
                            .setTimestamp(toTimestamp(Instant.now()))
                            .build());

                    if (!ack.getSuccess()) {
                        System.out.println("Mensagem rejeitada pelo servidor.");
                    }
                }
            }
        } catch (IOException exception) {
            throw new RuntimeException("Falha na leitura da entrada", exception);
        } finally {
            channel.shutdownNow();
        }
    }

    private static String format(ChatMessage message) {
        return "[" + message.getTimestamp().getSeconds() + "] " + message.getFrom() + ": " + message.getContent();
    }

    private static com.google.protobuf.Timestamp toTimestamp(Instant instant) {
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
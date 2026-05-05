package kiri.chat;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ChatServer {
    public static void main(String[] args) {
    // Porta configurável para permitir execução local ou em ambiente remoto.
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 50051;

    // O servidor expõe apenas a implementação do serviço de chat.
        Server server = ServerBuilder.forPort(port)
                .addService(new ChatServiceImpl())
                .build();

    // Garante encerramento limpo quando a JVM finalizar.
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        try {
            server.start();
            System.out.println("Servidor de chat gRPC iniciado na porta " + port);
            server.awaitTermination();
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao iniciar o servidor", exception);
        }
    }
}
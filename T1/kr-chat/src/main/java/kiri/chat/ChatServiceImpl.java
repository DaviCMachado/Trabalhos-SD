package kiri.chat;

import elc1018.grpc.chat.protos.Ack;
import elc1018.grpc.chat.protos.ChatMessage;
import elc1018.grpc.chat.protos.ChatServiceGrpc;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    // Sala compartilhada e conjunto de usuários registrados.
    private final ChatRoom chatRoom = new ChatRoom();
    private final Set<String> registeredUsers = ConcurrentHashMap.newKeySet();

    @Override
    public void register(User request, StreamObserver<RegisterResponse> responseObserver) {
        // Nome vazio ou repetido não pode ser registrado.
        String username = request.getUsername().trim();
        boolean success = username != null && !username.isBlank() && registeredUsers.add(username);

        responseObserver.onNext(RegisterResponse.newBuilder()
                .setSuccess(success)
                .setUsername(username)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendMessage(ChatMessage request, StreamObserver<Ack> responseObserver) {
        // Apenas usuários registrados podem enviar mensagens.
        boolean knownUser = registeredUsers.contains(request.getFrom());
        if(knownUser){
            // Comandos especiais são tratados antes do broadcast normal.
            if("/list".equalsIgnoreCase(request.getContent().trim())){
                String lista = "Usuários online: " + String.join(", ", chatRoom.getOnlineUsers());
                chatRoom.sendPrivateMessage(request.getFrom().trim(), "Sistema", lista);
            }else if("/exit".equalsIgnoreCase(request.getContent().trim())){
                // /exit remove o usuário da sala e encerra sua presença no servidor.
                chatRoom.leave(request.getFrom().trim());
                registeredUsers.remove(request.getFrom().trim());
            } else {
                // Mensagens comuns seguem para todos os participantes.
                chatRoom.broadcastUserMessage(request.getFrom().trim(), request.getContent().trim());
            }
        }

        responseObserver.onNext(Ack.newBuilder().setSuccess(knownUser).build());
        responseObserver.onCompleted();
    }

    @Override
    public void receiveMessages(User request, StreamObserver<ChatMessage> responseObserver) {
        // O stream só é aberto para usuários previamente registrados.
        String username = request.getUsername().trim();
        if (!registeredUsers.contains(username)) {
            responseObserver.onError(new IllegalArgumentException("User not registered: " + username));
            return;
        }

        ServerCallStreamObserver<ChatMessage> serverObserver = (ServerCallStreamObserver<ChatMessage>) responseObserver;
        CountDownLatch disconnected = new CountDownLatch(1);

        // Ao conectar, o usuário entra na sala e passa a receber mensagens em tempo real.
        chatRoom.join(username, responseObserver);
        serverObserver.setOnCancelHandler(() -> {
            // O handler libera a thread quando o cliente encerra a conexão.
            disconnected.countDown();
        });

        try {
            disconnected.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            // Limpeza final para evitar usuários zumbis na lista de registrados.
            registeredUsers.remove(username); 
            chatRoom.leave(username);
        }
    }
}
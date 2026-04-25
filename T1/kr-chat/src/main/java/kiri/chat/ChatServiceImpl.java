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
    private final ChatRoom chatRoom = new ChatRoom();
    private final Set<String> registeredUsers = ConcurrentHashMap.newKeySet();

    @Override
    public void register(User request, StreamObserver<RegisterResponse> responseObserver) {
        String username = request.getUsername();
        boolean success = username != null && !username.isBlank() && registeredUsers.add(username);

        responseObserver.onNext(RegisterResponse.newBuilder()
                .setSuccess(success)
                .setUsername(username)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void sendMessage(ChatMessage request, StreamObserver<Ack> responseObserver) {
        boolean knownUser = registeredUsers.contains(request.getFrom());
        if (knownUser) {
            chatRoom.broadcastUserMessage(request.getFrom(), request.getContent());
        }

        responseObserver.onNext(Ack.newBuilder().setSuccess(knownUser).build());
        responseObserver.onCompleted();
    }

    @Override
    public void receiveMessages(User request, StreamObserver<ChatMessage> responseObserver) {
        String username = request.getUsername();
        if (!registeredUsers.contains(username)) {
            responseObserver.onError(new IllegalArgumentException("User not registered: " + username));
            return;
        }

        ServerCallStreamObserver<ChatMessage> serverObserver = (ServerCallStreamObserver<ChatMessage>) responseObserver;
        CountDownLatch disconnected = new CountDownLatch(1);

        chatRoom.join(username, responseObserver);
        serverObserver.setOnCancelHandler(() -> {
            chatRoom.leave(username);
            disconnected.countDown();
        });

        try {
            disconnected.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            chatRoom.leave(username);
        }
    }
}
package kiri.chat;

import elc1018.grpc.chat.protos.ChatMessage;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatRoom {
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public synchronized void join(String username, StreamObserver<ChatMessage> responseObserver) {
        ClientSession session = new ClientSession(username, responseObserver);
        sessions.put(username, session);
        session.start();
        broadcast(systemMessage(username + " entrou na sala"));
    }

    public void broadcastUserMessage(String from, String content) {
        broadcast(new ChatMessageEnvelope(from, content, Instant.now()));
    }

    public synchronized void leave(String username) {
        ClientSession session = sessions.remove(username);
        if (session != null) {
            session.stop();
            broadcast(systemMessage(username + " saiu da sala"));
        }
    }

    private void broadcast(ChatMessageEnvelope envelope) {
        ChatMessage message = toProto(envelope);
        for (ClientSession session : sessions.values()) {
            session.enqueue(message);
        }
    }

    private ChatMessageEnvelope systemMessage(String content) {
        return new ChatMessageEnvelope("SYSTEM", content, Instant.now());
    }

    private ChatMessage toProto(ChatMessageEnvelope envelope) {
        return ChatMessage.newBuilder()
                .setFrom(envelope.from())
                .setContent(envelope.content())
                .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(envelope.timestamp().getEpochSecond())
                        .setNanos(envelope.timestamp().getNano())
                        .build())
                .build();
    }

    private static final class ClientSession {
        private final StreamObserver<ChatMessage> observer;
        private final BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();
        private final Thread senderThread;
        private volatile boolean active = true;

        private ClientSession(String username, StreamObserver<ChatMessage> observer) {
            this.observer = observer;
            this.senderThread = new Thread(this::run, "chat-stream-" + username);
            this.senderThread.setDaemon(true);
        }

        private void start() {
            senderThread.start();
        }

        private void enqueue(ChatMessage message) {
            if (active) {
                queue.offer(message);
            }
        }

        private void stop() {
            active = false;
            senderThread.interrupt();
        }

        private void run() {
            try {
                while (active || !queue.isEmpty()) {
                    ChatMessage message = queue.poll();
                    if (message == null) {
                        Thread.sleep(25L);
                        continue;
                    }
                    observer.onNext(message);
                }
                observer.onCompleted();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                observer.onCompleted();
            } catch (Exception exception) {
                observer.onError(exception);
            }
        }
    }
}
package kiri.chat;

import elc1018.grpc.chat.protos.ChatMessage;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.Set;


public class ChatRoom {
    // Estado central da única sala: usuário -> sessão ativa.
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public synchronized void join(String username, StreamObserver<ChatMessage> responseObserver) {
        // Cada usuário ganha uma sessão própria para receber o stream de mensagens.
        ClientSession session = new ClientSession(username, responseObserver);
        sessions.put(username, session);
        session.start();
        broadcast(systemMessage(username + " entrou na sala"));
    }

    public void broadcastUserMessage(String from, String content) {
        // Mensagens normais são enviadas para todos os usuários conectados.
        broadcast(new ChatMessageEnvelope(from, content, Instant.now()));
    }

    public void sendPrivateMessage(String to, String from, String content) {
        // Canal auxiliar para respostas pontuais, como o comando /list.
        ClientSession session = sessions.get(to);
        if (session != null) {
            session.enqueue(toProto(new ChatMessageEnvelope(from, content, Instant.now())));
        }
    }

    public synchronized void leave(String username) {
        // Remove a sessão e notifica os demais usuários sobre a saída.
        ClientSession session = sessions.remove(username);
        if (session != null) {
            session.stop();
            broadcast(systemMessage(username + " saiu da sala"));
        }
    }

    public Set<String> getOnlineUsers() {
        // Snapshot da lista de usuários online para consultas como /list.
        return Set.copyOf(sessions.keySet());
    }

    private void broadcast(ChatMessageEnvelope envelope) {
        // Reutiliza o mesmo envelope protobuf para todos os clientes conectados.
        ChatMessage message = toProto(envelope);
        for (ClientSession session : sessions.values()) {
            session.enqueue(message);
        }
    }

    private ChatMessageEnvelope systemMessage(String content) {
        // Mensagens do sistema identificam eventos de entrada e saída.
        return new ChatMessageEnvelope("SYSTEM", content, Instant.now());
    }

    private ChatMessage toProto(ChatMessageEnvelope envelope) {
        // Converte o envelope interno para o tipo gerado pelo protobuf.
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
        // Cada sessão tem sua própria fila para preservar a ordem de entrega.
        private final StreamObserver<ChatMessage> observer;
        private final BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();
        private final Thread senderThread;
        private volatile boolean active = true;

        private ClientSession(String username, StreamObserver<ChatMessage> observer) {
            this.observer = observer;
            // Thread dedicada evita bloquear o broadcast de outras sessões.
            this.senderThread = new Thread(this::run, "chat-stream-" + username);
            this.senderThread.setDaemon(true);
        }

        private void start() {
            senderThread.start();
        }

        private void enqueue(ChatMessage message) {
            // Se a sessão ainda estiver ativa, a mensagem entra na fila.
            if (active) {
                queue.offer(message);
            }
        }

        private void stop() {
            // O cancelamento fecha o loop de envio e interrompe a espera.
            active = false;
            senderThread.interrupt();
        }

        private void run() {
            try {
                while (active || !queue.isEmpty()) {
                    ChatMessage message = queue.poll();
                    if (message == null) {
                        // Pequena pausa para evitar espera ocupada quando não há mensagens.
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
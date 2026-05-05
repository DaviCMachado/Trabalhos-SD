package kiri.chat;

import elc1018.grpc.chat.protos.Ack;
import elc1018.grpc.chat.protos.ChatMessage;
import elc1018.grpc.chat.protos.RegisterResponse;
import elc1018.grpc.chat.protos.User;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceImplTest {

    @Test
    void registerRejectsDuplicateUsers() {
        ChatServiceImpl service = new ChatServiceImpl();

        // Primeiro registro deve ser aceito.
        RecordingUnaryObserver<RegisterResponse> first = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), first);

        // Segundo registro com o mesmo nome precisa ser rejeitado.
        RecordingUnaryObserver<RegisterResponse> second = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), second);

        assertTrue(first.awaitCompletion(Duration.ofSeconds(1)));
        assertTrue(second.awaitCompletion(Duration.ofSeconds(1)));
        assertTrue(first.value().getSuccess());
        assertFalse(second.value().getSuccess());
        assertEquals("alice", second.value().getUsername());
    }

    @Test
    void receiveMessagesStreamsBroadcastsForRegisteredUser() throws Exception {
        ChatServiceImpl service = new ChatServiceImpl();

        // Registra o usuário antes de abrir o stream de mensagens.
        RecordingUnaryObserver<RegisterResponse> registerObserver = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), registerObserver);
        assertTrue(registerObserver.awaitCompletion(Duration.ofSeconds(1)));
        assertTrue(registerObserver.value().getSuccess());

        // O stream permanece aberto enquanto o servidor envia mensagens.
        RecordingServerCallStreamObserver<ChatMessage> streamObserver = new RecordingServerCallStreamObserver<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread receiveThread = new Thread(() -> {
            try {
                service.receiveMessages(User.newBuilder().setUsername("alice").build(), streamObserver);
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "receive-test");

        receiveThread.start();

        assertTrue(streamObserver.awaitMessageCount(1, Duration.ofSeconds(2)));
        ChatMessage joinMessage = streamObserver.messages().get(0);
        assertEquals("SYSTEM", joinMessage.getFrom());
        assertTrue(joinMessage.getContent().contains("entrou"));

        RecordingUnaryObserver<Ack> ackObserver = new RecordingUnaryObserver<>();
        service.sendMessage(ChatMessage.newBuilder()
                .setFrom("alice")
                .setContent("oi")
                .build(), ackObserver);

        assertTrue(ackObserver.awaitCompletion(Duration.ofSeconds(1)));
        assertTrue(ackObserver.value().getSuccess());
        assertTrue(streamObserver.awaitMessageCount(2, Duration.ofSeconds(2)));

        ChatMessage userMessage = streamObserver.messages().get(1);
        assertEquals("alice", userMessage.getFrom());
        assertEquals("oi", userMessage.getContent());

        streamObserver.cancelFromClient();
        receiveThread.join(TimeUnit.SECONDS.toMillis(2));

        assertFalse(receiveThread.isAlive());
        assertNull(failure.get());
    }

    @Test
    void receiveMessagesRejectsUnknownUsers() {
        ChatServiceImpl service = new ChatServiceImpl();
        // Usuário inexistente deve gerar erro imediato no stream.
        RecordingUnaryObserver<ChatMessage> observer = new RecordingUnaryObserver<>();

        service.receiveMessages(User.newBuilder().setUsername("ghost").build(), observer);

        assertTrue(observer.awaitError(Duration.ofSeconds(1)));
        assertTrue(observer.error().getMessage().contains("User not registered"));
    }

    @Test
    void chatMessageContainsTimestamp() throws Exception {
        ChatServiceImpl service = new ChatServiceImpl();

        // Abre o stream para capturar a mensagem enviada ao usuário.
        RecordingUnaryObserver<RegisterResponse> registerObserver = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), registerObserver);
        assertTrue(registerObserver.awaitCompletion(Duration.ofSeconds(1)));

        // O teste valida que o servidor preenche o timestamp automaticamente.
        RecordingServerCallStreamObserver<ChatMessage> streamObserver = new RecordingServerCallStreamObserver<>();
        Thread receiveThread = new Thread(() -> {
            service.receiveMessages(User.newBuilder().setUsername("alice").build(), streamObserver);
        }, "receive-timestamp-test");
        receiveThread.start();

        assertTrue(streamObserver.awaitMessageCount(1, Duration.ofSeconds(2)));

        RecordingUnaryObserver<Ack> ackObserver = new RecordingUnaryObserver<>();
        service.sendMessage(ChatMessage.newBuilder()
                .setFrom("alice")
                .setContent("test message")
                .build(), ackObserver);

        assertTrue(ackObserver.awaitCompletion(Duration.ofSeconds(1)));
        assertTrue(streamObserver.awaitMessageCount(2, Duration.ofSeconds(2)));

        ChatMessage message = streamObserver.messages().get(1);
        assertTrue(message.hasTimestamp(), "ChatMessage deve conter timestamp preenchido");
        assertTrue(message.getTimestamp().getSeconds() > 0, "Timestamp deve ter segundos preenchidos");

        streamObserver.cancelFromClient();
        receiveThread.join(TimeUnit.SECONDS.toMillis(2));
    }

    @Test
    void messagesPreserveOrderBySender() throws Exception {
        ChatServiceImpl service = new ChatServiceImpl();

        // Alice entra primeiro para receber as próprias notificações de sistema.
        RecordingUnaryObserver<RegisterResponse> aliceReg = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), aliceReg);
        assertTrue(aliceReg.awaitCompletion(Duration.ofSeconds(1)));

        // Captura as mensagens recebidas por Alice durante o teste.
        RecordingServerCallStreamObserver<ChatMessage> aliceStream = new RecordingServerCallStreamObserver<>();
        Thread aliceReceiveThread = new Thread(() -> {
            service.receiveMessages(User.newBuilder().setUsername("alice").build(), aliceStream);
        }, "alice-receive");
        aliceReceiveThread.start();

        assertTrue(aliceStream.awaitMessageCount(1, Duration.ofSeconds(2)), 
                "Alice deve receber mensagem de entrada");

        // Bob entra depois para verificar a ordem de mensagens recebidas do mesmo remetente.
        RecordingUnaryObserver<RegisterResponse> bobReg = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("bob").build(), bobReg);
        assertTrue(bobReg.awaitCompletion(Duration.ofSeconds(1)));

        // Bob também usa um stream dedicado para validar a ordem de chegada.
        RecordingServerCallStreamObserver<ChatMessage> bobStream = new RecordingServerCallStreamObserver<>();
        Thread bobReceiveThread = new Thread(() -> {
            service.receiveMessages(User.newBuilder().setUsername("bob").build(), bobStream);
        }, "bob-receive");
        bobReceiveThread.start();

        assertTrue(bobStream.awaitMessageCount(1, Duration.ofSeconds(2)), 
                "Bob deve receber sua mensagem de entrada");
        assertTrue(aliceStream.awaitMessageCount(2, Duration.ofSeconds(2)), 
                "Alice deve receber a mensagem de entrada de Bob");

        for (int i = 1; i <= 3; i++) {
            RecordingUnaryObserver<Ack> ackObs = new RecordingUnaryObserver<>();
            service.sendMessage(ChatMessage.newBuilder()
                    .setFrom("alice")
                    .setContent("msg-" + i)
                    .build(), ackObs);
            assertTrue(ackObs.awaitCompletion(Duration.ofSeconds(1)));
            Thread.sleep(50);
        }

        assertTrue(bobStream.awaitMessageCount(4, Duration.ofSeconds(2)), 
                "Bob deve receber 4 mensagens (entrada dele + 3 de alice)");

        List<ChatMessage> bobMessages = bobStream.messages();
        assertEquals("msg-1", bobMessages.get(1).getContent(), "Primeira mensagem de Alice");
        assertEquals("msg-2", bobMessages.get(2).getContent(), "Segunda mensagem de Alice");
        assertEquals("msg-3", bobMessages.get(3).getContent(), "Terceira mensagem de Alice");

        bobStream.cancelFromClient();
        aliceStream.cancelFromClient();
        bobReceiveThread.join(TimeUnit.SECONDS.toMillis(2));
        aliceReceiveThread.join(TimeUnit.SECONDS.toMillis(2));
    }

    private static final class RecordingUnaryObserver<T> implements StreamObserver<T> {
        // Captura a resposta final do RPC unary.
        private final CountDownLatch done = new CountDownLatch(1);
        // Guarda o valor recebido em onNext para asserções posteriores.
        private final AtomicReference<T> value = new AtomicReference<>();
        // Guarda o erro, caso o RPC termine de forma excepcional.
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onNext(T value) {
            // Em chamadas unary, a resposta chega em um único onNext.
            this.value.set(value);
        }

        @Override
        public void onError(Throwable throwable) {
            // Erro libera a espera do teste.
            error.set(throwable);
            done.countDown();
        }

        @Override
        public void onCompleted() {
            // Sinaliza que o RPC terminou normalmente.
            done.countDown();
        }

        private boolean awaitCompletion(Duration timeout) {
            try {
                return done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private boolean awaitError(Duration timeout) {
            return awaitCompletion(timeout) && error.get() != null;
        }

        private T value() {
            return value.get();
        }

        private Throwable error() {
            return error.get();
        }
    }

    private static final class RecordingServerCallStreamObserver<T> extends ServerCallStreamObserver<T> {
        // Mantém todas as mensagens recebidas na ordem em que chegam.
        private final List<T> messages = new CopyOnWriteArrayList<>();
        // Permite simular cancelamento vindo do cliente.
        private final AtomicReference<Runnable> cancelHandler = new AtomicReference<>();
        // Registra falhas do stream, se acontecerem.
        private final AtomicReference<Throwable> terminalError = new AtomicReference<>();
        // Estado simples para indicar cancelamento no teste.
        private volatile boolean cancelled;

        @Override
        public void onNext(T value) {
            // Cada mensagem enviada pelo servidor é armazenada para validação.
            messages.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            // Qualquer erro no stream fica registrado para diagnóstico.
            terminalError.set(throwable);
        }

        @Override
        public void onCompleted() {
            // The service thread should return after cancellation.
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setOnCancelHandler(Runnable onCancelHandler) {
            // O serviço usa este handler para reagir ao cancelamento do cliente.
            cancelHandler.set(onCancelHandler);
        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {
            // Not needed.
        }

        @Override
        public boolean isReady() {
            // O teste trata o canal como sempre pronto para simplificar a execução.
            return true;
        }

        @Override
        public void request(int count) {
            // No backpressure simulation needed.
        }

        @Override
        public void setCompression(String compression) {
            // Not used.
        }

        @Override
        public void setMessageCompression(boolean enable) {
            // Not used.
        }

        @Override
        public void disableAutoInboundFlowControl() {
            // Not used.
        }

        private boolean awaitMessageCount(int expectedCount, Duration timeout) {
            // Aguarda até o número esperado de mensagens chegar no stream.
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (messages.size() >= expectedCount) {
                    return true;
                }

                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            return messages.size() >= expectedCount;
        }

        private List<T> messages() {
            // Retorna uma cópia para evitar mutação externa do histórico capturado.
            return new ArrayList<>(messages);
        }

        private void cancelFromClient() {
            // Simula o cliente encerrando a conexão e dispara o callback do serviço.
            cancelled = true;
            Runnable handler = cancelHandler.get();
            if (handler != null) {
                handler.run();
            }
        }

        @SuppressWarnings("unused")
        private Throwable terminalError() {
            return terminalError.get();
        }
    }
}
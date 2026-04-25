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

        RecordingUnaryObserver<RegisterResponse> first = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), first);

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

        RecordingUnaryObserver<RegisterResponse> registerObserver = new RecordingUnaryObserver<>();
        service.register(User.newBuilder().setUsername("alice").build(), registerObserver);
        assertTrue(registerObserver.awaitCompletion(Duration.ofSeconds(1)));
        assertTrue(registerObserver.value().getSuccess());

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
        RecordingUnaryObserver<ChatMessage> observer = new RecordingUnaryObserver<>();

        service.receiveMessages(User.newBuilder().setUsername("ghost").build(), observer);

        assertTrue(observer.awaitError(Duration.ofSeconds(1)));
        assertTrue(observer.error().getMessage().contains("User not registered"));
    }

    private static final class RecordingUnaryObserver<T> implements StreamObserver<T> {
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<T> value = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onNext(T value) {
            this.value.set(value);
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            done.countDown();
        }

        @Override
        public void onCompleted() {
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
        private final List<T> messages = new CopyOnWriteArrayList<>();
        private final AtomicReference<Runnable> cancelHandler = new AtomicReference<>();
        private final AtomicReference<Throwable> terminalError = new AtomicReference<>();
        private volatile boolean cancelled;

        @Override
        public void onNext(T value) {
            messages.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
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
            cancelHandler.set(onCancelHandler);
        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {
            // Not needed.
        }

        @Override
        public boolean isReady() {
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
            return new ArrayList<>(messages);
        }

        private void cancelFromClient() {
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
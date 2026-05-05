package kiri.chat;

import elc1018.grpc.chat.protos.ChatMessage;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRoomTest {

    @Test
    void leaveBroadcastsToRemainingSessions() {
        ChatRoom room = new ChatRoom();
        RecordingObserver alice = new RecordingObserver();
        RecordingObserver bob = new RecordingObserver();

        // Dois usuários entram na sala para validar entrada, broadcast e saída.
        room.join("alice", alice);
        room.join("bob", bob);

        // A segunda entrada deve gerar notificação SYSTEM para os usuários conectados.
        assertTrue(bob.awaitMessageCount(1, Duration.ofSeconds(2)));
        assertEquals("SYSTEM", bob.messages().get(0).getFrom());
        assertTrue(bob.messages().get(0).getContent().contains("bob entrou"));

        // Mensagem normal do usuário precisa chegar aos demais membros da sala.
        room.broadcastUserMessage("alice", "hello");
        assertTrue(bob.awaitMessageCount(2, Duration.ofSeconds(2)));
        assertEquals("alice", bob.messages().get(1).getFrom());
        assertEquals("hello", bob.messages().get(1).getContent());

        // Ao sair, o servidor deve notificar os participantes restantes.
        room.leave("alice");
        assertTrue(bob.awaitMessageCount(3, Duration.ofSeconds(2)));
        assertEquals("SYSTEM", bob.messages().get(2).getFrom());
        assertTrue(bob.messages().get(2).getContent().contains("alice saiu"));

        // Encerramento da última sessão para liberar os observers do teste.
        room.leave("bob");
        assertTrue(bob.awaitCompletion(Duration.ofSeconds(2)));
        assertTrue(alice.awaitCompletion(Duration.ofSeconds(2)));
    }

    private static final class RecordingObserver implements StreamObserver<ChatMessage> {
        // Armazena todas as mensagens recebidas para permitir asserções depois.
        private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
        // Sinaliza quando o fluxo foi encerrado ou falhou.
        private final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void onNext(ChatMessage value) {
            // Cada mensagem recebida é registrada na ordem de chegada.
            messages.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
            // Erro também encerra a espera do teste.
            completed.countDown();
        }

        @Override
        public void onCompleted() {
            // O fluxo terminou normalmente.
            completed.countDown();
        }

        private boolean awaitMessageCount(int expectedCount, Duration timeout) {
            // Aguarda de forma simples até o número esperado de mensagens chegar.
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

        private boolean awaitCompletion(Duration timeout) {
            // Garante que o fluxo foi encerrado antes de finalizar o teste.
            try {
                return completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private List<ChatMessage> messages() {
            return new ArrayList<>(messages);
        }
    }
}
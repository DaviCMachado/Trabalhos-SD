package kiri.chat;

import java.time.Instant;

// Envelope interno usado para transportar mensagem e timestamp antes da conversão para protobuf.
public record ChatMessageEnvelope(String from, String content, Instant timestamp) {
}
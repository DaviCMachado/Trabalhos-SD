package kiri.chat;

import java.time.Instant;

public record ChatMessageEnvelope(String from, String content, Instant timestamp) {
}
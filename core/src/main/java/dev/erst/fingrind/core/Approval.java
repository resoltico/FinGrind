package dev.erst.fingrind.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Canonical approval fact attached to an evidence bundle. */
public record Approval(
    ActorId approverId,
    ApprovalStatus approvalStatus,
    Optional<Instant> decidedAt,
    Optional<String> note) {
  /** Validates one approval fact. */
  public Approval {
    Objects.requireNonNull(approverId, "approverId");
    Objects.requireNonNull(approvalStatus, "approvalStatus");
    Objects.requireNonNull(decidedAt, "decidedAt");
    Objects.requireNonNull(note, "note");
    note =
        note.map(
            value -> {
              String normalized = value.strip();
              if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Approval note must not be blank when present.");
              }
              if (normalized.length() > 512) {
                throw new IllegalArgumentException("Approval note must not exceed 512 characters.");
              }
              return normalized;
            });
  }
}

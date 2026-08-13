package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * In-process capability for writing the private stages owned by one prepared publication journal.
 *
 * <p>The transaction ID is the sole recovery authority. A stage path supplied here is only a
 * short-lived producer destination; it must never be rendered, persisted by a caller, or reused as
 * a cleanup handle.
 */
public final class PublicationTransactionStageReservation {
  private final PublicationTransactionId transactionId;
  private final Map<String, Path> stagePaths;

  PublicationTransactionStageReservation(PublicationTransactionJournal journal) {
    PublicationTransactionJournal checkedJournal = Objects.requireNonNull(journal, "journal");
    this.transactionId = checkedJournal.transactionId();
    this.stagePaths =
        checkedJournal.members().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    PublicationTransactionMember::memberId,
                    PublicationTransactionMember::stagePath));
  }

  /** Returns the authenticated journal identifier that exclusively authorizes recovery. */
  public PublicationTransactionId transactionId() {
    return transactionId;
  }

  /**
   * Returns the exact private stage destination reserved for the named member producer.
   *
   * <p>The path is valid only while the journal remains prepared and only for producing this
   * transaction's member bytes. It is not an artifact result or a deletion capability.
   */
  public Path stagePath(String memberId) {
    Path stagePath = stagePaths.get(Objects.requireNonNull(memberId, "memberId"));
    if (stagePath == null) {
      throw new IllegalArgumentException(
          "Publication transaction has no member named " + memberId + ".");
    }
    return stagePath;
  }
}

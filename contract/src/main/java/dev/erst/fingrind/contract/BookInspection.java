package dev.erst.fingrind.contract;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Machine-readable compatibility and lifecycle snapshot for one selected book file. */
public record BookInspection(
    Status status,
    boolean initialized,
    boolean compatibleWithCurrentBinary,
    boolean canInitializeWithOpenBook,
    @Nullable Integer applicationId,
    @Nullable Integer detectedBookFormatVersion,
    int supportedBookFormatVersion,
    String migrationPolicy,
    @Nullable Instant initializedAt) {
  /** Stable lifecycle and compatibility state reported for one book file. */
  public enum Status {
    MISSING,
    BLANK_SQLITE,
    INITIALIZED,
    FOREIGN_SQLITE,
    UNSUPPORTED_FORMAT_VERSION,
    INCOMPLETE_FINGRIND
  }

  /** Validates one book-inspection snapshot. */
  public BookInspection {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(migrationPolicy, "migrationPolicy");
  }
}

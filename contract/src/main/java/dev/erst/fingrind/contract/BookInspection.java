package dev.erst.fingrind.contract;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Machine-readable compatibility and lifecycle snapshot for one selected book file. */
public sealed interface BookInspection
    permits BookInspection.Missing, BookInspection.Existing, BookInspection.Initialized {
  /** Stable lifecycle and compatibility state reported for one book file. */
  enum Status {
    MISSING,
    BLANK_SQLITE,
    INITIALIZED,
    FOREIGN_SQLITE,
    UNSUPPORTED_FORMAT_VERSION,
    INCOMPLETE_FINGRIND;

    /** Returns the stable public wire value for this book-inspection state. */
    public String wireValue() {
      return switch (this) {
        case MISSING -> "missing";
        case BLANK_SQLITE -> "blank-sqlite";
        case INITIALIZED -> "initialized";
        case FOREIGN_SQLITE -> "foreign-sqlite";
        case UNSUPPORTED_FORMAT_VERSION -> "unsupported-format-version";
        case INCOMPLETE_FINGRIND -> "incomplete-fingrind";
      };
    }

    /** Returns every stable public wire value in declaration order. */
    public static List<String> wireValues() {
      return Arrays.stream(values()).map(Status::wireValue).toList();
    }

    /** Parses one stable public wire value. */
    public static Status fromWireValue(String wireValue) {
      Objects.requireNonNull(wireValue, "wireValue");
      return Arrays.stream(values())
          .filter(value -> value.wireValue().equals(wireValue))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unsupported book state: " + wireValue));
    }

    /** Returns whether this status identifies an existing SQLite file with shared metadata. */
    boolean isExistingNonInitialized() {
      return switch (this) {
        case BLANK_SQLITE, FOREIGN_SQLITE, UNSUPPORTED_FORMAT_VERSION, INCOMPLETE_FINGRIND -> true;
        case MISSING, INITIALIZED -> false;
      };
    }

    /** Returns whether `open-book` may initialize an existing file in this state. */
    boolean canInitializeWithOpenBook() {
      return this == BLANK_SQLITE;
    }
  }

  /** Stable lifecycle state for this inspection snapshot. */
  Status status();

  /** Whether the selected book already carries the explicit FinGrind initialization marker. */
  default boolean initialized() {
    return false;
  }

  /** Whether the current binary can safely operate on the selected book as-is. */
  default boolean compatibleWithCurrentBinary() {
    return false;
  }

  /** Whether `open-book` can initialize the selected path in place. */
  boolean canInitializeWithOpenBook();

  /** Book format version supported by the current FinGrind binary. */
  int supportedBookFormatVersion();

  /** Migration policy that governs incompatible on-disk books. */
  BookMigrationPolicy migrationPolicy();

  private static void requireSupportedBookFormatVersion(int supportedBookFormatVersion) {
    if (supportedBookFormatVersion < 1) {
      throw new IllegalArgumentException("Supported book format version must be at least 1.");
    }
  }

  private static void requireDetectedBookMetadata(
      int applicationId, int detectedBookFormatVersion, int supportedBookFormatVersion) {
    requireSupportedBookFormatVersion(supportedBookFormatVersion);
    if (detectedBookFormatVersion < 0) {
      throw new IllegalArgumentException("Detected book format version must be non-negative.");
    }
    if (applicationId < 0) {
      throw new IllegalArgumentException("SQLite applicationId must be non-negative.");
    }
  }

  /** Inspection state for a missing book path. */
  record Missing(int supportedBookFormatVersion, BookMigrationPolicy migrationPolicy)
      implements BookInspection {
    /** Validates one missing-book inspection snapshot. */
    public Missing {
      requireSupportedBookFormatVersion(supportedBookFormatVersion);
      Objects.requireNonNull(migrationPolicy, "migrationPolicy");
    }

    @Override
    public Status status() {
      return Status.MISSING;
    }

    @Override
    public boolean canInitializeWithOpenBook() {
      return true;
    }
  }

  /** Shared inspection state for existing non-initialized or incompatible SQLite files. */
  record Existing(
      Status status,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      BookMigrationPolicy migrationPolicy)
      implements BookInspection {
    /** Validates one existing-book inspection snapshot. */
    public Existing {
      Objects.requireNonNull(status, "status");
      if (!status.isExistingNonInitialized()) {
        throw new IllegalArgumentException(
            "Existing book inspection status must be one of BLANK_SQLITE, FOREIGN_SQLITE, "
                + "UNSUPPORTED_FORMAT_VERSION, or INCOMPLETE_FINGRIND.");
      }
      requireDetectedBookMetadata(
          applicationId, detectedBookFormatVersion, supportedBookFormatVersion);
      Objects.requireNonNull(migrationPolicy, "migrationPolicy");
    }

    @Override
    public boolean canInitializeWithOpenBook() {
      return status.canInitializeWithOpenBook();
    }
  }

  /** Inspection state for a fully initialized FinGrind book. */
  record Initialized(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      BookMigrationPolicy migrationPolicy,
      Instant initializedAt)
      implements BookInspection {
    /** Validates one initialized-book inspection snapshot. */
    public Initialized {
      requireDetectedBookMetadata(
          applicationId, detectedBookFormatVersion, supportedBookFormatVersion);
      Objects.requireNonNull(migrationPolicy, "migrationPolicy");
      Objects.requireNonNull(initializedAt, "initializedAt");
    }

    @Override
    public Status status() {
      return Status.INITIALIZED;
    }

    @Override
    public boolean initialized() {
      return true;
    }

    @Override
    public boolean compatibleWithCurrentBinary() {
      return true;
    }

    @Override
    public boolean canInitializeWithOpenBook() {
      return false;
    }
  }
}

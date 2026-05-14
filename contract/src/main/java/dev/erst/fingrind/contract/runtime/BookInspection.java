package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.WireValue;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Machine-readable compatibility and lifecycle snapshot for one selected book file. */
public sealed interface BookInspection
    permits BookInspection.Missing, BookInspection.Existing, BookInspection.Initialized {
  /** Stable lifecycle and compatibility state reported for one book file. */
  enum Status implements WireValue {
    MISSING,
    BLANK_SQLITE,
    INITIALIZED,
    FOREIGN_SQLITE,
    UNSUPPORTED_FORMAT_VERSION,
    INCOMPLETE_FINGRIND;

    /** Returns the stable public wire value for this book-inspection state. */
    @Override
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
      return WireValue.wireValues(Status.class);
    }

    /** Parses one stable public wire value. */
    public static Status fromWireValue(String wireValue) {
      return WireValue.fromWireValue(Status.class, wireValue, "Unsupported book state");
    }

    /** Returns whether this status identifies an existing SQLite file with shared metadata. */
    boolean isExistingNonInitialized() {
      return switch (this) {
        case BLANK_SQLITE, FOREIGN_SQLITE, UNSUPPORTED_FORMAT_VERSION, INCOMPLETE_FINGRIND -> true;
        case MISSING, INITIALIZED -> false;
      };
    }

    /** Returns whether this status identifies a fully initialized FinGrind book. */
    public boolean initialized() {
      return this == INITIALIZED;
    }

    /** Returns whether the current FinGrind binary can operate on this state without migration. */
    public boolean compatibleWithCurrentBinary() {
      return this == INITIALIZED;
    }

    /** Returns whether `open-book` may initialize the selected path from this state. */
    public boolean canInitializeWithOpenBook() {
      return switch (this) {
        case MISSING, BLANK_SQLITE -> true;
        case INITIALIZED, FOREIGN_SQLITE, UNSUPPORTED_FORMAT_VERSION, INCOMPLETE_FINGRIND -> false;
      };
    }
  }

  /** Stable lifecycle state for this inspection snapshot. */
  Status status();

  /** Book format version supported by the current FinGrind binary. */
  int supportedBookFormatVersion();

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
  record Missing(int supportedBookFormatVersion) implements BookInspection {
    /** Validates one missing-book inspection snapshot. */
    public Missing {
      requireSupportedBookFormatVersion(supportedBookFormatVersion);
    }

    @Override
    public Status status() {
      return Status.MISSING;
    }
  }

  /** Shared inspection state for existing non-initialized or incompatible SQLite files. */
  record Existing(
      Status status,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion)
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
    }
  }

  /** Inspection state for a fully initialized FinGrind book. */
  record Initialized(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt,
      BookIdentity bookIdentity)
      implements BookInspection {
    /** Validates one initialized-book inspection snapshot. */
    public Initialized {
      requireDetectedBookMetadata(
          applicationId, detectedBookFormatVersion, supportedBookFormatVersion);
      Objects.requireNonNull(initializedAt, "initializedAt");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
    }

    @Override
    public Status status() {
      return Status.INITIALIZED;
    }
  }
}

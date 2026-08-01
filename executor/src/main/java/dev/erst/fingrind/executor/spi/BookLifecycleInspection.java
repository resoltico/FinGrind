package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.WireValue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Local lifecycle and compatibility snapshot for one selected book file. */
public sealed interface BookLifecycleInspection
    permits BookLifecycleInspection.Missing,
        BookLifecycleInspection.Existing,
        BookLifecycleInspection.Initialized {
  /** Stable local lifecycle and compatibility state for one inspected book file. */
  enum Status implements WireValue {
    MISSING("missing", false, false, false),
    BLANK_SQLITE("blank-sqlite", true, false, false),
    INITIALIZED("initialized", false, true, true),
    FOREIGN_SQLITE("foreign-sqlite", true, false, false),
    UNSUPPORTED_FORMAT_VERSION("unsupported-format-version", true, false, false),
    INCOMPLETE_FINGRIND("incomplete-fingrind", true, false, false);

    private final String wireValue;
    private final boolean existingNonInitialized;
    private final boolean initialized;
    private final boolean compatibleWithCurrentBinary;

    Status(
        String wireValue,
        boolean existingNonInitialized,
        boolean initialized,
        boolean compatibleWithCurrentBinary) {
      this.wireValue = wireValue;
      this.existingNonInitialized = existingNonInitialized;
      this.initialized = initialized;
      this.compatibleWithCurrentBinary = compatibleWithCurrentBinary;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }

    /** Returns every stable local wire value in declaration order. */
    public static List<String> wireValues() {
      return WireValue.wireValues(Status.class);
    }

    public boolean isExistingNonInitialized() {
      return existingNonInitialized;
    }

    /** Returns whether this status identifies a fully initialized FinGrind book. */
    public boolean initialized() {
      return initialized;
    }

    /** Returns whether the current FinGrind binary can operate on this state without migration. */
    public boolean compatibleWithCurrentBinary() {
      return compatibleWithCurrentBinary;
    }
  }

  /** Stable lifecycle state for this inspection snapshot. */
  Status status();

  /** Returns whether this snapshot identifies a fully initialized FinGrind book. */
  default boolean initialized() {
    return status().initialized();
  }

  /** Returns whether the current FinGrind binary can operate on this snapshot safely. */
  default boolean compatibleWithCurrentBinary() {
    return status().compatibleWithCurrentBinary();
  }

  /** Returns whether initialized-book workflows may proceed for the supplied inspection. */
  static boolean allowsInitializedWorkflowFor(BookLifecycleInspection inspection) {
    return Objects.requireNonNull(inspection, "inspection").allowsInitializedWorkflow();
  }

  /** Returns the initialized book identity or throws when the selected book is not initialized. */
  static BookIdentity requireInitializedBookIdentity(BookLifecycleInspection inspection) {
    return switch (Objects.requireNonNull(inspection, "inspection")) {
      case Initialized initialized -> initialized.bookIdentity();
      case Missing _ ->
          throw new IllegalStateException(
              "Book identity is unavailable because the book is missing.");
      case Existing existing -> {
        existing.allowsInitializedWorkflow();
        throw new IllegalStateException(
            "Book identity is unavailable for non-initialized book status "
                + existing.status().wireValue()
                + ".");
      }
    };
  }

  /** Returns whether initialized-book workflows may proceed for this inspection snapshot. */
  default boolean allowsInitializedWorkflow() {
    return switch (this) {
      case Initialized _ -> true;
      case Missing _ -> false;
      case Existing existing -> existingAllowsInitializedWorkflow(existing);
    };
  }

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
    if (applicationId < 0) {
      throw new IllegalArgumentException("SQLite applicationId must be non-negative.");
    }
    if (detectedBookFormatVersion < 0) {
      throw new IllegalArgumentException("Detected book format version must be non-negative.");
    }
  }

  private static boolean existingAllowsInitializedWorkflow(Existing existing) {
    Status status = existing.status();
    if (status == Status.BLANK_SQLITE) {
      return false;
    }
    if (status == Status.FOREIGN_SQLITE) {
      throw foreignBookFailure();
    }
    if (status == Status.UNSUPPORTED_FORMAT_VERSION) {
      throw unsupportedBookVersionFailure(
          existing.detectedBookFormatVersion(), existing.supportedBookFormatVersion());
    }
    throw incompleteBookFailure();
  }

  private static IllegalStateException foreignBookFailure() {
    return new IllegalStateException("The selected SQLite file is not a FinGrind book.");
  }

  private static IllegalStateException incompleteBookFailure() {
    return new IllegalStateException(
        "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.");
  }

  private static ContractFailureException unsupportedBookVersionFailure(
      int loadedUserVersion, int expectedBookVersion) {
    return new ContractFailureException(
        ContractErrors.unsupportedBookFormatVersionFailure(loadedUserVersion, expectedBookVersion));
  }

  /** Inspection state for a missing book path. */
  record Missing(int supportedBookFormatVersion) implements BookLifecycleInspection {
    /** Creates one missing-book inspection snapshot. */
    public Missing(int supportedBookFormatVersion) {
      requireSupportedBookFormatVersion(supportedBookFormatVersion);
      this.supportedBookFormatVersion = supportedBookFormatVersion;
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
      implements BookLifecycleInspection {
    /** Creates one existing-book inspection snapshot. */
    public Existing(
        Status status,
        int applicationId,
        int detectedBookFormatVersion,
        int supportedBookFormatVersion) {
      Objects.requireNonNull(status, "status");
      if (!status.isExistingNonInitialized()) {
        throw new IllegalArgumentException(
            "Existing book inspection status must be one of BLANK_SQLITE, FOREIGN_SQLITE, "
                + "UNSUPPORTED_FORMAT_VERSION, or INCOMPLETE_FINGRIND.");
      }
      requireDetectedBookMetadata(
          applicationId, detectedBookFormatVersion, supportedBookFormatVersion);
      this.status = status;
      this.applicationId = applicationId;
      this.detectedBookFormatVersion = detectedBookFormatVersion;
      this.supportedBookFormatVersion = supportedBookFormatVersion;
    }
  }

  /** Inspection state for a fully initialized FinGrind book. */
  record Initialized(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt,
      BookIdentity bookIdentity)
      implements BookLifecycleInspection {
    /** Creates one initialized-book inspection snapshot. */
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

    /** Returns the immutable effective-date boundary selected when this book was opened. */
    public LocalDate bookStartDate() {
      return bookIdentity.bookStartEffectiveDate();
    }
  }
}

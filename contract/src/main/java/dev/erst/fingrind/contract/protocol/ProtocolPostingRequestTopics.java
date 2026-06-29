package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Canonical command-topic selection for raw and typed posting-request surfaces. */
public final class ProtocolPostingRequestTopics {
  private ProtocolPostingRequestTopics() {}

  /** Returns whether the selected command accepts the full published entry-kind family. */
  public static boolean acceptsAnyEntryKind(OperationId operationId) {
    return Objects.requireNonNull(operationId, "operationId") == OperationId.PREFLIGHT_ENTRY;
  }

  /** Returns the exact entry kind required by one command topic, when the topic is narrowed. */
  public static Optional<BookkeepingEntryKind> requiredEntryKind(OperationId operationId) {
    return Optional.ofNullable(requiredEntryKindOrNull(operationId));
  }

  /** Returns the canonical scaffold entry kind for one request-file command topic. */
  public static BookkeepingEntryKind scaffoldEntryKind(OperationId operationId) {
    OperationId requiredOperationId = Objects.requireNonNull(operationId, "operationId");
    if (requiredOperationId == OperationId.PREFLIGHT_ENTRY) {
      return BookkeepingEntryKind.SALE;
    }
    BookkeepingEntryKind requiredEntryKind = requiredEntryKindOrNull(requiredOperationId);
    if (requiredEntryKind != null) {
      return requiredEntryKind;
    }
    throw new IllegalArgumentException(
        "Operation "
            + requiredOperationId.wireName()
            + " does not own one posting-request scaffold.");
  }

  private static @Nullable BookkeepingEntryKind requiredEntryKindOrNull(OperationId operationId) {
    OperationId requiredOperationId = Objects.requireNonNull(operationId, "operationId");
    return switch (requiredOperationId) {
      case POST_ENTRY -> BookkeepingEntryKind.DIRECT_JOURNAL;
      case RECORD_SALE -> BookkeepingEntryKind.SALE;
      case RECORD_EXPENSE -> BookkeepingEntryKind.EXPENSE;
      case RECORD_OWNER_CONTRIBUTION -> BookkeepingEntryKind.OWNER_CONTRIBUTION;
      case RECORD_OWNER_WITHDRAWAL -> BookkeepingEntryKind.OWNER_WITHDRAWAL;
      case RECORD_OPENING_POSITION -> BookkeepingEntryKind.OPENING_POSITION;
      case RECORD_REVERSAL -> BookkeepingEntryKind.REVERSAL;
      default -> null;
    };
  }
}

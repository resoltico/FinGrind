package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Canonical command-topic selection for raw and typed posting-request surfaces. */
public final class ProtocolPostingRequestTopics {
  private static final Map<OperationId, BookkeepingEntryKind> REQUIRED_ENTRY_KINDS =
      Map.ofEntries(
          Map.entry(OperationId.POST_ENTRY, BookkeepingEntryKind.DIRECT_JOURNAL),
          Map.entry(OperationId.RECORD_SALE_SETTLED, BookkeepingEntryKind.SALE_SETTLED),
          Map.entry(OperationId.RECORD_SALE_ON_CREDIT, BookkeepingEntryKind.SALE_ON_CREDIT),
          Map.entry(OperationId.RECORD_PURCHASE_SETTLED, BookkeepingEntryKind.PURCHASE_SETTLED),
          Map.entry(OperationId.RECORD_PURCHASE_ON_CREDIT, BookkeepingEntryKind.PURCHASE_ON_CREDIT),
          Map.entry(OperationId.RECORD_EXPENSE_SETTLED, BookkeepingEntryKind.EXPENSE_SETTLED),
          Map.entry(OperationId.RECORD_EXPENSE_ON_CREDIT, BookkeepingEntryKind.EXPENSE_ON_CREDIT),
          Map.entry(OperationId.RECORD_RECEIPT, BookkeepingEntryKind.RECEIPT),
          Map.entry(OperationId.RECORD_PAYMENT, BookkeepingEntryKind.PAYMENT),
          Map.entry(OperationId.RECORD_OWNER_CONTRIBUTION, BookkeepingEntryKind.OWNER_CONTRIBUTION),
          Map.entry(OperationId.RECORD_OWNER_WITHDRAWAL, BookkeepingEntryKind.OWNER_WITHDRAWAL),
          Map.entry(OperationId.RECORD_OPENING_POSITION, BookkeepingEntryKind.OPENING_POSITION),
          Map.entry(OperationId.RECORD_REVERSAL, BookkeepingEntryKind.REVERSAL));

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
      return BookkeepingEntryKind.SALE_SETTLED;
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
    return REQUIRED_ENTRY_KINDS.get(Objects.requireNonNull(operationId, "operationId"));
  }
}

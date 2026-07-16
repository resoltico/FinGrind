package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Canonical command-topic selection for raw and typed posting-request surfaces. */
public final class ProtocolPostingRequestTopics {
  private static final Map<OperationId, BookkeepingEntryKind> REQUIRED_ENTRY_KINDS =
      requiredEntryKinds();

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

  private static Map<OperationId, BookkeepingEntryKind> requiredEntryKinds() {
    var entryKinds = new EnumMap<OperationId, BookkeepingEntryKind>(OperationId.class);
    entryKinds.put(OperationId.POST_ENTRY, BookkeepingEntryKind.DIRECT_JOURNAL);
    entryKinds.putAll(ProtocolTypedRecordEntryKinds.entryKinds());
    return Map.copyOf(entryKinds);
  }
}

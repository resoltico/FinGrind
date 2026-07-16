package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Canonical association between published typed entry kinds and their economic event classes. */
final class TypedEntryEventClassCatalog {
  private static final Map<BookkeepingEntryKind, EconomicEventClass> EVENT_CLASSES = eventClasses();
  private static final Map<BookkeepingEntryKind, EconomicEventClass> CLASSIFIER_EVENT_CLASSES =
      classifierEventClasses();

  private TypedEntryEventClassCatalog() {}

  static EconomicEventClass requiredEventClass(BookkeepingEntryKind entryKind) {
    BookkeepingEntryKind requiredEntryKind = Objects.requireNonNull(entryKind, "entryKind");
    if (requiredEntryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      throw new IllegalArgumentException("Direct journals do not assert one typed event class.");
    }
    return Objects.requireNonNull(
        EVENT_CLASSES.get(requiredEntryKind),
        "Typed entry kinds must each declare one economic event class.");
  }

  static Optional<EconomicEventClass> classifierAssertedEventClass(BookkeepingEntryKind entryKind) {
    return Optional.ofNullable(
        CLASSIFIER_EVENT_CLASSES.get(Objects.requireNonNull(entryKind, "entryKind")));
  }

  private static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    var eventClasses =
        new EnumMap<BookkeepingEntryKind, EconomicEventClass>(BookkeepingEntryKind.class);
    eventClasses.putAll(StandardTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(InventoryTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(AccrualCutoffTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(LatvianPayrollTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(FixedAssetTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(FinancingTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(RealizedForeignExchangeTypedEntryEventClasses.eventClasses());
    return Map.copyOf(eventClasses);
  }

  private static Map<BookkeepingEntryKind, EconomicEventClass> classifierEventClasses() {
    var eventClasses =
        new EnumMap<BookkeepingEntryKind, EconomicEventClass>(BookkeepingEntryKind.class);
    eventClasses.putAll(InventoryTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(AccrualCutoffTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(LatvianPayrollTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(FixedAssetTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(FinancingTypedEntryEventClasses.eventClasses());
    eventClasses.putAll(RealizedForeignExchangeTypedEntryEventClasses.eventClasses());
    return Map.copyOf(eventClasses);
  }
}

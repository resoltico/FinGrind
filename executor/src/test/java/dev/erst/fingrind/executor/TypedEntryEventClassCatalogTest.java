package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Keeps the typed entry vocabulary total as its public enum evolves. */
class TypedEntryEventClassCatalogTest {
  @Test
  void requiredEventClass_coversEveryTypedEntryKindAndExcludesDirectJournals() {
    for (BookkeepingEntryKind entryKind : BookkeepingEntryKind.values()) {
      if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
        continue;
      }
      assertNotNull(TypedEntryEventClassCatalog.requiredEventClass(entryKind));
    }

    assertThrows(
        IllegalArgumentException.class,
        () -> TypedEntryEventClassCatalog.requiredEventClass(BookkeepingEntryKind.DIRECT_JOURNAL));
  }

  @Test
  void classifierEventClass_includesOnlyContextsWhoseEntriesCarryDerivedClassification() {
    assertEquals(
        Optional.empty(),
        TypedEntryEventClassCatalog.classifierAssertedEventClass(
            BookkeepingEntryKind.SALE_SETTLED));
    assertEquals(
        Optional.of(EconomicEventClass.FIXED_ASSET_DISPOSAL),
        TypedEntryEventClassCatalog.classifierAssertedEventClass(
            BookkeepingEntryKind.FIXED_ASSET_DISPOSAL));
  }
}

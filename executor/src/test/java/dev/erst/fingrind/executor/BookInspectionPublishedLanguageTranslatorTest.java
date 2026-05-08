package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Direct coverage for translating local inspection snapshots into the public contract. */
class BookInspectionPublishedLanguageTranslatorTest {
  @Test
  void toPublished_projectsEveryLocalInspectionVariant() {
    assertEquals(
        new BookInspection.Missing(3),
        BookInspectionPublishedLanguageTranslator.toPublished(
            new BookLifecycleInspection.Missing(3)));

    assertEquals(
        new BookInspection.Existing(BookInspection.Status.BLANK_SQLITE, 7, 2, 3),
        BookInspectionPublishedLanguageTranslator.toPublished(
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.BLANK_SQLITE, 7, 2, 3)));
    assertEquals(
        new BookInspection.Existing(BookInspection.Status.FOREIGN_SQLITE, 7, 2, 3),
        BookInspectionPublishedLanguageTranslator.toPublished(
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.FOREIGN_SQLITE, 7, 2, 3)));
    assertEquals(
        new BookInspection.Existing(BookInspection.Status.UNSUPPORTED_FORMAT_VERSION, 7, 2, 3),
        BookInspectionPublishedLanguageTranslator.toPublished(
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION, 7, 2, 3)));
    assertEquals(
        new BookInspection.Existing(BookInspection.Status.INCOMPLETE_FINGRIND, 7, 2, 3),
        BookInspectionPublishedLanguageTranslator.toPublished(
            new BookLifecycleInspection.Existing(
                BookLifecycleInspection.Status.INCOMPLETE_FINGRIND, 7, 2, 3)));

    Instant initializedAt = Instant.parse("2026-05-07T10:15:30Z");
    assertEquals(
        new BookInspection.Initialized(9, 4, 5, initializedAt),
        BookInspectionPublishedLanguageTranslator.toPublished(
            new BookLifecycleInspection.Initialized(9, 4, 5, initializedAt)));
  }

  @Test
  void toPublished_rejectsNullInspection() {
    assertEquals(
        "inspection",
        assertThrows(
                NullPointerException.class,
                () -> BookInspectionPublishedLanguageTranslator.toPublished(nullOf()))
            .getMessage());
  }
}

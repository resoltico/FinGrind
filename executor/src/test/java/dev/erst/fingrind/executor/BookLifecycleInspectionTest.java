package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for the local book lifecycle inspection model. */
class BookLifecycleInspectionTest {
  @Test
  void status_exposesStableWireValuesAndCompatibilityFlags() {
    assertEquals(
        List.of(
            "missing",
            "blank-sqlite",
            "initialized",
            "foreign-sqlite",
            "unsupported-format-version",
            "incomplete-fingrind"),
        BookLifecycleInspection.Status.wireValues());

    assertEquals("missing", BookLifecycleInspection.Status.MISSING.wireValue());
    assertEquals("blank-sqlite", BookLifecycleInspection.Status.BLANK_SQLITE.wireValue());
    assertEquals("initialized", BookLifecycleInspection.Status.INITIALIZED.wireValue());
    assertEquals("foreign-sqlite", BookLifecycleInspection.Status.FOREIGN_SQLITE.wireValue());
    assertEquals(
        "unsupported-format-version",
        BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION.wireValue());
    assertEquals(
        "incomplete-fingrind", BookLifecycleInspection.Status.INCOMPLETE_FINGRIND.wireValue());

    assertFalse(BookLifecycleInspection.Status.MISSING.isExistingNonInitialized());
    assertTrue(BookLifecycleInspection.Status.BLANK_SQLITE.isExistingNonInitialized());
    assertFalse(BookLifecycleInspection.Status.INITIALIZED.isExistingNonInitialized());
    assertTrue(BookLifecycleInspection.Status.FOREIGN_SQLITE.isExistingNonInitialized());
    assertTrue(
        BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION.isExistingNonInitialized());
    assertTrue(BookLifecycleInspection.Status.INCOMPLETE_FINGRIND.isExistingNonInitialized());

    assertFalse(BookLifecycleInspection.Status.MISSING.initialized());
    assertTrue(BookLifecycleInspection.Status.INITIALIZED.initialized());
    assertFalse(BookLifecycleInspection.Status.FOREIGN_SQLITE.initialized());

    assertFalse(BookLifecycleInspection.Status.MISSING.compatibleWithCurrentBinary());
    assertTrue(BookLifecycleInspection.Status.INITIALIZED.compatibleWithCurrentBinary());
    assertFalse(
        BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION.compatibleWithCurrentBinary());
  }

  @Test
  void missing_requiresSupportedFormatVersionAndReportsMissingStatus() {
    BookLifecycleInspection.Missing inspection = new BookLifecycleInspection.Missing(3);

    assertEquals(BookLifecycleInspection.Status.MISSING, inspection.status());
    assertEquals(
        "Supported book format version must be at least 1.",
        assertThrows(IllegalArgumentException.class, () -> new BookLifecycleInspection.Missing(0))
            .getMessage());
  }

  @Test
  void existing_requiresExistingNonInitializedStatusAndNonNegativeMetadata() {
    BookLifecycleInspection.Existing blankSqlite =
        new BookLifecycleInspection.Existing(BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 1);
    BookLifecycleInspection.Existing foreignSqlite =
        new BookLifecycleInspection.Existing(
            BookLifecycleInspection.Status.FOREIGN_SQLITE, 1, 2, 3);
    BookLifecycleInspection.Existing unsupportedFormat =
        new BookLifecycleInspection.Existing(
            BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION, 7, 5, 6);
    BookLifecycleInspection.Existing incompleteFingrind =
        new BookLifecycleInspection.Existing(
            BookLifecycleInspection.Status.INCOMPLETE_FINGRIND, 9, 8, 10);

    assertEquals(BookLifecycleInspection.Status.BLANK_SQLITE, blankSqlite.status());
    assertFalse(blankSqlite.compatibleWithCurrentBinary());
    assertEquals(BookLifecycleInspection.Status.FOREIGN_SQLITE, foreignSqlite.status());
    assertEquals(
        BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION, unsupportedFormat.status());
    assertEquals(BookLifecycleInspection.Status.INCOMPLETE_FINGRIND, incompleteFingrind.status());

    assertEquals(
        "status",
        assertThrows(
                NullPointerException.class,
                () -> new BookLifecycleInspection.Existing(nullOf(), 1, 2, 3))
            .getMessage());
    assertEquals(
        "Existing book inspection status must be one of BLANK_SQLITE, FOREIGN_SQLITE, "
            + "UNSUPPORTED_FORMAT_VERSION, or INCOMPLETE_FINGRIND.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.MISSING, 1, 2, 3))
            .getMessage());
    assertEquals(
        "Existing book inspection status must be one of BLANK_SQLITE, FOREIGN_SQLITE, "
            + "UNSUPPORTED_FORMAT_VERSION, or INCOMPLETE_FINGRIND.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.INITIALIZED, 1, 2, 3))
            .getMessage());
    assertEquals(
        "Supported book format version must be at least 1.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.BLANK_SQLITE, 1, 2, 0))
            .getMessage());
    assertEquals(
        "SQLite applicationId must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.BLANK_SQLITE, -1, 2, 1))
            .getMessage());
    assertEquals(
        "Detected book format version must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                        BookLifecycleInspection.Status.BLANK_SQLITE, 1, -1, 1))
            .getMessage());
  }

  @Test
  void initialized_requiresMetadataAndInitializationTimestamp() {
    Instant initializedAt = Instant.parse("2026-05-07T09:10:11Z");
    BookLifecycleInspection.Initialized inspection =
        new BookLifecycleInspection.Initialized(1, 2, 3, initializedAt);

    assertEquals(BookLifecycleInspection.Status.INITIALIZED, inspection.status());
    assertEquals(initializedAt, inspection.initializedAt());
    assertTrue(inspection.compatibleWithCurrentBinary());

    assertEquals(
        "Supported book format version must be at least 1.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new BookLifecycleInspection.Initialized(1, 2, 0, initializedAt))
            .getMessage());
    assertEquals(
        "SQLite applicationId must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new BookLifecycleInspection.Initialized(-1, 2, 3, initializedAt))
            .getMessage());
    assertEquals(
        "Detected book format version must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new BookLifecycleInspection.Initialized(1, -1, 3, initializedAt))
            .getMessage());
    assertEquals(
        "initializedAt",
        assertThrows(
                NullPointerException.class,
                () -> new BookLifecycleInspection.Initialized(1, 2, 3, nullOf()))
            .getMessage());
  }

  @Test
  void accessPolicy_interpretsLifecycleStatesDeterministically() {
    assertFalse(new BookLifecycleInspection.Missing(1).allowsInitializedWorkflow());
    assertFalse(
        BookLifecycleInspection.allowsInitializedWorkflowFor(
            new BookLifecycleInspection.Missing(1)));
    assertFalse(
        new BookLifecycleInspection.Existing(BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 1)
            .allowsInitializedWorkflow());
    assertTrue(
        new BookLifecycleInspection.Initialized(1, 1, 1, Instant.parse("2026-05-07T09:10:11Z"))
            .allowsInitializedWorkflow());
    assertEquals(
        "inspection",
        assertThrows(
                NullPointerException.class,
                () -> BookLifecycleInspection.allowsInitializedWorkflowFor(nullOf()))
            .getMessage());
    assertEquals(
        "The selected SQLite file is not a FinGrind book.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                            BookLifecycleInspection.Status.FOREIGN_SQLITE, 0, 0, 1)
                        .allowsInitializedWorkflow())
            .getMessage());
    assertEquals(
        "The selected FinGrind book format version 7 is unsupported. Expected version 3.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                            BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION, 1, 7, 3)
                        .allowsInitializedWorkflow())
            .getMessage());
    assertEquals(
        "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                            BookLifecycleInspection.Status.INCOMPLETE_FINGRIND, 1, 1, 1)
                        .allowsInitializedWorkflow())
            .getMessage());
  }

  @Test
  void accessPolicy_rejectsIncompleteBooksExplicitly() {
    assertEquals(
        "The selected FinGrind book is incomplete or corrupted and cannot be opened safely.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    new BookLifecycleInspection.Existing(
                            BookLifecycleInspection.Status.INCOMPLETE_FINGRIND, 3, 2, 4)
                        .allowsInitializedWorkflow())
            .getMessage());
  }
}

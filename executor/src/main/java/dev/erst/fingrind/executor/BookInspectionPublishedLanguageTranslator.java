package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.Objects;

/** Projects local inspection snapshots into the published runtime/discovery contract. */
public final class BookInspectionPublishedLanguageTranslator {
  private BookInspectionPublishedLanguageTranslator() {}

  /** Projects one local inspection snapshot into the public machine-facing contract. */
  public static BookInspection toPublished(BookLifecycleInspection inspection) {
    Objects.requireNonNull(inspection, "inspection");
    if (inspection instanceof BookLifecycleInspection.Missing missing) {
      return new BookInspection.Missing(missing.supportedBookFormatVersion());
    }
    if (inspection instanceof BookLifecycleInspection.Existing existing) {
      return new BookInspection.Existing(
          toPublishedExistingStatus(existing.status()),
          existing.applicationId(),
          existing.detectedBookFormatVersion(),
          existing.supportedBookFormatVersion());
    }
    BookLifecycleInspection.Initialized initialized =
        (BookLifecycleInspection.Initialized) inspection;
    return new BookInspection.Initialized(
        initialized.applicationId(),
        initialized.detectedBookFormatVersion(),
        initialized.supportedBookFormatVersion(),
        initialized.initializedAt(),
        initialized.bookIdentity());
  }

  private static BookInspection.Status toPublishedExistingStatus(
      BookLifecycleInspection.Status status) {
    Objects.requireNonNull(status, "status");
    if (status == BookLifecycleInspection.Status.BLANK_SQLITE) {
      return BookInspection.Status.BLANK_SQLITE;
    }
    if (status == BookLifecycleInspection.Status.FOREIGN_SQLITE) {
      return BookInspection.Status.FOREIGN_SQLITE;
    }
    if (status == BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION) {
      return BookInspection.Status.UNSUPPORTED_FORMAT_VERSION;
    }
    return BookInspection.Status.INCOMPLETE_FINGRIND;
  }
}

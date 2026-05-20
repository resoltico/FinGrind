package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ReportingObligationStatus;
import java.util.Objects;

/** Explicit initialization command for one new book identity. */
public record OpenBookCommand(BookIdentity bookIdentity) {
  /** Validates one open-book command. */
  public OpenBookCommand {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    if (bookIdentity.entityProfile().ownerModel() == OwnerModel.UNKNOWN) {
      throw new IllegalArgumentException("Book initialization requires one explicit owner model.");
    }
    if (bookIdentity.entityProfile().reportingObligationStatus()
        == ReportingObligationStatus.UNSPECIFIED) {
      throw new IllegalArgumentException(
          "Book initialization requires one explicit reporting obligation status.");
    }
    if (bookIdentity.entityProfile().businessActivityTags().isEmpty()) {
      throw new IllegalArgumentException(
          "Book initialization requires at least one business activity tag.");
    }
  }
}

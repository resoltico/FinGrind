package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import java.util.Objects;

/** Explicit initialization command for one new book identity. */
public record OpenBookCommand(BookIdentity bookIdentity) {
  /** Validates one open-book command. */
  public OpenBookCommand {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    if (bookIdentity.entityProfile().businessActivityTags().isEmpty()) {
      throw new IllegalArgumentException(
          "Book initialization requires at least one business activity tag.");
    }
  }
}

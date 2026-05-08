package dev.erst.fingrind.executor;

import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.spi.BookStore;
import java.time.Clock;
import java.util.Objects;

/** Application service that owns explicit book initialization and account-registry commands. */
public final class BookAdministrationService {
  private final BookStore bookStore;
  private final Clock clock;

  /** Creates the book-administration service with its application-owned seams. */
  public BookAdministrationService(BookStore bookStore, Clock clock) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Explicitly initializes a new book. */
  public BookOpeningOutcome openBook() {
    return bookStore.openBook(clock.instant());
  }

  /** Declares or reactivates one account in the selected book. */
  public AccountDeclarationOutcome declareAccount(AccountDeclaration command) {
    Objects.requireNonNull(command, "command");
    return bookStore.declareAccount(
        command.accountCode(), command.accountName(), command.normalBalance(), clock.instant());
  }
}

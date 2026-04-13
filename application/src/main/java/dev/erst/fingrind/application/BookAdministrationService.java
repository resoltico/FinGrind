package dev.erst.fingrind.application;

import java.time.Clock;
import java.util.Objects;

/** Application service that owns explicit book initialization and account-registry commands. */
public final class BookAdministrationService {
  private final BookSession bookSession;
  private final Clock clock;

  /** Creates the book-administration service with its application-owned seams. */
  public BookAdministrationService(BookSession bookSession, Clock clock) {
    this.bookSession = Objects.requireNonNull(bookSession, "bookSession");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Explicitly initializes a new book. */
  public OpenBookResult openBook() {
    return bookSession.openBook(clock.instant());
  }

  /** Declares or reactivates one account in the selected book. */
  public DeclareAccountResult declareAccount(DeclareAccountCommand command) {
    Objects.requireNonNull(command, "command");
    return bookSession.declareAccount(
        command.accountCode(), command.accountName(), command.normalBalance(), clock.instant());
  }

  /** Lists the current account registry for the selected book. */
  public ListAccountsResult listAccounts() {
    if (!bookSession.isInitialized()) {
      return new ListAccountsResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
    }
    return new ListAccountsResult.Listed(bookSession.listAccounts());
  }
}

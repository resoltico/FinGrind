package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.OpenBookResult;
import java.time.Clock;
import java.util.Objects;

/** Application service that owns explicit book initialization and account-registry commands. */
public final class BookAdministrationService {
  private final BookAdministrationSession bookSession;
  private final Clock clock;

  /** Creates the book-administration service with its application-owned seams. */
  public BookAdministrationService(BookAdministrationSession bookSession, Clock clock) {
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
}

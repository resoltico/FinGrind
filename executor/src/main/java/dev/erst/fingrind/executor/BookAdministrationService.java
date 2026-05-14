package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.spi.BookStore;
import java.time.Clock;
import java.util.Objects;

/** Application service that owns explicit book initialization and account-registry commands. */
public final class BookAdministrationService {
  private final BookStore bookStore;
  private final Clock clock;
  private final PeriodCloseService periodCloseService;

  /** Creates the book-administration service with its application-owned seams. */
  public BookAdministrationService(BookStore bookStore, Clock clock) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.periodCloseService =
        new PeriodCloseService(this.bookStore, new UuidV7PostingIdGenerator(), this.clock);
  }

  /** Explicitly initializes a new book. */
  public BookOpeningOutcome openBook(BookIdentity bookIdentity) {
    return bookStore.openBook(
        clock.instant(), Objects.requireNonNull(bookIdentity, "bookIdentity"));
  }

  /** Declares or reactivates one account in the selected book. */
  public AccountDeclarationOutcome declareAccount(AccountDeclaration command) {
    Objects.requireNonNull(command, "command");
    return bookStore.declareAccount(
        command.accountCode(),
        command.accountName(),
        command.accountType(),
        command.accountRole(),
        clock.instant());
  }

  /** Closes one contiguous reporting period into the retained-earnings account. */
  public PeriodCloseOutcome closePeriod(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      dev.erst.fingrind.core.AccountCode retainedEarningsAccountCode) {
    return periodCloseService.closePeriod(
        Objects.requireNonNull(reportingPeriod, "reportingPeriod"),
        Objects.requireNonNull(retainedEarningsAccountCode, "retainedEarningsAccountCode"));
  }
}

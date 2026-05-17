package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.ChartOfAccounts;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service that owns explicit book initialization and account-registry commands. */
public final class BookAdministrationService {
  private final BookLifecycleReader lifecycleReader;
  private final BookAdministrationStore bookStore;
  private final AccountCatalogStore accountCatalogStore;
  private final Clock clock;

  /** Creates the book-administration service with its application-owned seams. */
  public BookAdministrationService(
      BookLifecycleReader lifecycleReader,
      BookAdministrationStore bookStore,
      AccountCatalogStore accountCatalogStore,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.accountCatalogStore = Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Explicitly initializes a new book. */
  public BookOpeningOutcome openBook(BookIdentity bookIdentity) {
    return bookStore.openBook(
        clock.instant(), Objects.requireNonNull(bookIdentity, "bookIdentity"));
  }

  /** Declares or reactivates one account in the selected book. */
  public AccountDeclarationOutcome declareAccount(AccountDeclaration command) {
    Objects.requireNonNull(command, "command");
    BookLifecycleInspection inspection = lifecycleReader.inspectBook();
    if (!inspection.allowsInitializedWorkflow()) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    Optional<BookkeepingAdministrationRejection> rejection =
        ChartOfAccounts.of(accountCatalogStore.allAccounts()).validate(command);
    if (rejection.isPresent()) {
      return new AccountDeclarationOutcome.Rejected(rejection.orElseThrow());
    }
    return bookStore.declareAccount(
        command.accountCode(),
        command.accountName(),
        command.accountType(),
        command.accountRole(),
        command.accountTaxonomy(),
        clock.instant());
  }
}

package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.LedgerPlanService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.PostingLookupStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Workflow and lifecycle fixtures shared by Jazzer harnesses. */
public final class CliFuzzWorkflowFixtures {
  private CliFuzzWorkflowFixtures() {}

  /** Returns the canonical book identity used by Jazzer lifecycle setup. */
  public static BookIdentity bookIdentity() {
    return bookIdentity(CurrencyUnit.of("EUR"));
  }

  /** Returns the canonical book identity used by Jazzer lifecycle setup for one currency. */
  public static BookIdentity bookIdentity(CurrencyUnit functionalCurrency) {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        Objects.requireNonNull(functionalCurrency, "functionalCurrency"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }

  /** Returns the canonical open-book command used by workflow and replay setup. */
  public static OpenBookCommand openBookCommand() {
    return openBookCommand(CurrencyUnit.of("EUR"));
  }

  /** Returns the canonical open-book command used by workflow and replay setup for one currency. */
  public static OpenBookCommand openBookCommand(CurrencyUnit functionalCurrency) {
    return new OpenBookCommand(bookIdentity(functionalCurrency));
  }

  /** Creates the fixed-clock administration service used by lifecycle-aware harnesses. */
  public static <T extends BookLifecycleReader & BookAdministrationStore & AccountCatalogStore>
      BookAdministrationService administrationService(T bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    return new BookAdministrationService(
        bookStore, bookStore, bookStore, CliFuzzFixtures.fixedClock());
  }

  /** Creates the fixed-clock posting service used by workflow fuzz harnesses and replay. */
  public static PostingApplicationService postingApplicationService(
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(validationStore, "validationStore must not be null");
    Objects.requireNonNull(commitStore, "commitStore must not be null");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator must not be null");
    return new PostingApplicationService(
        validationStore, commitStore, postingIdGenerator, CliFuzzFixtures.fixedClock());
  }

  /** Creates the fixed-clock ledger-plan service used by plan fuzz harnesses and replay. */
  public static <T extends BookAdministrationStore & AccountCatalogStore & TaxAdministrationStore>
      LedgerPlanService ledgerPlanService(
          LedgerPlanTransaction transactionStore,
          T administrationStore,
          BookkeepingReadStore readStore,
          PostingValidationStore validationStore,
          PostingCommitStore commitStore,
          PostingIdGenerator postingIdGenerator) {
    Objects.requireNonNull(transactionStore, "transactionStore must not be null");
    Objects.requireNonNull(administrationStore, "administrationStore must not be null");
    Objects.requireNonNull(readStore, "readStore must not be null");
    Objects.requireNonNull(validationStore, "validationStore must not be null");
    Objects.requireNonNull(commitStore, "commitStore must not be null");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator must not be null");
    return new LedgerPlanService(
        transactionStore,
        administrationStore,
        administrationStore,
        readStore,
        validationStore,
        commitStore,
        administrationStore,
        postingIdGenerator,
        CliFuzzFixtures.fixedClock());
  }

  /** Opens one book and fails fast if lifecycle setup drifts unexpectedly. */
  public static void openBook(BookAdministrationService administrationService) {
    openBook(administrationService, CurrencyUnit.of("EUR"));
  }

  /** Opens one book in the supplied functional currency and fails fast on lifecycle drift. */
  public static void openBook(
      BookAdministrationService administrationService, CurrencyUnit functionalCurrency) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    OpenBookResult result =
        BookkeepingPublishedLanguageTranslator.toPublished(
            administrationService.openBook(bookIdentity(functionalCurrency)));
    OpenBookResult.Opened opened =
        switch (result) {
          case OpenBookResult.Opened accepted -> accepted;
          case OpenBookResult.Rejected _ ->
              throw new IllegalStateException("Lifecycle setup failed to initialize the book.");
        };
    if (!opened.initializedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Lifecycle setup used an unexpected initialized-at instant.");
    }
  }

  /** Runs one posting preflight through the internal bookkeeping translation boundary. */
  public static PreflightEntryResult preflight(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.preflight(command);
  }

  /** Runs one posting commit through the internal bookkeeping translation boundary. */
  public static CommitEntryResult commit(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.commit(command);
  }

  /**
   * Translates an optional stored posting from the bookkeeping model into the public fact shape.
   */
  public static Optional<PostingFact> publishedStoredPosting(
      Optional<StoredRequestPosting> storedPosting) {
    Objects.requireNonNull(storedPosting, "storedPosting must not be null");
    return storedPosting
        .map(StoredRequestPosting::postingFact)
        .map(BookkeepingPublishedLanguageTranslator::toPublished);
  }

  /** Loads one stored posting and translates it into the public fact shape. */
  public static Optional<PostingFact> publishedStoredPosting(
      PostingLookupStore bookStore, dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    return publishedStoredPosting(bookStore.findExistingPosting(idempotencyKey));
  }
}

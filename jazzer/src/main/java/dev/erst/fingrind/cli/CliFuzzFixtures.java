package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared helpers for FinGrind Jazzer harnesses that start from CLI request JSON. */
public final class CliFuzzFixtures {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private CliFuzzFixtures() {}

  /** Parses one CLI request payload from bytes using the same reader used by the production CLI. */
  public static PostEntryCommand readPostEntryCommand(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readPostEntryCommand(Path.of("-"));
  }

  /** Parses one ledger-plan payload from bytes using the production CLI request reader. */
  public static LedgerPlan readLedgerPlan(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    return new CliRequestReader(new ByteArrayInputStream(input)).readLedgerPlan(Path.of("-"));
  }

  /** Returns a deterministic posting-id generator for one fuzz iteration. */
  public static PostingIdGenerator postingIdGenerator(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    String postingId = UUID.nameUUIDFromBytes(input).toString();
    return () -> new PostingId(postingId);
  }

  /** Returns the deterministic clock shared by Jazzer harnesses and regression replay. */
  public static Clock fixedClock() {
    return FIXED_CLOCK;
  }

  /** Creates the fixed-clock administration service used by lifecycle-aware harnesses. */
  public static BookAdministrationService administrationService(BookStore bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    return new BookAdministrationService(bookStore, fixedClock());
  }

  /** Opens one book and fails fast if lifecycle setup drifts unexpectedly. */
  public static void openBook(BookAdministrationService administrationService) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    OpenBookResult result =
        BookkeepingPublishedLanguageTranslator.toPublished(administrationService.openBook());
    OpenBookResult.Opened opened =
        switch (result) {
          case OpenBookResult.Opened accepted -> accepted;
          case OpenBookResult.Rejected _ ->
              throw new IllegalStateException("Lifecycle setup failed to initialize the book.");
        };
    if (!opened.initializedAt().equals(fixedClock().instant())) {
      throw new IllegalStateException("Lifecycle setup used an unexpected initialized-at instant.");
    }
  }

  /**
   * Declares every distinct posting account so the final write path can exercise business rules.
   */
  public static List<DeclaredAccount> declarePostingAccounts(
      BookAdministrationService administrationService, PostEntryCommand command) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return declarePostingAccountCommands(command).stream()
        .map(declareAccountCommand -> declareAccount(administrationService, declareAccountCommand))
        .map(CliFuzzFixtures::requireDeclaredAccount)
        .toList();
  }

  /** Returns deterministic declare-account commands for every distinct posting account. */
  public static List<DeclareAccountCommand> declarePostingAccountCommands(
      PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return command.journalEntry().lines().stream()
        .map(line -> line.accountCode())
        .distinct()
        .map(CliFuzzFixtures::syntheticDeclareAccountCommand)
        .toList();
  }

  /** Returns the first journal-line account code for lifecycle assertions. */
  public static AccountCode firstAccountCode(PostEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return command.journalEntry().lines().getFirst().accountCode();
  }

  /** Reactivates one account with an updated display name and asserts the durable shape. */
  public static DeclaredAccount reactivateAccount(
      BookAdministrationService administrationService, DeclaredAccount account) {
    Objects.requireNonNull(administrationService, "administrationService must not be null");
    Objects.requireNonNull(account, "account must not be null");
    DeclareAccountResult result =
        declareAccount(
            administrationService,
            new DeclareAccountCommand(
                account.accountCode(),
                new AccountName(account.accountName().value() + " restored"),
                account.accountType(),
                account.accountRole()));
    DeclaredAccount restoredAccount = requireDeclaredAccount(result);
    if (!restoredAccount.active()) {
      throw new IllegalStateException("Account reactivation did not restore the active flag.");
    }
    if (!restoredAccount.declaredAt().equals(account.declaredAt())) {
      throw new IllegalStateException(
          "Account reactivation changed the original declared-at timestamp.");
    }
    return restoredAccount;
  }

  /** Runs one posting preflight through the internal bookkeeping translation boundary. */
  public static PreflightEntryResult preflight(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.preflight(
        BookkeepingPublishedLanguageTranslator.fromPublished(command));
  }

  /** Runs one posting commit through the internal bookkeeping translation boundary. */
  public static CommitEntryResult commit(
      PostingApplicationService applicationService, PostEntryCommand command) {
    Objects.requireNonNull(applicationService, "applicationService must not be null");
    Objects.requireNonNull(command, "command must not be null");
    return applicationService.commit(BookkeepingPublishedLanguageTranslator.fromPublished(command));
  }

  /**
   * Translates an optional stored posting from the bookkeeping model into the public fact shape.
   */
  public static Optional<PostingFact> publishedStoredPosting(
      Optional<CommittedPosting> storedPosting) {
    Objects.requireNonNull(storedPosting, "storedPosting must not be null");
    return storedPosting.map(BookkeepingPublishedLanguageTranslator::toPublished);
  }

  /**
   * Loads one stored posting from a posting session and translates it into the public fact shape.
   */
  public static Optional<PostingFact> publishedStoredPosting(
      BookStore bookStore, dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    return publishedStoredPosting(bookStore.findExistingPosting(idempotencyKey));
  }

  /** Lists accounts and fails fast if the registry surface is not in the expected state. */
  public static List<DeclaredAccount> listAccounts(BookStore bookStore) {
    Objects.requireNonNull(bookStore, "bookStore must not be null");
    List<DeclaredAccount> accounts = new java.util.ArrayList<>();
    BookReadService readService = new BookReadService(bookStore);
    Optional<AccountPageCursor> cursor = Optional.empty();
    while (true) {
      ListAccountsResult result = listAccountsPage(readService, cursor);
      ListAccountsResult.Listed listed =
          switch (result) {
            case ListAccountsResult.Listed accepted -> accepted;
            case ListAccountsResult.Rejected _ ->
                throw new IllegalStateException(
                    "Lifecycle setup failed to list declared accounts.");
          };
      accounts.addAll(listed.page().accounts());
      if (!listed.page().hasMore()) {
        return List.copyOf(accounts);
      }
      cursor = listed.page().nextCursor();
    }
  }

  private static ListAccountsResult listAccountsPage(
      BookReadService readService, Optional<AccountPageCursor> cursor) {
    return readService.listAccounts(
        new ListAccountsQuery(InteractionLimits.PAGE_LIMIT_MAX, cursor));
  }

  private static DeclaredAccount requireDeclaredAccount(DeclareAccountResult result) {
    return switch (result) {
      case DeclareAccountResult.Declared declared -> declared.account();
      case DeclareAccountResult.Rejected _ ->
          throw new IllegalStateException("Lifecycle setup failed to declare an account.");
    };
  }

  private static DeclareAccountResult declareAccount(
      BookAdministrationService administrationService, DeclareAccountCommand command) {
    return BookkeepingPublishedLanguageTranslator.toPublished(
        administrationService.declareAccount(
            BookkeepingPublishedLanguageTranslator.fromPublished(command)));
  }

  private static AccountName syntheticAccountName(AccountCode accountCode) {
    return new AccountName("Synthetic " + accountCode.value());
  }

  private static DeclareAccountCommand syntheticDeclareAccountCommand(AccountCode accountCode) {
    AccountRole accountRole = syntheticAccountRole(accountCode);
    return new DeclareAccountCommand(
        accountCode,
        syntheticAccountName(accountCode),
        syntheticAccountType(accountCode),
        accountRole);
  }

  private static AccountRole syntheticAccountRole(AccountCode accountCode) {
    int bucket = Math.floorMod(accountCode.value().hashCode(), 4);
    if (bucket == 0) {
      return AccountRole.CONTRA;
    }
    return AccountRole.ORDINARY;
  }

  private static AccountType syntheticAccountType(AccountCode accountCode) {
    return switch (Math.floorMod(accountCode.value().hashCode(), 5)) {
      case 0 -> AccountType.ASSET;
      case 1 -> AccountType.LIABILITY;
      case 2 -> AccountType.EQUITY;
      case 3 -> AccountType.REVENUE;
      default -> AccountType.EXPENSE;
    };
  }
}

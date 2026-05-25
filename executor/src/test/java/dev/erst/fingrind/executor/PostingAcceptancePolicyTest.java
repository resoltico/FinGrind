package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.initializedLifecycleInspection;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** Unit tests for shared bookkeeping posting acceptance rules. */
class PostingAcceptancePolicyTest {
  private static final PostingAcceptancePolicy POSTING_ACCEPTANCE_POLICY =
      PostingAcceptancePolicy.currentKernel();

  @Test
  void rejectionFor_rejectsMissingBookBeforeAnyOtherChecks() {
    RecordingValidationBook book = new RecordingValidationBook();

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(command("idem-missing"), book);

    assertEquals(Optional.of(new BookkeepingPostingRejection.BookNotInitialized()), rejection);
  }

  @Test
  void rejectionFor_reportsDuplicateIdempotencyBeforeAccountViolations() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.existingPosting = Optional.of(existingPosting("posting-1", "idem-1"));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(command("idem-1"), book);

    assertEquals(Optional.of(new BookkeepingPostingRejection.DuplicateIdempotencyKey()), rejection);
    assertEquals(0, book.findAccountsCalls);
  }

  @Test
  void rejectionFor_usesOneBulkLookupAndDeduplicatesRepeatedAccounts() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.accounts.put(
        new AccountCode("1000"),
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            false,
            Instant.parse("2026-04-07T10:15:30Z")));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                "idem-2",
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "1.00"),
                    line("1000", JournalLine.EntrySide.DEBIT, "2.00"),
                    line("2000", JournalLine.EntrySide.CREDIT, "3.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(
                    new BookkeepingPostingRejection.InactiveAccount(new AccountCode("1000")),
                    new BookkeepingPostingRejection.UnknownAccount(new AccountCode("2000"))))),
        rejection);
    assertEquals(1, book.findAccountsCalls);
    assertEquals(List.of(new AccountCode("1000"), new AccountCode("2000")), book.requestedAccounts);
    assertThrows(AssertionError.class, () -> book.findAccount(new AccountCode("1000")));
  }

  @Test
  void rejectionFor_rejectsNonPostableHeaderAccounts() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount cashHeader =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash Header"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            new AccountTaxonomy(
                AccountNodeKind.HEADER,
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.empty()),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(cashHeader.accountCode(), cashHeader);
    book.accounts.put(revenue.accountCode(), revenue);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                "idem-non-postable-header",
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(
                    new BookkeepingPostingRejection.NonPostableAccount(
                        new AccountCode("1000"), AccountNodeKind.HEADER)))),
        rejection);
  }

  @Test
  void defaultFindAccounts_delegatesToSingleAccountLookupsInStableOrder() {
    FallbackValidationBook book = new FallbackValidationBook();
    AccountCode cash = new AccountCode("1000");
    AccountCode revenue = new AccountCode("2000");
    RegisteredAccount cashAccount =
        registeredAccount(
            cash,
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(cash, cashAccount);

    assertEquals(
        Map.of(cash, cashAccount),
        book.findAccounts(new java.util.LinkedHashSet<>(List.of(cash, revenue))));
    assertEquals(List.of(cash, revenue), book.requestedAccounts);
  }

  @Test
  void rejectionFor_rejectsTransferredPeriodResultAttemptsBeforeAccountChecks() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.closedThrough = Optional.of(LocalDate.parse("2026-04-07"));

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(command("idem-closed"), book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.TransferredPeriodResultViolation(
                LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07"))),
        rejection);
    assertEquals(0, book.findAccountsCalls);
  }

  @Test
  void rejectionFor_allowsSystemGeneratedPostingKindsFromPostingCommands() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount resultHolding =
        new RegisteredAccount(
            new AccountCode("3200"),
            new AccountName("Retained Earnings"),
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(resultHolding.accountCode(), resultHolding);
    book.accounts.put(revenue.accountCode(), revenue);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                PostingKind.PERIOD_RESULT_TRANSFER,
                dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER,
                "idem-system-command",
                SourceChannel.SYSTEM,
                List.of(
                    line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(Optional.empty(), rejection);
  }

  @Test
  void rejectionFor_rejectsFunctionalCurrencyMismatchBeforeAccountChecks() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                "idem-usd",
                SourceChannel.CLI,
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "USD", "10.00"),
                    line("2000", JournalLine.EntrySide.CREDIT, "USD", "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                dev.erst.fingrind.core.CurrencyUnit.of("EUR"),
                dev.erst.fingrind.core.CurrencyUnit.of("USD"))),
        rejection);
    assertEquals(0, book.findAccountsCalls);
  }

  @Test
  void rejectionFor_rejectsOpeningBalanceRevenueAccounts() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount asset =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(asset.accountCode(), asset);
    book.accounts.put(revenue.accountCode(), revenue);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                PostingKind.OPENING_BALANCE,
                dev.erst.fingrind.core.PostingOriginKind.OPENING_BALANCE_ADJUSTMENT,
                "idem-opening-revenue",
                SourceChannel.CLI,
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("4000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE)),
        rejection);
  }

  @Test
  void rejectionFor_rejectsOpeningBalanceExpenseAccounts() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount capital =
        registeredAccount(
            new AccountCode("3000"),
            new AccountName("Owner Capital"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount expense =
        registeredAccount(
            new AccountCode("5000"),
            new AccountName("Expense"),
            AccountType.EXPENSE,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(capital.accountCode(), capital);
    book.accounts.put(expense.accountCode(), expense);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                PostingKind.OPENING_BALANCE,
                dev.erst.fingrind.core.PostingOriginKind.OPENING_BALANCE_ADJUSTMENT,
                "idem-opening-expense",
                SourceChannel.CLI,
                List.of(
                    line("5000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("3000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount(
                new AccountCode("5000"), AccountType.EXPENSE)),
        rejection);
  }

  @Test
  void rejectionFor_acceptsOpeningBalanceForBalanceSheetAccounts() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount asset =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount capital =
        registeredAccount(
            new AccountCode("3000"),
            new AccountName("Owner Capital"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(asset.accountCode(), asset);
    book.accounts.put(capital.accountCode(), capital);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                PostingKind.OPENING_BALANCE,
                dev.erst.fingrind.core.PostingOriginKind.OPENING_BALANCE_ADJUSTMENT,
                "idem-opening-balance-sheet",
                SourceChannel.CLI,
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("3000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(Optional.empty(), rejection);
  }

  @Test
  void rejectionFor_rejectsOpeningBalanceAfterAnyCommittedPostingExists() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount asset =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount capital =
        registeredAccount(
            new AccountCode("3000"),
            new AccountName("Owner Capital"),
            AccountType.EQUITY,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(asset.accountCode(), asset);
    book.accounts.put(capital.accountCode(), capital);
    CommittedPosting openingPosting = existingPosting("posting-1", "idem-1");
    CommittedPosting ordinaryPosting = existingPosting("posting-2", "idem-2");
    book.postings =
        List.of(
            new CommittedPosting(
                openingPosting.postingId(),
                openingPosting.journalEntry(),
                openingPosting.postingLineage(),
                PostingKind.OPENING_BALANCE,
                dev.erst.fingrind.core.PostingOriginKind.OPENING_BALANCE_ADJUSTMENT,
                openingPosting.evidence(),
                openingPosting.provenance()),
            ordinaryPosting);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                PostingKind.OPENING_BALANCE,
                dev.erst.fingrind.core.PostingOriginKind.OPENING_BALANCE_ADJUSTMENT,
                "idem-opening-late",
                SourceChannel.CLI,
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("3000", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.OpeningBalanceWindowClosed(
                PostingKind.OPENING_BALANCE, LocalDate.parse("2026-04-07"))),
        rejection);
  }

  @Test
  void rejectionFor_rejectsRetainedEarningsAccountOutsidePeriodResultTransferPosting() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount resultHolding =
        new RegisteredAccount(
            new AccountCode("3200"),
            new AccountName("Retained Earnings"),
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount balancingAccount =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(resultHolding.accountCode(), resultHolding);
    book.accounts.put(balancingAccount.accountCode(), balancingAccount);

    Optional<BookkeepingPostingRejection> rejection =
        POSTING_ACCEPTANCE_POLICY.rejectionFor(
            command(
                "idem-retained",
                List.of(
                    line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
            book);

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ResultHoldingAccountReserved(
                resultHolding.accountCode())),
        rejection);
  }

  @Test
  void rejectionFor_allowsRetainedEarningsAccountInsidePeriodResultTransferPosting() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    RegisteredAccount resultHolding =
        new RegisteredAccount(
            new AccountCode("3200"),
            new AccountName("Retained Earnings"),
            AccountType.EQUITY,
            AccountRole.ORDINARY,
            financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("4000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(resultHolding.accountCode(), resultHolding);
    book.accounts.put(revenue.accountCode(), revenue);

    PostingDraft closingCommand =
        new PostingDraft(
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    line("4000", JournalLine.EntrySide.DEBIT, "10.00"),
                    line("3200", JournalLine.EntrySide.CREDIT, "10.00"))),
            PostingLineageModel.direct(),
            PostingKind.PERIOD_RESULT_TRANSFER,
            dev.erst.fingrind.core.PostingOriginKind.PERIOD_RESULT_TRANSFER,
            generatedEvidence("idem-close", "period-result-transfer-plan"),
            new dev.erst.fingrind.core.CommittedProvenance(
                new RequestProvenance(
                    new ActorId("actor-1"),
                    ActorType.SYSTEM,
                    new CommandId("command-close"),
                    new IdempotencyKey("idem-close"),
                    new CausationId("cause-close"),
                    Optional.of(new CorrelationId("corr-close"))),
                Instant.parse("2026-04-07T10:15:30Z"),
                SourceChannel.SYSTEM));

    assertEquals(Optional.empty(), POSTING_ACCEPTANCE_POLICY.rejectionFor(closingCommand, book));
  }

  @Test
  void rejectionFor_acceptsActiveOrdinaryAccountsAfterTheClosedThroughBoundary() {
    RecordingValidationBook book = new RecordingValidationBook();
    book.initialized = true;
    book.closedThrough = Optional.of(LocalDate.parse("2026-04-06"));
    RegisteredAccount cash =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    RegisteredAccount revenue =
        registeredAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    book.accounts.put(cash.accountCode(), cash);
    book.accounts.put(revenue.accountCode(), revenue);

    assertEquals(
        Optional.empty(), POSTING_ACCEPTANCE_POLICY.rejectionFor(command("idem-open"), book));
  }

  private static PostingCommand command(String idempotencyKey) {
    return command(
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
        idempotencyKey,
        SourceChannel.CLI,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static PostingCommand command(String idempotencyKey, List<JournalLine> lines) {
    return command(
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
        idempotencyKey,
        SourceChannel.CLI,
        lines);
  }

  private static PostingCommand command(
      PostingKind postingKind,
      dev.erst.fingrind.core.PostingOriginKind postingOriginKind,
      String idempotencyKey,
      SourceChannel sourceChannel,
      List<JournalLine> lines) {
    return new PostingCommand(
        postingKind,
        postingOriginKind,
        new JournalEntry(LocalDate.parse("2026-04-07"), lines),
        PostingLineageModel.direct(),
        accountingEvidence(idempotencyKey),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        sourceChannel);
  }

  private static CommittedPosting existingPosting(String postingId, String idempotencyKey) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
        accountingEvidence(idempotencyKey),
        new dev.erst.fingrind.core.CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return line(accountCode, side, "EUR", amount);
  }

  private static JournalLine line(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse(currencyCode, amount));
  }

  /** Validation-book double that exposes the batch account lookup path explicitly. */
  private static final class RecordingValidationBook implements PostingValidationStore {
    private final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();
    private boolean initialized;
    private Optional<CommittedPosting> existingPosting = Optional.empty();
    private Optional<LocalDate> closedThrough = Optional.empty();
    private List<CommittedPosting> postings = List.of();
    private int findAccountsCalls;
    private List<AccountCode> requestedAccounts = List.of();

    @Override
    public BookLifecycleInspection inspectBook() {
      return initialized
          ? initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"))
          : new BookLifecycleInspection.Missing(1);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      throw new AssertionError("findAccount should not be used when batch lookup is available");
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      findAccountsCalls++;
      requestedAccounts = List.copyOf(accountCodes);
      Map<AccountCode, RegisteredAccount> matchedAccounts =
          new java.util.concurrent.ConcurrentHashMap<>();
      for (AccountCode accountCode : accountCodes) {
        RegisteredAccount account = accounts.get(accountCode);
        if (account != null) {
          matchedAccounts.put(accountCode, account);
        }
      }
      return Map.copyOf(matchedAccounts);
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return existingPosting;
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return postings;
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return closedThrough;
    }
  }

  /** Validation-book double that exercises the default single-account fallback lookup path. */
  private static final class FallbackValidationBook implements PostingValidationStore {
    private final Map<AccountCode, RegisteredAccount> accounts = new ConcurrentHashMap<>();
    private List<AccountCode> requestedAccounts = List.of();

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(1001, 1, 1, Instant.parse("2026-04-07T10:15:30Z"));
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      requestedAccounts =
          java.util.stream.Stream.concat(
                  requestedAccounts.stream(), java.util.stream.Stream.of(accountCode))
              .toList();
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }
}

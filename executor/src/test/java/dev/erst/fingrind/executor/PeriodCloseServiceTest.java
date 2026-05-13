package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.line;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for close-period bookkeeping generation and rejection rules. */
class PeriodCloseServiceTest {
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private static final LocalDate OPENING_DATE = LocalDate.parse("2026-04-01");
  private static final LocalDate PERIOD_DATE = LocalDate.parse("2026-04-07");
  private static final ReportingPeriod PERIOD = new ReportingPeriod(PERIOD_DATE, PERIOD_DATE);
  private static final ReportingPeriod FULL_PERIOD = new ReportingPeriod(OPENING_DATE, PERIOD_DATE);

  @Test
  void closePeriod_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PeriodCloseOutcome outcome = service(bookSession).closePeriod(PERIOD);

      assertEquals(
          new PeriodCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          outcome);
    }
  }

  @Test
  void closePeriod_rejectsMissingRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
      declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
      declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
      declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
      seedProfitAndLossPosting(bookSession);

      PeriodCloseOutcome outcome = service(bookSession).closePeriod(FULL_PERIOD);

      assertEquals(
          new PeriodCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.RetainedEarningsAccountMissing()),
          outcome);
    }
  }

  @Test
  void closePeriod_rejectsInactiveRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      bookSession.deactivateAccount(new AccountCode("3200"));
      seedProfitAndLossPosting(bookSession);

      PeriodCloseOutcome outcome = service(bookSession).closePeriod(PERIOD);

      assertEquals(
          new PeriodCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.RetainedEarningsAccountInactive(
                  new AccountCode("3200"))),
          outcome);
    }
  }

  @Test
  void closePeriod_rejectsMoreThanOneRetainedEarningsAccount() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareAccount(
          bookSession,
          "3200",
          "Retained Earnings A",
          AccountType.EQUITY,
          AccountRole.RETAINED_EARNINGS);
      declareAccount(
          bookSession,
          "3210",
          "Retained Earnings B",
          AccountType.EQUITY,
          AccountRole.RETAINED_EARNINGS);

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> service(bookSession).closePeriod(PERIOD));

      assertEquals(
          "Retained-earnings account lookup returned more than one account.", failure.getMessage());
    }
  }

  @Test
  void closePeriod_allowsFirstCloseToStartBeforeEarliestPosting() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      commitPosting(
          bookSession,
          "posting-sale",
          "idem-sale",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "50.00"),
              line("4000", JournalLine.EntrySide.CREDIT, "50.00")));

      PeriodCloseOutcome outcome = service(bookSession).closePeriod(FULL_PERIOD);
      dev.erst.fingrind.executor.bookkeeping.ClosedPeriod closedPeriod =
          assertInstanceOf(PeriodCloseOutcome.Closed.class, outcome).closedPeriod();

      assertEquals(FULL_PERIOD, closedPeriod.reportingPeriod());
    }
  }

  @Test
  void closePeriod_rejectsNonContiguousStartAfterExistingClose() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);
      assertInstanceOf(
          PeriodCloseOutcome.Closed.class, service(bookSession).closePeriod(FULL_PERIOD));

      PeriodCloseOutcome outcome =
          service(bookSession)
              .closePeriod(new ReportingPeriod(PERIOD_DATE.plusDays(2), PERIOD_DATE.plusDays(2)));

      assertEquals(
          new PeriodCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.PeriodCloseMustStartAt(
                  PERIOD_DATE.plusDays(1))),
          outcome);
    }
  }

  @Test
  void closePeriod_allowsEmptyDraftWhenNoProfitAndLossAccountsMoved() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      commitPosting(
          bookSession,
          "posting-open",
          "idem-open",
          PERIOD_DATE,
          List.of(
              line("1000", JournalLine.EntrySide.DEBIT, "50.00"),
              line("3000", JournalLine.EntrySide.CREDIT, "50.00")));

      PeriodCloseOutcome outcome = service(bookSession).closePeriod(PERIOD);
      dev.erst.fingrind.executor.bookkeeping.ClosedPeriod closedPeriod =
          assertInstanceOf(PeriodCloseOutcome.Closed.class, outcome).closedPeriod();

      assertEquals(1, closedPeriod.closeOrder());
      assertEquals(List.of(), closedPeriod.closingPostingIds());
    }
  }

  @Test
  void closePeriod_generatesOnePeriodClosePostingAndIgnoresPriorCloseFacts() {
    try (InMemoryBookSession bookSession = openedBook()) {
      declareRetainedEarningsFixture(bookSession);
      seedProfitAndLossPosting(bookSession);

      dev.erst.fingrind.executor.bookkeeping.ClosedPeriod firstClose =
          assertInstanceOf(
                  PeriodCloseOutcome.Closed.class, service(bookSession).closePeriod(FULL_PERIOD))
              .closedPeriod();
      CommittedPosting closingPosting =
          bookSession.findPosting(firstClose.closingPostingIds().getFirst()).orElseThrow();

      assertEquals(PostingKind.PERIOD_CLOSE, closingPosting.postingKind());
      assertEquals(
          new JournalEntry(
              PERIOD_DATE,
              List.of(
                  line("4000", JournalLine.EntrySide.DEBIT, "120.00"),
                  line("5000", JournalLine.EntrySide.CREDIT, "45.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "75.00"))),
          closingPosting.journalEntry());

      PeriodCloseOutcome secondClose =
          service(bookSession)
              .closePeriod(new ReportingPeriod(PERIOD_DATE.plusDays(1), PERIOD_DATE.plusDays(1)));

      assertEquals(
          new PeriodCloseOutcome.Closed(
              new dev.erst.fingrind.executor.bookkeeping.ClosedPeriod(
                  2,
                  new ReportingPeriod(PERIOD_DATE.plusDays(1), PERIOD_DATE.plusDays(1)),
                  new AccountCode("3200"),
                  List.of(),
                  FIXED_INSTANT,
                  List.of())),
          secondClose);
    }
  }

  @Test
  void
      closePeriod_skipsNonStandardUnknownAndZeroedTemporaryBuckets_andOrdersGeneratedDraftsByCurrency() {
    RecordingCloseBook book = new RecordingCloseBook();
    book.accounts =
        List.of(
            account("1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY),
            account("3200", "Retained Earnings", AccountType.EQUITY, AccountRole.RETAINED_EARNINGS),
            account("4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY),
            account("5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY));
    book.postings =
        List.of(
            posting(
                "existing-close",
                PostingKind.PERIOD_CLOSE,
                PERIOD_DATE,
                List.of(
                    moneyLine("4000", JournalLine.EntrySide.DEBIT, "EUR", "1.00"),
                    moneyLine("3200", JournalLine.EntrySide.CREDIT, "EUR", "1.00"))),
            posting(
                "eur-revenue-credit",
                PostingKind.STANDARD,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                    moneyLine("4000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"))),
            posting(
                "eur-revenue-debit",
                PostingKind.STANDARD,
                PERIOD_DATE,
                List.of(
                    moneyLine("4000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                    moneyLine("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"))),
            posting(
                "eur-unknown",
                PostingKind.STANDARD,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "EUR", "9.00"),
                    moneyLine("9999", JournalLine.EntrySide.CREDIT, "EUR", "9.00"))),
            posting(
                "usd-revenue",
                PostingKind.STANDARD,
                PERIOD_DATE,
                List.of(
                    moneyLine("1000", JournalLine.EntrySide.DEBIT, "USD", "30.00"),
                    moneyLine("4000", JournalLine.EntrySide.CREDIT, "USD", "30.00"))),
            posting(
                "bhd-expense",
                PostingKind.STANDARD,
                PERIOD_DATE,
                List.of(
                    moneyLine("5000", JournalLine.EntrySide.DEBIT, "BHD", "7.000"),
                    moneyLine("1000", JournalLine.EntrySide.CREDIT, "BHD", "7.000"))));

    PeriodCloseOutcome outcome = service(book).closePeriod(PERIOD);
    dev.erst.fingrind.executor.bookkeeping.ClosedPeriod closedPeriod =
        assertInstanceOf(PeriodCloseOutcome.Closed.class, outcome).closedPeriod();

    assertEquals(
        new dev.erst.fingrind.executor.bookkeeping.ClosedPeriod(
            1,
            PERIOD,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("BHD", "7.000"), Money.parse("BHD", "0.000")),
                CurrencyBalance.ofTotals(Money.parse("USD", "0.00"), Money.parse("USD", "30.00"))),
            FIXED_INSTANT,
            List.of(new PostingId("generated-1"), new PostingId("generated-2"))),
        closedPeriod);
    assertEquals(
        new PeriodCloseDraft(
            PERIOD,
            new AccountCode("3200"),
            List.of(
                CurrencyBalance.ofTotals(Money.parse("BHD", "7.000"), Money.parse("BHD", "0.000")),
                CurrencyBalance.ofTotals(Money.parse("USD", "0.00"), Money.parse("USD", "30.00"))),
            FIXED_INSTANT,
            List.of(
                new PostingDraft(
                    new JournalEntry(
                        PERIOD_DATE,
                        List.of(
                            moneyLine("5000", JournalLine.EntrySide.CREDIT, "BHD", "7.000"),
                            moneyLine("3200", JournalLine.EntrySide.DEBIT, "BHD", "7.000"))),
                    PostingLineageModel.direct(),
                    PostingKind.PERIOD_CLOSE,
                    periodCloseProvenance("BHD")),
                new PostingDraft(
                    new JournalEntry(
                        PERIOD_DATE,
                        List.of(
                            moneyLine("4000", JournalLine.EntrySide.DEBIT, "USD", "30.00"),
                            moneyLine("3200", JournalLine.EntrySide.CREDIT, "USD", "30.00"))),
                    PostingLineageModel.direct(),
                    PostingKind.PERIOD_CLOSE,
                    periodCloseProvenance("USD")))),
        book.recordedDraft);
  }

  private static PeriodCloseService service(InMemoryBookSession bookSession) {
    PostingIdGenerator postingIdGenerator = new SequencePostingIdGenerator();
    return new PeriodCloseService(bookSession, postingIdGenerator, FIXED_CLOCK);
  }

  private static PeriodCloseService service(RecordingCloseBook bookStore) {
    return new PeriodCloseService(bookStore, new SequencePostingIdGenerator(), FIXED_CLOCK);
  }

  private static InMemoryBookSession openedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT);
    return bookSession;
  }

  private static void declareRetainedEarningsFixture(InMemoryBookSession bookSession) {
    declareAccount(bookSession, "1000", "Cash", AccountType.ASSET, AccountRole.ORDINARY);
    declareAccount(bookSession, "3000", "Capital", AccountType.EQUITY, AccountRole.ORDINARY);
    declareAccount(
        bookSession,
        "3200",
        "Retained Earnings",
        AccountType.EQUITY,
        AccountRole.RETAINED_EARNINGS);
    declareAccount(bookSession, "4000", "Revenue", AccountType.REVENUE, AccountRole.ORDINARY);
    declareAccount(bookSession, "5000", "Expense", AccountType.EXPENSE, AccountRole.ORDINARY);
  }

  private static void declareAccount(
      InMemoryBookSession bookSession,
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole) {
    assertInstanceOf(
        AccountDeclarationOutcome.Declared.class,
        bookSession.declareAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            accountType,
            accountRole,
            FIXED_INSTANT));
  }

  private static void seedProfitAndLossPosting(InMemoryBookSession bookSession) {
    commitPosting(
        bookSession,
        "posting-open",
        "idem-open",
        OPENING_DATE,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "100.00"),
            line("3000", JournalLine.EntrySide.CREDIT, "100.00")));
    commitPosting(
        bookSession,
        "posting-sale",
        "idem-sale",
        PERIOD_DATE,
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "120.00"),
            line("4000", JournalLine.EntrySide.CREDIT, "120.00")));
    commitPosting(
        bookSession,
        "posting-expense",
        "idem-expense",
        PERIOD_DATE,
        List.of(
            line("5000", JournalLine.EntrySide.DEBIT, "45.00"),
            line("1000", JournalLine.EntrySide.CREDIT, "45.00")));
  }

  private static RegisteredAccount account(
      String accountCode, String accountName, AccountType accountType, AccountRole accountRole) {
    return new RegisteredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountRole,
        true,
        FIXED_INSTANT);
  }

  private static JournalLine moneyLine(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse(currencyCode, amount));
  }

  private static CommittedPosting posting(
      String postingId, PostingKind postingKind, LocalDate effectiveDate, List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        postingKind,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-" + postingId),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey("idem-" + postingId),
                new CausationId("cause-" + postingId),
                Optional.of(new CorrelationId("corr-" + postingId))),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static CommittedProvenance periodCloseProvenance(String currencyCode) {
    String closeToken = PERIOD_DATE + ":" + PERIOD_DATE + ":" + FIXED_INSTANT.toEpochMilli();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new ActorId("system:periodClose"),
            ActorType.SYSTEM,
            new CommandId("periodClose:" + closeToken + ":" + currencyCode),
            new IdempotencyKey("periodClose:" + closeToken + ":" + currencyCode),
            new CausationId("periodClose:" + closeToken),
            Optional.of(new CorrelationId("periodClose:" + closeToken)));
    return new CommittedProvenance(requestProvenance, FIXED_INSTANT, SourceChannel.CLI);
  }

  private static void commitPosting(
      InMemoryBookSession bookSession,
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    CommittedPosting posting =
        new CommittedPosting(
            new PostingId(postingId),
            new JournalEntry(effectiveDate, lines),
            PostingLineageModel.direct(),
            PostingKind.STANDARD,
            new CommittedProvenance(
                new RequestProvenance(
                    new dev.erst.fingrind.core.ActorId("actor-" + postingId),
                    dev.erst.fingrind.core.ActorType.AGENT,
                    new dev.erst.fingrind.core.CommandId("command-" + postingId),
                    new dev.erst.fingrind.core.IdempotencyKey(idempotencyKey),
                    new dev.erst.fingrind.core.CausationId("cause-" + postingId),
                    Optional.empty()),
                FIXED_INSTANT,
                SourceChannel.CLI));
    assertInstanceOf(PostingCommitResult.Committed.class, bookSession.commit(posting));
  }

  /** Deterministic posting id generator for generated close postings. */
  private static final class SequencePostingIdGenerator implements PostingIdGenerator {
    private int nextValue = 1;

    @Override
    public PostingId nextPostingId() {
      int currentValue = nextValue;
      nextValue = currentValue + 1;
      return new PostingId("period-close-" + currentValue);
    }
  }

  /** Recording book double that captures generated close drafts and account/posting inputs. */
  private static final class RecordingCloseBook implements BookStore {
    private List<RegisteredAccount> accounts = List.of();
    private List<CommittedPosting> postings = List.of();
    private PeriodCloseDraft recordedDraft =
        new PeriodCloseDraft(PERIOD, new AccountCode("3200"), List.of(), FIXED_INSTANT, List.of());

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(
          BookFormatContract.APPLICATION_ID,
          BookFormatContract.FORMAT_VERSION,
          BookFormatContract.FORMAT_VERSION,
          FIXED_INSTANT);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return accounts.stream()
          .filter(account -> account.accountCode().equals(accountCode))
          .findFirst();
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return postings.stream().filter(posting -> posting.postingId().equals(postingId)).findFirst();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return accounts;
    }

    @Override
    public List<CommittedPosting> postings(
        dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return postings.stream()
          .filter(posting -> effectiveDateRange.contains(posting.journalEntry().effectiveDate()))
          .toList();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return postings.stream()
          .map(posting -> posting.journalEntry().effectiveDate())
          .min(LocalDate::compareTo);
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public PeriodCloseOutcome closePeriod(
        PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
      recordedDraft = periodCloseDraft;
      List<PostingId> generatedPostingIds = new ArrayList<>();
      for (int index = 0; index < periodCloseDraft.closingPostings().size(); index++) {
        generatedPostingIds.add(new PostingId("generated-" + (index + 1)));
      }
      return new PeriodCloseOutcome.Closed(
          new dev.erst.fingrind.executor.bookkeeping.ClosedPeriod(
              1,
              periodCloseDraft.reportingPeriod(),
              periodCloseDraft.retainedEarningsAccountCode(),
              periodCloseDraft.closedTotals(),
              periodCloseDraft.closedAt(),
              generatedPostingIds));
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome openBook(
        Instant initializedAt) {
      throw unsupported();
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        Instant declaredAt) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage listPostings(
        dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery query) {
      throw unsupported();
    }

    @Override
    public Optional<dev.erst.fingrind.executor.bookkeeping.AccountBalanceView> accountBalance(
        dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria query) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.TrialBalanceView trialBalance(
        dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria query) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountLedgerView accountLedger(
        dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria query,
        RegisteredAccount account) {
      throw unsupported();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView periodSummary(
        dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria query) {
      throw unsupported();
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      throw unsupported();
    }

    private static AssertionError unsupported() {
      return new AssertionError("This close-service test double does not support that seam.");
    }
  }
}

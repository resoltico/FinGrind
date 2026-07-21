package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Exercises every owned lifecycle context through executor resolution, retained state, and reads.
 */
class OwnedLifecycleContextIntegrationTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC);
  private static final AccountCode CASH = new AccountCode("cash");
  private static final AccountCode ASSET = new AccountCode("fixed-asset");
  private static final AccountCode ACCUMULATED_DEPRECIATION =
      new AccountCode("accumulated-depreciation");
  private static final AccountCode DEPRECIATION_EXPENSE = new AccountCode("depreciation-expense");
  private static final AccountCode DISPOSAL_GAIN = new AccountCode("disposal-gain");
  private static final AccountCode DISPOSAL_LOSS = new AccountCode("disposal-loss");
  private static final AccountCode FINANCING_PRINCIPAL = new AccountCode("financing-principal");
  private static final AccountCode FINANCING_INTEREST_PAYABLE =
      new AccountCode("financing-interest-payable");
  private static final AccountCode FINANCING_INTEREST_EXPENSE =
      new AccountCode("financing-interest-expense");
  private static final AccountCode FX_RECEIVABLE = new AccountCode("foreign-receivable");
  private static final AccountCode FX_REVENUE = new AccountCode("foreign-revenue");
  private static final AccountCode FX_GAIN = new AccountCode("foreign-exchange-gain");
  private static final AccountCode FX_LOSS = new AccountCode("foreign-exchange-loss");
  private static final FixedAssetId FIXED_ASSET_ID = new FixedAssetId("asset-vehicle-001");
  private static final FinancingArrangementId FINANCING_ID =
      new FinancingArrangementId("loan-working-capital-001");
  private static final ForeignCurrencyObligationId FX_OBLIGATION_ID =
      new ForeignCurrencyObligationId("receivable-usd-001");

  @Test
  void ownedLifecycleContexts_resolvePersistAndPublishTheirExactState() {
    try (InMemoryBookSession session = initializedBook()) {
      declareLifecycleAccounts(session);
      PostingApplicationService service = postingService(session);

      PostEntryResult.Committed capitalization =
          commit(service, fixedAssetCapitalization(), "fixed-capitalization", "supplier-invoice");
      PostEntryResult.Committed depreciation =
          commit(service, fixedAssetDepreciation(), "fixed-depreciation", "depreciation-schedule");
      PostEntryResult.Committed disposal =
          commit(service, fixedAssetDisposal(), "fixed-disposal", "cash-receipt");
      PostEntryResult.Committed borrowing =
          commit(service, financingBorrowing(), "financing-borrowing", "loan-agreement");
      PostEntryResult.Committed principalRepayment =
          commit(
              service,
              financingPrincipalRepayment(),
              "financing-principal-repayment",
              "loan-statement");
      PostEntryResult.Committed interestAccrual =
          commit(
              service,
              financingInterestAccrual(),
              "financing-interest-accrual",
              "interest-calculation");
      assertInstanceOf(
          FinancingBookkeepingEntryVariants.InterestAccrual.class,
          session
              .findPosting(interestAccrual.postingId())
              .orElseThrow()
              .resolvedOriginatingEntry()
              .orElseThrow());
      assertEquals(
          Money.parse("EUR", "5.00"),
          session.findFinancingArrangement(FINANCING_ID).orElseThrow().outstandingInterest());
      PostEntryResult.Committed interestPayment =
          commit(
              service, financingInterestPayment(), "financing-interest-payment", "loan-statement");
      PostEntryResult.Committed obligation =
          commit(
              service,
              foreignCurrencyObligation(),
              "foreign-currency-obligation",
              "foreign-currency-invoice");
      PostEntryResult.Committed settlement =
          commit(
              service,
              foreignExchangeSettlement(),
              "foreign-exchange-settlement",
              "settlement-confirmation");

      assertJournalLines(capitalization, List.of("fixed-asset:DEBIT:120.00", "cash:CREDIT:120.00"));
      assertJournalLines(
          depreciation,
          List.of("depreciation-expense:DEBIT:10.00", "accumulated-depreciation:CREDIT:10.00"));
      assertJournalLines(
          disposal,
          List.of(
              "cash:DEBIT:110.00",
              "accumulated-depreciation:DEBIT:10.00",
              "fixed-asset:CREDIT:120.00"));
      assertJournalLines(
          borrowing, List.of("cash:DEBIT:100.00", "financing-principal:CREDIT:100.00"));
      assertJournalLines(
          principalRepayment, List.of("financing-principal:DEBIT:40.00", "cash:CREDIT:40.00"));
      assertJournalLines(
          interestAccrual,
          List.of(
              "financing-interest-expense:DEBIT:5.00", "financing-interest-payable:CREDIT:5.00"));
      assertJournalLines(
          interestPayment, List.of("financing-interest-payable:DEBIT:5.00", "cash:CREDIT:5.00"));
      assertJournalLines(
          obligation, List.of("foreign-receivable:DEBIT:92.00", "foreign-revenue:CREDIT:92.00"));
      assertJournalLines(
          settlement,
          List.of(
              "cash:DEBIT:95.00",
              "foreign-receivable:CREDIT:92.00",
              "foreign-exchange-gain:CREDIT:3.00"));

      assertPublishedRegisters(session);
      assertResolvedReadback(session, depreciation, principalRepayment, settlement);
    }
  }

  @Test
  void resolution_support_returnsEachOwnedLifecycleRejectionBeforeJournalCompletion() {
    try (InMemoryBookSession session = initializedBook()) {
      declareLifecycleAccounts(session);
      PostingApplicationService service = postingService(session);
      commit(service, fixedAssetCapitalization(), "fixed-capitalization", "supplier-invoice");
      commit(service, financingBorrowing(), "financing-borrowing", "loan-agreement");
      commit(
          service,
          foreignCurrencyObligation(),
          "foreign-currency-obligation",
          "foreign-currency-invoice");

      assertResolutionRejection(
          PostEntryResolutionSupport.resolve(fixedAssetCapitalization(), session),
          "fixed-asset-id-already-exists");
      assertResolutionRejection(
          PostEntryResolutionSupport.resolve(financingBorrowing(), session),
          "financing-arrangement-id-already-exists");
      assertResolutionRejection(
          PostEntryResolutionSupport.resolve(foreignCurrencyObligation(), session),
          "foreign-currency-obligation-id-already-exists");
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession session = new InMemoryBookSession();
    session.openBook(
        CLOCK.instant(),
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Lifecycle Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            java.time.LocalDate.parse("2026-01-01")),
        List.of());
    return session;
  }

  private static PostingApplicationService postingService(InMemoryBookSession session) {
    AtomicInteger sequence = new AtomicInteger();
    return new PostingApplicationService(
        session, session, () -> new PostingId("lifecycle-" + sequence.incrementAndGet()), CLOCK);
  }

  private static PostEntryResult.Committed commit(
      PostingApplicationService service,
      BookkeepingEntry entry,
      String token,
      String sourceDocumentType) {
    PostEntryResult result =
        service.commit(
            new PostEntryCommand(
                entry,
                evidence(token, sourceDocumentType, entry.effectiveDate()),
                provenance(token),
                SourceChannel.CLI));
    return assertInstanceOf(
        PostEntryResult.Committed.class,
        result,
        () -> "Expected lifecycle entry to commit, but received: " + result);
  }

  private static AccountingEvidence evidence(
      String token, String sourceDocumentType, LocalDate effectiveDate) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("lifecycle-evidence-" + token),
                new SourceDocumentType(sourceDocumentType),
                effectiveDate)),
        List.of());
  }

  private static RequestProvenance provenance(String token) {
    return new RequestProvenance(
        new CommandId("lifecycle-command-" + token),
        new IdempotencyKey("lifecycle-idempotency-" + token),
        new CausationId("lifecycle-cause-" + token),
        Optional.empty());
  }

  private static void assertPublishedRegisters(InMemoryBookSession session) {
    BookReadService reads = new BookReadService(session);
    FixedAssetRegisterResult.Reported fixedAssets =
        assertInstanceOf(
            FixedAssetRegisterResult.Reported.class,
            reads.fixedAssetRegister(new FixedAssetRegisterQuery(Optional.empty())));
    assertEquals(1, fixedAssets.report().rows().size());
    assertEquals(
        MonetaryAmount.of(Money.parse("EUR", "0.00")),
        fixedAssets.report().rows().getFirst().carryingAmount());
    assertEquals(
        Optional.of(MonetaryAmount.of(Money.parse("EUR", "110.00"))),
        fixedAssets.report().rows().getFirst().carryingAmountAtDisposal());
    assertEquals(
        Optional.of(LocalDate.parse("2026-07-01")),
        fixedAssets.report().rows().getFirst().disposedOn());

    FinancingRegisterResult.Reported financing =
        assertInstanceOf(
            FinancingRegisterResult.Reported.class,
            reads.financingRegister(new FinancingRegisterQuery()));
    assertEquals(1, financing.report().rows().size());
    assertEquals(
        MonetaryAmount.of(Money.parse("EUR", "60.00")),
        financing.report().rows().getFirst().principalOutstanding());
    assertEquals(
        MonetaryAmount.of(Money.parse("EUR", "0.00")),
        financing.report().rows().getFirst().interestOutstanding());

    RealizedForeignExchangeRegisterResult.Reported foreignExchange =
        assertInstanceOf(
            RealizedForeignExchangeRegisterResult.Reported.class,
            reads.realizedForeignExchangeRegister(new RealizedForeignExchangeRegisterQuery()));
    assertEquals(1, foreignExchange.report().rows().size());
    assertEquals(
        Optional.of(MonetaryAmount.of(Money.parse("EUR", "3.00"))),
        foreignExchange.report().rows().getFirst().realizedGainOrLossAmount());
  }

  private static void assertResolvedReadback(
      InMemoryBookSession session,
      PostEntryResult.Committed depreciation,
      PostEntryResult.Committed principalRepayment,
      PostEntryResult.Committed settlement) {
    BookReadService reads = new BookReadService(session);
    assertInstanceOf(
        FixedAssetBookkeepingEntryVariants.Depreciation.class,
        found(reads, depreciation.postingId()).postingFact().callerAuthoredEntry().orElseThrow());
    assertInstanceOf(
        FinancingBookkeepingEntryVariants.PrincipalRepayment.class,
        found(reads, principalRepayment.postingId())
            .postingFact()
            .callerAuthoredEntry()
            .orElseThrow());
    assertInstanceOf(
        RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class,
        found(reads, settlement.postingId()).postingFact().callerAuthoredEntry().orElseThrow());
  }

  private static GetPostingResult.Found found(BookReadService reads, PostingId postingId) {
    return assertInstanceOf(GetPostingResult.Found.class, reads.getPosting(postingId));
  }

  private static void assertResolutionRejection(
      PostEntryResolutionSupport.ResolutionOutcome outcome, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            outcome.rejection().orElseThrow());
    assertEquals(expectedCode, rejection.violations().getFirst().code());
  }

  private static void assertJournalLines(
      PostEntryResult.Committed committed, List<String> expectedLines) {
    assertEquals(
        expectedLines,
        committed.resolvedJournal().expandedLines().lines().stream()
            .map(
                line ->
                    line.accountCode().value()
                        + ":"
                        + line.side()
                        + ":"
                        + line.amount().money().canonicalDecimal())
            .toList());
  }

  private static FixedAssetBookkeepingEntryVariants.Capitalization fixedAssetCapitalization() {
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        LocalDate.parse("2026-06-01"),
        FIXED_ASSET_ID,
        ASSET,
        ACCUMULATED_DEPRECIATION,
        DEPRECIATION_EXPENSE,
        DISPOSAL_GAIN,
        DISPOSAL_LOSS,
        CASH,
        MonetaryAmount.of(Money.parse("EUR", "120.00")),
        new FixedAssetDepreciationSchedule(
            LocalDate.parse("2026-06-01"), 12, MonetaryAmount.of(Money.parse("EUR", "0.00"))));
  }

  private static FixedAssetBookkeepingEntryVariants.Depreciation fixedAssetDepreciation() {
    return new FixedAssetBookkeepingEntryVariants.Depreciation(
        LocalDate.parse("2026-06-30"), FIXED_ASSET_ID, null);
  }

  private static FixedAssetBookkeepingEntryVariants.Disposal fixedAssetDisposal() {
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        LocalDate.parse("2026-07-01"),
        FIXED_ASSET_ID,
        CASH,
        MonetaryAmount.of(Money.parse("EUR", "110.00")),
        null);
  }

  private static FinancingBookkeepingEntryVariants.Borrowing financingBorrowing() {
    return new FinancingBookkeepingEntryVariants.Borrowing(
        LocalDate.parse("2026-06-01"),
        FINANCING_ID,
        CASH,
        FINANCING_PRINCIPAL,
        FINANCING_INTEREST_PAYABLE,
        MonetaryAmount.of(Money.parse("EUR", "100.00")));
  }

  private static FinancingBookkeepingEntryVariants.PrincipalRepayment
      financingPrincipalRepayment() {
    return new FinancingBookkeepingEntryVariants.PrincipalRepayment(
        LocalDate.parse("2026-06-02"),
        FINANCING_ID,
        CASH,
        MonetaryAmount.of(Money.parse("EUR", "40.00")),
        null);
  }

  private static FinancingBookkeepingEntryVariants.InterestAccrual financingInterestAccrual() {
    return new FinancingBookkeepingEntryVariants.InterestAccrual(
        LocalDate.parse("2026-06-03"),
        FINANCING_ID,
        FINANCING_INTEREST_EXPENSE,
        MonetaryAmount.of(Money.parse("EUR", "5.00")),
        null);
  }

  private static FinancingBookkeepingEntryVariants.InterestPayment financingInterestPayment() {
    return new FinancingBookkeepingEntryVariants.InterestPayment(
        LocalDate.parse("2026-06-04"),
        FINANCING_ID,
        CASH,
        MonetaryAmount.of(Money.parse("EUR", "5.00")),
        null);
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
      foreignCurrencyObligation() {
    return new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
        LocalDate.parse("2026-07-01"),
        FX_OBLIGATION_ID,
        FX_RECEIVABLE,
        FX_REVENUE,
        FX_GAIN,
        FX_LOSS,
        foreignExchangeDetails("92.00", "2026-07-01"));
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.Settlement
      foreignExchangeSettlement() {
    return new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
        LocalDate.parse("2026-07-03"),
        FX_OBLIGATION_ID,
        CASH,
        foreignExchangeDetails("95.00", "2026-07-03"),
        null);
  }

  private static ForeignExchangeDetails foreignExchangeDetails(
      String functionalAmount, String quotedOn) {
    MonetaryAmount transaction = MonetaryAmount.of(Money.parse("USD", "100.00"));
    MonetaryAmount functional = MonetaryAmount.of(Money.parse("EUR", functionalAmount));
    return new ForeignExchangeDetails(
        transaction,
        functional,
        new QuotedExchangeRate(transaction, functional, LocalDate.parse(quotedOn), "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static void declareLifecycleAccounts(InMemoryBookSession session) {
    declareCash(session, CASH, "Cash");
    declare(session, ASSET, "Fixed Asset", AccountType.ASSET, nonCurrentAssetTaxonomy());
    declare(
        session,
        ACCUMULATED_DEPRECIATION,
        "Accumulated Depreciation",
        AccountType.ASSET,
        nonCurrentAssetTaxonomy());
    declare(
        session,
        DEPRECIATION_EXPENSE,
        "Depreciation Expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declare(
        session,
        DISPOSAL_GAIN,
        "Disposal Gain",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
    declare(
        session,
        DISPOSAL_LOSS,
        "Disposal Loss",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declare(
        session,
        FINANCING_PRINCIPAL,
        "Financing Principal",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    declare(
        session,
        FINANCING_INTEREST_PAYABLE,
        "Financing Interest Payable",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY));
    declare(
        session,
        FINANCING_INTEREST_EXPENSE,
        "Financing Interest Expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declare(
        session,
        FX_RECEIVABLE,
        "Foreign Receivable",
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE));
    declare(
        session,
        FX_REVENUE,
        "Foreign Revenue",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
    declare(
        session,
        FX_GAIN,
        "Foreign Exchange Gain",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
    declare(
        session,
        FX_LOSS,
        "Foreign Exchange Loss",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
  }

  private static AccountTaxonomy nonCurrentAssetTaxonomy() {
    return financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET);
  }

  private static void declareCash(InMemoryBookSession session, AccountCode code, String name) {
    declare(
        session,
        code,
        name,
        AccountType.ASSET,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
  }

  private static void declare(
      InMemoryBookSession session,
      AccountCode code,
      String name,
      AccountType type,
      AccountTaxonomy taxonomy) {
    session.declareAccount(code, new AccountName(name), type, taxonomy, CLOCK.instant());
  }
}

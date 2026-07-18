package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.accountTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.sqlite.SqlitePostingFactFixtureSupport.generatedEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises each owned lifecycle aggregate through the protected SQLite posting boundary. */
class SqliteOwnedLifecycleContextIntegrationTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-15T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);

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
  private static final ForeignCurrencyObligationId FX_LOSS_OBLIGATION_ID =
      new ForeignCurrencyObligationId("receivable-usd-002");

  @Test
  void ownedLifecycleCommands_resolvePersistAndRehydrateAgainstTheProtectedBook() {
    Path bookPath = tempDirectory.resolve("owned-lifecycle-contexts.sqlite");
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("owned-lifecycle", TEST_BOOK_KEY.toCharArray());
        SqlitePostingSession store = SqlitePostingSessions.open(bookPath, passphrase)) {
      store.openBook(RECORDED_AT, accrualBookIdentity(), List.of());
      declareLifecycleAccounts(store);
      PostingApplicationService application =
          new PostingApplicationService(
              store,
              store,
              postingIds(
                  "fixed-capitalization",
                  "fixed-depreciation",
                  "fixed-disposal",
                  "financing-borrowing",
                  "financing-principal-repayment",
                  "financing-interest-accrual",
                  "financing-interest-payment",
                  "foreign-currency-obligation",
                  "foreign-exchange-settlement",
                  "foreign-currency-loss-obligation",
                  "foreign-exchange-loss-settlement"),
              CLOCK);

      PostEntryResult.Committed capitalization =
          commit(
              application, fixedAssetCapitalization(), "fixed-capitalization", "supplier-invoice");
      PostEntryResult.Committed depreciation =
          commit(
              application, fixedAssetDepreciation(), "fixed-depreciation", "depreciation-schedule");
      PostEntryResult.Committed disposal =
          commit(application, fixedAssetDisposal(), "fixed-disposal", "cash-receipt");
      PostEntryResult.Committed borrowing =
          commit(application, financingBorrowing(), "financing-borrowing", "loan-agreement");
      PostEntryResult.Committed principalRepayment =
          commit(
              application,
              financingPrincipalRepayment(),
              "financing-principal-repayment",
              "loan-statement");
      PostEntryResult.Committed interestAccrual =
          commit(
              application,
              financingInterestAccrual(),
              "financing-interest-accrual",
              "interest-calculation");
      PostEntryResult.Committed interestPayment =
          commit(
              application,
              financingInterestPayment(),
              "financing-interest-payment",
              "loan-statement");
      PostEntryResult.Committed obligation =
          commit(
              application,
              foreignCurrencyObligation(),
              "foreign-currency-obligation",
              "foreign-currency-invoice");
      assertUnsettledForeignExchangeState(store);
      PostEntryResult.Committed settlement =
          commit(
              application,
              foreignExchangeSettlement(),
              "foreign-exchange-settlement",
              "settlement-confirmation");
      PostEntryResult.Committed lossObligation =
          commit(
              application,
              foreignCurrencyLossObligation(),
              "foreign-currency-loss-obligation",
              "foreign-currency-invoice");
      PostEntryResult.Committed lossSettlement =
          commit(
              application,
              foreignExchangeLossSettlement(),
              "foreign-exchange-loss-settlement",
              "settlement-confirmation");

      assertEquals(new PostingId("fixed-capitalization"), capitalization.postingId());
      assertEquals(new PostingId("fixed-depreciation"), depreciation.postingId());
      assertEquals(new PostingId("fixed-disposal"), disposal.postingId());
      assertEquals(new PostingId("financing-borrowing"), borrowing.postingId());
      assertEquals(new PostingId("financing-principal-repayment"), principalRepayment.postingId());
      assertEquals(new PostingId("financing-interest-accrual"), interestAccrual.postingId());
      assertEquals(new PostingId("financing-interest-payment"), interestPayment.postingId());
      assertEquals(new PostingId("foreign-currency-obligation"), obligation.postingId());
      assertEquals(new PostingId("foreign-exchange-settlement"), settlement.postingId());
      assertEquals(new PostingId("foreign-currency-loss-obligation"), lossObligation.postingId());
      assertEquals(new PostingId("foreign-exchange-loss-settlement"), lossSettlement.postingId());

      assertFixedAssetState(store);
      assertFinancingState(store);
      assertForeignExchangeState(store);
      assertLossForeignExchangeState(store);
      assertPublishedRegisters(store);
      assertRehydratedEntries(
          store,
          List.of(
              new RehydratedEntryExpectation(
                  capitalization, FixedAssetBookkeepingEntryVariants.Capitalization.class),
              new RehydratedEntryExpectation(
                  depreciation, FixedAssetBookkeepingEntryVariants.Depreciation.class),
              new RehydratedEntryExpectation(
                  disposal, FixedAssetBookkeepingEntryVariants.Disposal.class),
              new RehydratedEntryExpectation(
                  borrowing, FinancingBookkeepingEntryVariants.Borrowing.class),
              new RehydratedEntryExpectation(
                  principalRepayment, FinancingBookkeepingEntryVariants.PrincipalRepayment.class),
              new RehydratedEntryExpectation(
                  interestAccrual, FinancingBookkeepingEntryVariants.InterestAccrual.class),
              new RehydratedEntryExpectation(
                  interestPayment, FinancingBookkeepingEntryVariants.InterestPayment.class),
              new RehydratedEntryExpectation(
                  obligation,
                  RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable.class),
              new RehydratedEntryExpectation(
                  settlement, RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class),
              new RehydratedEntryExpectation(
                  lossObligation,
                  RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable.class),
              new RehydratedEntryExpectation(
                  lossSettlement,
                  RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class)));
    }
  }

  private static void assertFixedAssetState(SqlitePostingSession store) {
    var asset = store.findFixedAsset(FIXED_ASSET_ID).orElseThrow();
    assertEquals(Money.parse("EUR", "120.00"), asset.cost());
    assertEquals(Money.parse("EUR", "10.00"), asset.accumulatedDepreciation());
    assertEquals(1, asset.depreciationPeriodsApplied());
    assertEquals(Optional.of(LocalDate.parse("2026-07-01")), asset.disposedOn());
    assertFalse(store.hasFixedAsset(new FixedAssetId("asset-missing")));
  }

  private static void assertFinancingState(SqlitePostingSession store) {
    var arrangement = store.findFinancingArrangement(FINANCING_ID).orElseThrow();
    assertEquals(Money.parse("EUR", "60.00"), arrangement.outstandingPrincipal());
    assertEquals(Money.parse("EUR", "0.00"), arrangement.outstandingInterest());
    assertEquals(
        Optional.of(LocalDate.parse("2026-06-04")), arrangement.latestLifecycleEffectiveDate());
    assertFalse(store.hasFinancingArrangement(new FinancingArrangementId("financing-missing")));
  }

  private static void assertForeignExchangeState(SqlitePostingSession store) {
    var obligation = store.findForeignCurrencyObligation(FX_OBLIGATION_ID).orElseThrow();
    assertEquals(Optional.of(LocalDate.parse("2026-07-03")), obligation.settledOn());
    assertEquals(Optional.of(Money.parse("EUR", "95.00")), obligation.functionalSettlementAmount());
    assertEquals(Optional.of(Money.parse("EUR", "3.00")), obligation.realizedGainOrLossAmount());
    assertEquals(Optional.of(true), obligation.realizedGain());
    assertEquals(LocalDate.parse("2026-07-03"), obligation.lifecycleHorizon());
    assertFalse(
        store.hasForeignCurrencyObligation(
            new ForeignCurrencyObligationId("foreign-currency-obligation-missing")));
  }

  private static void assertUnsettledForeignExchangeState(SqlitePostingSession store) {
    var obligation = store.findForeignCurrencyObligation(FX_OBLIGATION_ID).orElseThrow();
    assertEquals(Optional.empty(), obligation.settledOn());
    assertEquals(Optional.empty(), obligation.functionalSettlementAmount());
    assertEquals(Optional.empty(), obligation.realizedGainOrLossAmount());
    assertEquals(Optional.empty(), obligation.realizedGain());
  }

  private static void assertLossForeignExchangeState(SqlitePostingSession store) {
    var obligation = store.findForeignCurrencyObligation(FX_LOSS_OBLIGATION_ID).orElseThrow();
    assertEquals(Optional.of(LocalDate.parse("2026-07-05")), obligation.settledOn());
    assertEquals(Optional.of(Money.parse("EUR", "90.00")), obligation.functionalSettlementAmount());
    assertEquals(Optional.of(Money.parse("EUR", "2.00")), obligation.realizedGainOrLossAmount());
    assertEquals(Optional.of(false), obligation.realizedGain());
  }

  private static void assertPublishedRegisters(SqlitePostingSession store) {
    BookReadService reads = new BookReadService(store);
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

    FixedAssetRegisterResult.Reported fixedAssetsAsOf =
        assertInstanceOf(
            FixedAssetRegisterResult.Reported.class,
            reads.fixedAssetRegister(
                new FixedAssetRegisterQuery(Optional.of(LocalDate.parse("2026-06-30")))));
    assertEquals(1, fixedAssetsAsOf.report().rows().size());
    assertEquals(Optional.empty(), fixedAssetsAsOf.report().rows().getFirst().disposedOn());

    FixedAssetRegisterResult.Reported fixedAssetsAtCapitalization =
        assertInstanceOf(
            FixedAssetRegisterResult.Reported.class,
            reads.fixedAssetRegister(
                new FixedAssetRegisterQuery(Optional.of(LocalDate.parse("2026-06-01")))));
    assertEquals(1, fixedAssetsAtCapitalization.report().rows().size());
    assertEquals(
        Optional.empty(),
        fixedAssetsAtCapitalization.report().rows().getFirst().latestLifecycleEffectiveDate());

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
    assertEquals(2, foreignExchange.report().rows().size());
    assertEquals(
        Optional.of(MonetaryAmount.of(Money.parse("EUR", "3.00"))),
        foreignExchange.report().rows().getFirst().realizedGainOrLossAmount());
  }

  private static void assertRehydratedEntries(
      SqlitePostingSession store, List<RehydratedEntryExpectation> expectations) {
    for (RehydratedEntryExpectation expectation : expectations) {
      assertInstanceOf(
          expectation.expectedType(),
          store
              .findPosting(expectation.committed().postingId())
              .orElseThrow()
              .resolvedOriginatingEntry()
              .orElseThrow());
    }
  }

  private record RehydratedEntryExpectation(
      PostEntryResult.Committed committed, Class<? extends BookkeepingEntry> expectedType) {}

  private static PostEntryResult.Committed commit(
      PostingApplicationService application,
      BookkeepingEntry entry,
      String token,
      String sourceDocumentType) {
    return assertInstanceOf(
        PostEntryResult.Committed.class,
        application.commit(
            new PostEntryCommand(
                entry,
                generatedEvidence(token, sourceDocumentType),
                requestProvenance(token),
                SourceChannel.CLI)));
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

  private static RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
      foreignCurrencyLossObligation() {
    return new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
        LocalDate.parse("2026-07-04"),
        FX_LOSS_OBLIGATION_ID,
        FX_RECEIVABLE,
        FX_REVENUE,
        FX_GAIN,
        FX_LOSS,
        foreignExchangeDetails("92.00", "2026-07-04"));
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.Settlement
      foreignExchangeLossSettlement() {
    return new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
        LocalDate.parse("2026-07-05"),
        FX_LOSS_OBLIGATION_ID,
        CASH,
        foreignExchangeDetails("90.00", "2026-07-05"),
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

  private static BookIdentity accrualBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Lifecycle Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  private static void declareLifecycleAccounts(SqlitePostingSession store) {
    declareCash(store, CASH, "Cash");
    declareNonCurrentAsset(store, ASSET, "Fixed Asset");
    declareNonCurrentAsset(store, ACCUMULATED_DEPRECIATION, "Accumulated Depreciation");
    declareExpense(store, DEPRECIATION_EXPENSE, "Depreciation Expense");
    declareRevenue(store, DISPOSAL_GAIN, "Disposal Gain");
    declareExpense(store, DISPOSAL_LOSS, "Disposal Loss");
    declareLiability(store, FINANCING_PRINCIPAL, "Financing Principal");
    declareCurrentLiability(store, FINANCING_INTEREST_PAYABLE, "Financing Interest Payable");
    declareExpense(store, FINANCING_INTEREST_EXPENSE, "Financing Interest Expense");
    declareTradeReceivable(store, FX_RECEIVABLE, "Foreign Receivable");
    declareRevenue(store, FX_REVENUE, "Foreign Revenue");
    declareRevenue(store, FX_GAIN, "Foreign Exchange Gain");
    declareExpense(store, FX_LOSS, "Foreign Exchange Loss");
  }

  private static void declareCash(SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.ASSET,
        new dev.erst.fingrind.core.AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)),
        RECORDED_AT);
  }

  private static void declareNonCurrentAsset(
      SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET),
        RECORDED_AT);
  }

  private static void declareTradeReceivable(
      SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE),
        RECORDED_AT);
  }

  private static void declareCurrentLiability(
      SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        RECORDED_AT);
  }

  private static void declareLiability(SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY),
        RECORDED_AT);
  }

  private static void declareRevenue(SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE),
        RECORDED_AT);
  }

  private static void declareExpense(SqlitePostingSession store, AccountCode code, String name) {
    declareAccount(
        store,
        code,
        new AccountName(name),
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE),
        RECORDED_AT);
  }

  private static void declareAccount(
      SqlitePostingSession store,
      AccountCode code,
      AccountName name,
      AccountType type,
      dev.erst.fingrind.core.AccountTaxonomy taxonomy,
      Instant declaredAt) {
    store.declareAccount(new AccountDeclaration(code, name, type, taxonomy), declaredAt);
  }

  private static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        new ActorId("actor-" + token),
        ActorType.AGENT,
        new CommandId("command-" + token),
        new IdempotencyKey("idempotency-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("correlation-" + token)));
  }

  private static PostingIdGenerator postingIds(String... postingIds) {
    Deque<String> pending = new ArrayDeque<>(List.of(postingIds));
    return () -> new PostingId(pending.removeFirst());
  }
}

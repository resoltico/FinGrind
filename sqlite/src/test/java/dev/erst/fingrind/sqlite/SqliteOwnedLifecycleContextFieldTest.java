package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises every owned lifecycle context through the production SQLite adapter. */
class SqliteOwnedLifecycleContextFieldTest extends SqlitePostingFactStoreTestSupport {
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
  void ownedLifecycleContexts_resolvePersistAndReadTheirExactState() {
    Path bookPath = tempDirectory.resolve("owned-lifecycle-contexts.sqlite");
    SqlitePostingFactStore store = openStore(bookAccess(bookPath));
    try (SqlitePostingSession session = SqliteCapabilitySessions.posting(store)) {
      session.openAttestedBook(
          CLOCK.instant(),
          bookIdentity(),
          List.of(),
          SqliteAttestationTestSupport.genesis(bookIdentity(), CLOCK.instant()));
      declareLifecycleAccounts(session);
      PostingApplicationService service = postingService(session);

      commit(service, fixedAssetCapitalization(), "fixed-capitalization", "supplier-invoice");
      PostEntryResult.Committed depreciation =
          commit(service, fixedAssetDepreciation(), "fixed-depreciation", "depreciation-schedule");
      commit(service, fixedAssetDisposal(), "fixed-disposal", "cash-receipt");
      PostEntryResult.Committed borrowing =
          commit(service, financingBorrowing(), "financing-borrowing", "loan-agreement");
      commit(
          service,
          financingPrincipalRepayment(),
          "financing-principal-repayment",
          "loan-statement");
      commit(
          service,
          financingInterestAccrual(),
          "financing-interest-accrual",
          "interest-calculation");
      commit(service, financingInterestPayment(), "financing-interest-payment", "loan-statement");
      PostEntryResult.Committed obligation =
          commit(
              service,
              foreignCurrencyObligation(),
              "foreign-currency-obligation",
              "foreign-currency-invoice");
      commit(
          service,
          foreignExchangeSettlement(),
          "foreign-exchange-settlement",
          "settlement-confirmation");

      assertEquals(
          9, session.postings(dev.erst.fingrind.core.EffectiveDateRange.unbounded()).size());
      assertEquals(
          FIXED_ASSET_ID, session.findFixedAsset(FIXED_ASSET_ID).orElseThrow().fixedAssetId());
      assertEquals(
          FIXED_ASSET_ID,
          session
              .fixedAssets(Optional.of(LocalDate.parse("2026-07-15")))
              .getFirst()
              .fixedAssetId());
      assertEquals(
          Money.parse("EUR", "0.00"),
          session.findFixedAsset(FIXED_ASSET_ID).orElseThrow().carryingAmount());
      assertEquals(
          Money.parse("EUR", "40.00"),
          session.findFinancingArrangement(FINANCING_ID).orElseThrow().principalRepaid());
      assertEquals(
          Money.parse("EUR", "0.00"),
          session.findFinancingArrangement(FINANCING_ID).orElseThrow().outstandingInterest());
      assertEquals(
          Optional.of(LocalDate.parse("2026-07-03")),
          session.findForeignCurrencyObligation(FX_OBLIGATION_ID).orElseThrow().settledOn());
      assertTrue(session.hasFixedAsset(FIXED_ASSET_ID));
      assertTrue(session.hasFinancingArrangement(FINANCING_ID));
      assertTrue(session.hasForeignCurrencyObligation(FX_OBLIGATION_ID));
      assertEquals(
          depreciation.postingId(),
          session.findPosting(depreciation.postingId()).orElseThrow().postingId());
      assertEquals(
          borrowing.postingId(),
          session.findPosting(borrowing.postingId()).orElseThrow().postingId());
      assertEquals(
          obligation.postingId(),
          session.findPosting(obligation.postingId()).orElseThrow().postingId());
    }
  }

  private static PostingApplicationService postingService(SqlitePostingSession session) {
    AtomicInteger sequence = new AtomicInteger();
    return new PostingApplicationService(
        session,
        session,
        () -> SqliteTestPostingIds.fromLabel("sqlite-lifecycle-" + sequence.incrementAndGet()),
        CLOCK);
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
                SourceChannel.CLI),
            SqliteAttestationTestSupport.authorizer());
    return assertInstanceOf(
        PostEntryResult.Committed.class,
        result,
        () -> "Expected a committed lifecycle entry: " + result);
  }

  private static AccountingEvidence evidence(
      String token, String sourceDocumentType, LocalDate effectiveDate) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("sqlite-lifecycle-evidence-" + token),
                new SourceDocumentType(sourceDocumentType),
                effectiveDate)),
        List.of());
  }

  private static RequestProvenance provenance(String token) {
    return new RequestProvenance(
        SqliteTestCommandIds.fromLabel("sqlite-lifecycle-command-" + token),
        new IdempotencyKey("sqlite-lifecycle-idempotency-" + token),
        new CausationId("sqlite-lifecycle-cause-" + token),
        Optional.empty());
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
        amount("120.00"),
        new FixedAssetDepreciationSchedule(LocalDate.parse("2026-06-01"), 12, amount("0.00")));
  }

  private static FixedAssetBookkeepingEntryVariants.Depreciation fixedAssetDepreciation() {
    return new FixedAssetBookkeepingEntryVariants.Depreciation(
        LocalDate.parse("2026-06-30"), FIXED_ASSET_ID, null);
  }

  private static FixedAssetBookkeepingEntryVariants.Disposal fixedAssetDisposal() {
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        LocalDate.parse("2026-07-01"), FIXED_ASSET_ID, CASH, amount("110.00"), null);
  }

  private static dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants.Borrowing
      financingBorrowing() {
    return new dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants.Borrowing(
        LocalDate.parse("2026-06-01"),
        FINANCING_ID,
        CASH,
        FINANCING_PRINCIPAL,
        FINANCING_INTEREST_PAYABLE,
        amount("100.00"));
  }

  private static dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
          .PrincipalRepayment
      financingPrincipalRepayment() {
    return new dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
        .PrincipalRepayment(
        LocalDate.parse("2026-06-02"), FINANCING_ID, CASH, amount("40.00"), null);
  }

  private static dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
          .InterestAccrual
      financingInterestAccrual() {
    return new dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
        .InterestAccrual(
        LocalDate.parse("2026-06-03"),
        FINANCING_ID,
        FINANCING_INTEREST_EXPENSE,
        amount("5.00"),
        null);
  }

  private static dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
          .InterestPayment
      financingInterestPayment() {
    return new dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants
        .InterestPayment(LocalDate.parse("2026-06-04"), FINANCING_ID, CASH, amount("5.00"), null);
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
    MonetaryAmount transaction = amount("100.00", "USD");
    MonetaryAmount functional = amount(functionalAmount);
    return new ForeignExchangeDetails(
        transaction,
        functional,
        new QuotedExchangeRate(transaction, functional, LocalDate.parse(quotedOn), "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static MonetaryAmount amount(String value) {
    return amount(value, "EUR");
  }

  private static MonetaryAmount amount(String value, String currencyCode) {
    return MonetaryAmount.of(Money.parse(currencyCode, value));
  }

  private static void declareLifecycleAccounts(SqlitePostingSession store) {
    declare(
        store,
        CASH,
        "Cash",
        AccountType.ASSET,
        new AccountTaxonomy(
            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty(),
            Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    declare(store, ASSET, "Fixed Asset", AccountType.ASSET, nonCurrentAssetTaxonomy());
    declare(
        store,
        ACCUMULATED_DEPRECIATION,
        "Accumulated Depreciation",
        AccountType.ASSET,
        nonCurrentAssetTaxonomy());
    declare(
        store,
        DEPRECIATION_EXPENSE,
        "Depreciation Expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declare(
        store,
        DISPOSAL_GAIN,
        "Disposal Gain",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
    declare(
        store,
        DISPOSAL_LOSS,
        "Disposal Loss",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declare(
        store,
        FINANCING_PRINCIPAL,
        "Financing Principal",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_LIABILITY));
    declare(
        store,
        FINANCING_INTEREST_PAYABLE,
        "Financing Interest Payable",
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY));
    declare(
        store,
        FINANCING_INTEREST_EXPENSE,
        "Financing Interest Expense",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
    declare(
        store,
        FX_RECEIVABLE,
        "Foreign Receivable",
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.TRADE_RECEIVABLE));
    declare(
        store,
        FX_REVENUE,
        "Foreign Revenue",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
    declare(
        store,
        FX_GAIN,
        "Foreign Exchange Gain",
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE));
    declare(
        store,
        FX_LOSS,
        "Foreign Exchange Loss",
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE));
  }

  private static AccountTaxonomy nonCurrentAssetTaxonomy() {
    return financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET);
  }

  private static void declare(
      SqlitePostingSession store,
      AccountCode code,
      String name,
      AccountType type,
      AccountTaxonomy taxonomy) {
    assertInstanceOf(
        dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared.class,
        store.declareAccount(
            new AccountDeclaration(code, new AccountName(name), type, taxonomy),
            CLOCK.instant(),
            SqliteAttestationTestSupport.authorizer()));
  }
}

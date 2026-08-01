package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct first-defense admission coverage for the three owned lifecycle aggregates. */
class OwnedLifecycleAdmissionPolicyTest {
  private static final FixedAssetAdmissionPolicy FIXED_ASSETS = new FixedAssetAdmissionPolicy();
  private static final FinancingAdmissionPolicy FINANCING = new FinancingAdmissionPolicy();
  private static final RealizedForeignExchangeAdmissionPolicy FOREIGN_EXCHANGE =
      new RealizedForeignExchangeAdmissionPolicy();

  private static final FixedAssetId ASSET_ID = new FixedAssetId("asset-vehicle-001");
  private static final FinancingArrangementId FINANCING_ID =
      new FinancingArrangementId("loan-working-capital-001");
  private static final ForeignCurrencyObligationId OBLIGATION_ID =
      new ForeignCurrencyObligationId("receivable-usd-001");

  private static final AccountCode CASH = new AccountCode("cash");
  private static final AccountCode ASSET = new AccountCode("fixed-asset");
  private static final AccountCode ACCUMULATED_DEPRECIATION =
      new AccountCode("accumulated-depreciation");
  private static final AccountCode DEPRECIATION_EXPENSE = new AccountCode("depreciation-expense");
  private static final AccountCode DISPOSAL_GAIN = new AccountCode("disposal-gain");
  private static final AccountCode DISPOSAL_LOSS = new AccountCode("disposal-loss");
  private static final AccountCode PRINCIPAL_LIABILITY = new AccountCode("financing-principal");
  private static final AccountCode INTEREST_PAYABLE = new AccountCode("financing-interest-payable");
  private static final AccountCode INTEREST_EXPENSE = new AccountCode("financing-interest-expense");
  private static final AccountCode RECEIVABLE = new AccountCode("foreign-receivable");
  private static final AccountCode REVENUE = new AccountCode("foreign-revenue");
  private static final AccountCode FOREIGN_EXCHANGE_GAIN = new AccountCode("foreign-exchange-gain");
  private static final AccountCode FOREIGN_EXCHANGE_LOSS = new AccountCode("foreign-exchange-loss");

  @Test
  void fixedAssetAdmission_derivesLifecycleFactsAndRejectsDuplicateOutOfOrderAndExhaustedEvents() {
    ValidationBook existingAsset =
        new ValidationBook(
            Map.of(ASSET_ID, fixedAsset("10.00", 1, "2026-06-30", false)), Map.of(), Map.of());
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(capitalization(), existingAsset, "record-fixed-asset-capitalization")
            .rejection(),
        "fixed-asset-id-already-exists");
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(depreciation("2026-06-29"), existingAsset, "record-fixed-asset-depreciation")
            .rejection(),
        "fixed-asset-lifecycle-precedes-horizon");

    FixedAssetAdmissionPolicy.Resolution depreciationResolution =
        FIXED_ASSETS.resolve(
            depreciation("2026-06-30"), existingAsset, "record-fixed-asset-depreciation");
    assertTrue(depreciationResolution.rejection().isEmpty());
    FixedAssetBookkeepingEntryVariants.Depreciation resolvedDepreciation =
        assertInstanceOf(
            FixedAssetBookkeepingEntryVariants.Depreciation.class, depreciationResolution.entry());
    ResolvedFixedAssetDepreciation depreciation =
        Objects.requireNonNull(
            resolvedDepreciation.resolvedDepreciation(), "resolved fixed-asset depreciation");
    assertEquals(amount("EUR", "1000"), depreciation.amount());
    assertEquals(DEPRECIATION_EXPENSE, depreciation.depreciationExpenseAccountCode());
    assertEquals(ACCUMULATED_DEPRECIATION, depreciation.accumulatedDepreciationAccountCode());

    FixedAssetAdmissionPolicy.Resolution disposalResolution =
        FIXED_ASSETS.resolve(
            disposal("2026-07-01", "12000"), existingAsset, "record-fixed-asset-disposal");
    assertTrue(disposalResolution.rejection().isEmpty());
    FixedAssetBookkeepingEntryVariants.Disposal resolvedDisposal =
        assertInstanceOf(
            FixedAssetBookkeepingEntryVariants.Disposal.class, disposalResolution.entry());
    ResolvedFixedAssetDisposal disposal =
        Objects.requireNonNull(
            resolvedDisposal.resolvedDisposal(), "resolved fixed-asset disposal");
    assertEquals(amount("EUR", "11000"), disposal.carryingAmount());
    assertEquals(amount("EUR", "1000"), disposal.gainOrLossAmount());
    assertEquals(DISPOSAL_GAIN, disposal.gainOrLossAccountCode());
    assertTrue(disposal.gain());

    ValidationBook disposedAsset =
        new ValidationBook(
            Map.of(ASSET_ID, fixedAsset("10.00", 1, "2026-07-01", true)), Map.of(), Map.of());
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(depreciation("2026-07-01"), disposedAsset, "record-fixed-asset-depreciation")
            .rejection(),
        "fixed-asset-already-disposed");
    ValidationBook exhaustedAsset =
        new ValidationBook(
            Map.of(ASSET_ID, fixedAsset("120.00", 12, "2027-05-01", false)), Map.of(), Map.of());
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(depreciation("2027-05-01"), exhaustedAsset, "record-fixed-asset-depreciation")
            .rejection(),
        "fixed-asset-fully-depreciated");
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(depreciation("2026-05-31"), existingAsset, "record-fixed-asset-depreciation")
            .rejection(),
        "fixed-asset-depreciation-precedes-in-service-date");
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(
                new FixedAssetBookkeepingEntryVariants.Disposal(
                    LocalDate.parse("2026-07-01"), ASSET_ID, CASH, amount("USD", "12000"), null),
                existingAsset,
                "record-fixed-asset-disposal")
            .rejection(),
        "fixed-asset-disposal-currency-mismatch");
    ValidationBook emptyBook = new ValidationBook(Map.of(), Map.of(), Map.of());
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(depreciation("2026-06-30"), emptyBook, "record-fixed-asset-depreciation")
            .rejection(),
        "fixed-asset-not-found");
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(disposal("2026-06-30", "12000"), emptyBook, "record-fixed-asset-disposal")
            .rejection(),
        "fixed-asset-not-found");
  }

  @Test
  void fixedAssetDisposalAdmission_rejectsDisposedAndOutOfOrderEventsAndDerivesLosses() {
    ValidationBook activeBook =
        new ValidationBook(
            Map.of(ASSET_ID, fixedAsset("10.00", 1, "2026-06-30", false)), Map.of(), Map.of());
    ValidationBook disposedBook =
        new ValidationBook(
            Map.of(ASSET_ID, fixedAsset("10.00", 1, "2026-07-01", true)), Map.of(), Map.of());

    assertRejectionCode(
        FIXED_ASSETS
            .resolve(disposal("2026-07-01", "10000"), disposedBook, "record-fixed-asset-disposal")
            .rejection(),
        "fixed-asset-already-disposed");
    assertRejectionCode(
        FIXED_ASSETS
            .resolve(disposal("2026-06-29", "10000"), activeBook, "record-fixed-asset-disposal")
            .rejection(),
        "fixed-asset-lifecycle-precedes-horizon");

    FixedAssetAdmissionPolicy.Resolution resolution =
        FIXED_ASSETS.resolve(
            disposal("2026-07-01", "10000"), activeBook, "record-fixed-asset-disposal");
    FixedAssetBookkeepingEntryVariants.Disposal resolved =
        assertInstanceOf(FixedAssetBookkeepingEntryVariants.Disposal.class, resolution.entry());
    ResolvedFixedAssetDisposal disposal =
        Objects.requireNonNull(resolved.resolvedDisposal(), "resolved fixed-asset disposal");
    assertEquals(DISPOSAL_LOSS, disposal.gainOrLossAccountCode());
    assertEquals(amount("EUR", "1000"), disposal.gainOrLossAmount());
    assertFalse(disposal.gain());
  }

  @Test
  void financingAdmission_derivesArrangementAccountsAndRejectsBoundsCurrencyAndHorizonBreaches() {
    FinancingArrangementRecord arrangement = financingArrangement();
    ValidationBook book = new ValidationBook(Map.of(), Map.of(FINANCING_ID, arrangement), Map.of());
    assertRejectionCode(
        FINANCING.resolve(borrowing(), book, "record-financing-borrowing").rejection(),
        "financing-arrangement-id-already-exists");
    assertRejectionCode(
        FINANCING
            .resolve(
                principalRepayment("2026-06-02", "7000"),
                book,
                "record-financing-principal-repayment")
            .rejection(),
        "financing-lifecycle-precedes-horizon");
    assertRejectionCode(
        FINANCING
            .resolve(
                principalRepayment("2026-06-03", "7000"),
                book,
                "record-financing-principal-repayment")
            .rejection(),
        "financing-principal-repayment-exceeds-outstanding");
    assertRejectionCode(
        FINANCING
            .resolve(
                interestPayment("2026-06-03", "600"), book, "record-financing-interest-payment")
            .rejection(),
        "financing-interest-payment-exceeds-accrued");
    assertRejectionCode(
        FINANCING
            .resolve(
                interestAccrual("2026-06-03", amount("USD", "500")),
                book,
                "record-financing-interest-accrual")
            .rejection(),
        "financing-currency-mismatch");
    assertRejectionCode(
        FINANCING
            .resolve(
                interestAccrual("2026-06-02", amount("EUR", "500")),
                book,
                "record-financing-interest-accrual")
            .rejection(),
        "financing-lifecycle-precedes-horizon");
    assertRejectionCode(
        FINANCING
            .resolve(
                new FinancingBookkeepingEntryVariants.InterestPayment(
                    LocalDate.parse("2026-06-03"), FINANCING_ID, CASH, amount("USD", "500"), null),
                book,
                "record-financing-interest-payment")
            .rejection(),
        "financing-currency-mismatch");

    ValidationBook emptyBook = new ValidationBook(Map.of(), Map.of(), Map.of());
    assertRejectionCode(
        FINANCING
            .resolve(
                principalRepayment("2026-06-03", "100"),
                emptyBook,
                "record-financing-principal-repayment")
            .rejection(),
        "financing-arrangement-not-found");
    assertRejectionCode(
        FINANCING
            .resolve(
                interestAccrual("2026-06-03", amount("EUR", "100")),
                emptyBook,
                "record-financing-interest-accrual")
            .rejection(),
        "financing-arrangement-not-found");
    assertRejectionCode(
        FINANCING
            .resolve(
                interestPayment("2026-06-03", "100"),
                emptyBook,
                "record-financing-interest-payment")
            .rejection(),
        "financing-arrangement-not-found");

    FinancingAdmissionPolicy.Resolution repaymentResolution =
        FINANCING.resolve(
            principalRepayment("2026-06-03", "6000"), book, "record-financing-principal-repayment");
    FinancingBookkeepingEntryVariants.PrincipalRepayment resolvedRepayment =
        assertInstanceOf(
            FinancingBookkeepingEntryVariants.PrincipalRepayment.class,
            repaymentResolution.entry());
    ResolvedFinancingApplication resolvedApplication =
        Objects.requireNonNull(
            resolvedRepayment.resolvedApplication(), "resolved financing application");
    assertEquals(PRINCIPAL_LIABILITY, resolvedApplication.principalLiabilityAccountCode());
    assertEquals(INTEREST_PAYABLE, resolvedApplication.interestPayableAccountCode());
  }

  @Test
  void realizedForeignExchangeAdmission_derivesGainAndRejectsDuplicateAndInvalidSettlementFacts() {
    ForeignCurrencyObligationRecord obligation = foreignCurrencyObligation(Optional.empty());
    ValidationBook book = new ValidationBook(Map.of(), Map.of(), Map.of(OBLIGATION_ID, obligation));
    assertRejectionCode(
        FOREIGN_EXCHANGE
            .resolve(foreignCurrencyObligation(), book, "record-foreign-currency-obligation")
            .rejection(),
        "foreign-currency-obligation-id-already-exists");
    assertRejectionCode(
        FOREIGN_EXCHANGE
            .resolve(
                settlement(
                    "2026-06-30", foreignExchange("USD", "10000", "EUR", "9500", "2026-06-30")),
                book,
                "record-realized-foreign-exchange-settlement")
            .rejection(),
        "realized-foreign-exchange-settlement-precedes-lifecycle-horizon");
    assertRejectionCode(
        FOREIGN_EXCHANGE
            .resolve(
                settlement(
                    "2026-07-03", foreignExchange("USD", "9900", "EUR", "9500", "2026-07-03")),
                book,
                "record-realized-foreign-exchange-settlement")
            .rejection(),
        "realized-foreign-exchange-settlement-transaction-amount-mismatch");
    assertRejectionCode(
        FOREIGN_EXCHANGE
            .resolve(
                settlement(
                    "2026-07-03", foreignExchange("USD", "10000", "GBP", "9500", "2026-07-03")),
                book,
                "record-realized-foreign-exchange-settlement")
            .rejection(),
        "realized-foreign-exchange-settlement-functional-currency-mismatch");

    RealizedForeignExchangeAdmissionPolicy.Resolution settlementResolution =
        FOREIGN_EXCHANGE.resolve(
            settlement("2026-07-03", foreignExchange("USD", "10000", "EUR", "9500", "2026-07-03")),
            book,
            "record-realized-foreign-exchange-settlement");
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement resolvedSettlement =
        assertInstanceOf(
            RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class,
            settlementResolution.entry());
    ResolvedRealizedForeignExchangeSettlement resolved =
        Objects.requireNonNull(
            resolvedSettlement.resolvedSettlement(),
            "resolved realized foreign-exchange settlement");
    assertEquals(RECEIVABLE, resolved.receivableAccountCode());
    assertEquals(FOREIGN_EXCHANGE_GAIN, resolved.gainOrLossAccountCode());
    assertEquals(amount("EUR", "300"), resolved.realizedGainOrLossAmount());
    assertTrue(resolved.gain());

    ValidationBook settledBook =
        new ValidationBook(
            Map.of(),
            Map.of(),
            Map.of(
                OBLIGATION_ID,
                foreignCurrencyObligation(Optional.of(LocalDate.parse("2026-07-03")))));
    assertRejectionCode(
        FOREIGN_EXCHANGE
            .resolve(
                settlement(
                    "2026-07-03", foreignExchange("USD", "10000", "EUR", "9500", "2026-07-03")),
                settledBook,
                "record-realized-foreign-exchange-settlement")
            .rejection(),
        "foreign-currency-obligation-already-settled");
    assertRejectionCode(
        FOREIGN_EXCHANGE
            .resolve(
                settlement(
                    "2026-07-03", foreignExchange("USD", "10000", "EUR", "9500", "2026-07-03")),
                new ValidationBook(Map.of(), Map.of(), Map.of()),
                "record-realized-foreign-exchange-settlement")
            .rejection(),
        "foreign-currency-obligation-not-found");

    RealizedForeignExchangeAdmissionPolicy.Resolution lossResolution =
        FOREIGN_EXCHANGE.resolve(
            settlement("2026-07-03", foreignExchange("USD", "10000", "EUR", "9000", "2026-07-03")),
            book,
            "record-realized-foreign-exchange-settlement");
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement resolvedLossSettlement =
        assertInstanceOf(
            RealizedForeignExchangeBookkeepingEntryVariants.Settlement.class,
            lossResolution.entry());
    ResolvedRealizedForeignExchangeSettlement resolvedLoss =
        Objects.requireNonNull(
            resolvedLossSettlement.resolvedSettlement(), "resolved realized foreign-exchange loss");
    assertEquals(FOREIGN_EXCHANGE_LOSS, resolvedLoss.gainOrLossAccountCode());
    assertEquals(amount("EUR", "200"), resolvedLoss.realizedGainOrLossAmount());
    assertFalse(resolvedLoss.gain());
  }

  @Test
  void fixedAssetRecord_reconcilesDerivedFactsAndRejectsInvalidDurableState() {
    FixedAssetRecord activeAsset = fixedAsset("10.00", 1, "2026-06-30", false);
    assertEquals(Money.parse("EUR", "110.00"), activeAsset.carryingAmount());
    assertEquals(Money.parse("EUR", "110.00"), activeAsset.remainingDepreciableAmount());
    assertTrue(activeAsset.depreciable());
    assertEquals(LocalDate.parse("2026-06-30"), activeAsset.lifecycleHorizon());
    FixedAssetRecord disposedAsset = fixedAsset("10.00", 1, "2026-06-30", true);
    assertEquals(Money.parse("EUR", "0.00"), disposedAsset.carryingAmount());
    assertEquals(Money.parse("EUR", "110.00"), disposedAsset.carryingAmountBeforeDisposal());
    assertFalse(disposedAsset.depreciable());
    assertFalse(fixedAsset("120.00", 12, "2027-05-01", false).depreciable());
    assertFalse(fixedAsset("120.00", 0, "2026-06-01", false).depreciable());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.zero(Money.parse("EUR", "1.00").currencyUnit()),
                amount("EUR", "0"),
                Money.parse("EUR", "0.00"),
                0,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("USD", "0"),
                Money.parse("EUR", "0.00"),
                0,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("EUR", "0"),
                Money.parse("USD", "0.00"),
                0,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("EUR", "0"),
                Money.parse("EUR", "121.00"),
                0,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("EUR", "0"),
                Money.parse("EUR", "0.00"),
                -1,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("EUR", "0"),
                Money.parse("EUR", "0.00"),
                13,
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("EUR", "0"),
                Money.parse("EUR", "0.00"),
                0,
                Optional.of(LocalDate.parse("2026-05-31")),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRecord(
                Money.parse("EUR", "120.00"),
                amount("EUR", "0"),
                Money.parse("EUR", "0.00"),
                0,
                Optional.empty(),
                Optional.of(LocalDate.parse("2026-05-31"))));
  }

  @Test
  void financingArrangementRecord_reconcilesBalancesAndRejectsInvalidDurableState() {
    FinancingArrangementRecord arrangement = financingArrangement();
    assertEquals(Money.parse("EUR", "60.00"), arrangement.outstandingPrincipal());
    assertEquals(Money.parse("EUR", "5.00"), arrangement.outstandingInterest());
    assertEquals(LocalDate.parse("2026-06-03"), arrangement.lifecycleHorizon());
    assertEquals(
        LocalDate.parse("2026-06-01"),
        financingArrangementRecord(
                Money.parse("EUR", "100.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Optional.empty())
            .lifecycleHorizon());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            financingArrangementRecord(
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            financingArrangementRecord(
                Money.parse("EUR", "100.00"),
                Money.parse("USD", "0.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            financingArrangementRecord(
                Money.parse("EUR", "100.00"),
                Money.parse("EUR", "101.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "0.00"),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            financingArrangementRecord(
                Money.parse("EUR", "100.00"),
                Money.parse("EUR", "0.00"),
                Money.parse("EUR", "1.00"),
                Money.parse("EUR", "2.00"),
                Optional.empty()));
  }

  @Test
  void foreignCurrencyObligationRecord_reconcilesSettlementFactsAndRejectsInvalidDurableState() {
    ForeignCurrencyObligationRecord unsettled = foreignCurrencyObligation(Optional.empty());
    assertTrue(unsettled.unsettled());
    assertFalse(foreignCurrencyObligation(Optional.of(LocalDate.parse("2026-07-03"))).unsettled());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "0.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.of(Money.parse("EUR", "95.00")),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.of(Money.parse("EUR", "95.00")),
                Optional.of(Money.parse("EUR", "3.00")),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "0.00"),
                LocalDate.parse("2026-07-01"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("EUR", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-06-30"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-06-30")),
                Optional.of(Money.parse("EUR", "95.00")),
                Optional.of(Money.parse("EUR", "3.00")),
                Optional.of(true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.empty(),
                Optional.of(Money.parse("EUR", "95.00")),
                Optional.of(Money.parse("EUR", "3.00")),
                Optional.of(true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.of(Money.parse("EUR", "0.00")),
                Optional.of(Money.parse("EUR", "3.00")),
                Optional.of(true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.of(Money.parse("USD", "95.00")),
                Optional.of(Money.parse("EUR", "3.00")),
                Optional.of(true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            foreignCurrencyObligationRecord(
                Money.parse("USD", "100.00"),
                Money.parse("EUR", "92.00"),
                LocalDate.parse("2026-07-01"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.of(Money.parse("EUR", "95.00")),
                Optional.of(Money.parse("USD", "3.00")),
                Optional.of(true)));
  }

  private static FixedAssetBookkeepingEntryVariants.Capitalization capitalization() {
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        LocalDate.parse("2026-06-01"),
        ASSET_ID,
        ASSET,
        ACCUMULATED_DEPRECIATION,
        DEPRECIATION_EXPENSE,
        DISPOSAL_GAIN,
        DISPOSAL_LOSS,
        CASH,
        amount("EUR", "12000"),
        new FixedAssetDepreciationSchedule(LocalDate.parse("2026-06-01"), 12, amount("EUR", "0")));
  }

  private static FixedAssetBookkeepingEntryVariants.Depreciation depreciation(
      String effectiveDate) {
    return new FixedAssetBookkeepingEntryVariants.Depreciation(
        LocalDate.parse(effectiveDate), ASSET_ID, null);
  }

  private static FixedAssetBookkeepingEntryVariants.Disposal disposal(
      String effectiveDate, String proceedsMinorUnits) {
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        LocalDate.parse(effectiveDate), ASSET_ID, CASH, amount("EUR", proceedsMinorUnits), null);
  }

  private static FinancingBookkeepingEntryVariants.Borrowing borrowing() {
    return new FinancingBookkeepingEntryVariants.Borrowing(
        LocalDate.parse("2026-06-01"),
        FINANCING_ID,
        CASH,
        PRINCIPAL_LIABILITY,
        INTEREST_PAYABLE,
        amount("EUR", "10000"));
  }

  private static FinancingBookkeepingEntryVariants.PrincipalRepayment principalRepayment(
      String effectiveDate, String amountMinorUnits) {
    return new FinancingBookkeepingEntryVariants.PrincipalRepayment(
        LocalDate.parse(effectiveDate), FINANCING_ID, CASH, amount("EUR", amountMinorUnits), null);
  }

  private static FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual(
      String effectiveDate, MonetaryAmount interestAmount) {
    return new FinancingBookkeepingEntryVariants.InterestAccrual(
        LocalDate.parse(effectiveDate), FINANCING_ID, INTEREST_EXPENSE, interestAmount, null);
  }

  private static FinancingBookkeepingEntryVariants.InterestPayment interestPayment(
      String effectiveDate, String amountMinorUnits) {
    return new FinancingBookkeepingEntryVariants.InterestPayment(
        LocalDate.parse(effectiveDate), FINANCING_ID, CASH, amount("EUR", amountMinorUnits), null);
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
      foreignCurrencyObligation() {
    return new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
        LocalDate.parse("2026-07-01"),
        OBLIGATION_ID,
        RECEIVABLE,
        REVENUE,
        FOREIGN_EXCHANGE_GAIN,
        FOREIGN_EXCHANGE_LOSS,
        foreignExchange("USD", "10000", "EUR", "9200", "2026-07-01"));
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement(
      String effectiveDate, ForeignExchangeDetails foreignExchange) {
    return new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
        LocalDate.parse(effectiveDate), OBLIGATION_ID, CASH, foreignExchange, null);
  }

  private static FixedAssetRecord fixedAsset(
      String accumulatedDepreciation, int periodsApplied, String horizon, boolean disposed) {
    return fixedAssetRecord(
        Money.parse("EUR", "120.00"),
        amount("EUR", "0"),
        Money.parse("EUR", accumulatedDepreciation),
        periodsApplied,
        Optional.of(LocalDate.parse(horizon)),
        disposed ? Optional.of(LocalDate.parse(horizon)) : Optional.empty());
  }

  private static FixedAssetRecord fixedAssetRecord(
      Money cost,
      MonetaryAmount residualValue,
      Money accumulatedDepreciation,
      int periodsApplied,
      Optional<LocalDate> horizon,
      Optional<LocalDate> disposedOn) {
    return new FixedAssetRecord(
        ASSET_ID,
        LocalDate.parse("2026-06-01"),
        ASSET,
        ACCUMULATED_DEPRECIATION,
        DEPRECIATION_EXPENSE,
        DISPOSAL_GAIN,
        DISPOSAL_LOSS,
        cost,
        new FixedAssetDepreciationSchedule(LocalDate.parse("2026-06-01"), 12, residualValue),
        accumulatedDepreciation,
        periodsApplied,
        horizon,
        disposedOn);
  }

  private static FinancingArrangementRecord financingArrangement() {
    return financingArrangementRecord(
        Money.parse("EUR", "100.00"),
        Money.parse("EUR", "40.00"),
        Money.parse("EUR", "5.00"),
        Money.parse("EUR", "0.00"),
        Optional.of(LocalDate.parse("2026-06-03")));
  }

  private static FinancingArrangementRecord financingArrangementRecord(
      Money originalPrincipal,
      Money principalRepaid,
      Money interestAccrued,
      Money interestPaid,
      Optional<LocalDate> horizon) {
    return new FinancingArrangementRecord(
        FINANCING_ID,
        LocalDate.parse("2026-06-01"),
        PRINCIPAL_LIABILITY,
        INTEREST_PAYABLE,
        originalPrincipal,
        principalRepaid,
        interestAccrued,
        interestPaid,
        horizon);
  }

  private static ForeignCurrencyObligationRecord foreignCurrencyObligation(
      Optional<LocalDate> settledOn) {
    boolean settled = settledOn.isPresent();
    return foreignCurrencyObligationRecord(
        Money.parse("USD", "100.00"),
        Money.parse("EUR", "92.00"),
        LocalDate.parse("2026-07-01"),
        settledOn,
        settled ? Optional.of(Money.parse("EUR", "95.00")) : Optional.empty(),
        settled ? Optional.of(Money.parse("EUR", "3.00")) : Optional.empty(),
        settled ? Optional.of(true) : Optional.empty());
  }

  private static ForeignCurrencyObligationRecord foreignCurrencyObligationRecord(
      Money transactionAmount,
      Money functionalCarryingAmount,
      LocalDate lifecycleHorizon,
      Optional<LocalDate> settledOn,
      Optional<Money> functionalSettlementAmount,
      Optional<Money> realizedGainOrLossAmount,
      Optional<Boolean> realizedGain) {
    return new ForeignCurrencyObligationRecord(
        OBLIGATION_ID,
        LocalDate.parse("2026-07-01"),
        lifecycleHorizon,
        RECEIVABLE,
        FOREIGN_EXCHANGE_GAIN,
        FOREIGN_EXCHANGE_LOSS,
        transactionAmount,
        functionalCarryingAmount,
        settledOn,
        functionalSettlementAmount,
        realizedGainOrLossAmount,
        realizedGain);
  }

  private static ForeignExchangeDetails foreignExchange(
      String transactionCurrency,
      String transactionMinorUnits,
      String functionalCurrency,
      String functionalMinorUnits,
      String quotedOn) {
    MonetaryAmount transaction = amount(transactionCurrency, transactionMinorUnits);
    MonetaryAmount functional = amount(functionalCurrency, functionalMinorUnits);
    return new ForeignExchangeDetails(
        transaction,
        functional,
        new QuotedExchangeRate(
            transaction, functional, LocalDate.parse(quotedOn), "field-test-rate"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static MonetaryAmount amount(String currencyCode, String minorUnits) {
    return new MonetaryAmount(currencyCode, minorUnits);
  }

  private static void assertRejectionCode(
      Optional<BookkeepingPostingRejection> rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertEquals(expectedCode, violations.violations().getFirst().code());
  }

  /** In-memory aggregate state consulted by the three admission policies. */
  private static final class ValidationBook extends EmptyValidationStore {
    private final Map<FixedAssetId, FixedAssetRecord> fixedAssets;
    private final Map<FinancingArrangementId, FinancingArrangementRecord> financingArrangements;
    private final Map<ForeignCurrencyObligationId, ForeignCurrencyObligationRecord> obligations;

    private ValidationBook(
        Map<FixedAssetId, FixedAssetRecord> fixedAssets,
        Map<FinancingArrangementId, FinancingArrangementRecord> financingArrangements,
        Map<ForeignCurrencyObligationId, ForeignCurrencyObligationRecord> obligations) {
      this.fixedAssets = fixedAssets;
      this.financingArrangements = financingArrangements;
      this.obligations = obligations;
    }

    @Override
    public Optional<FixedAssetRecord> findFixedAsset(FixedAssetId fixedAssetId) {
      return Optional.ofNullable(fixedAssets.get(fixedAssetId));
    }

    @Override
    public boolean hasFixedAsset(FixedAssetId fixedAssetId) {
      return fixedAssets.containsKey(fixedAssetId);
    }

    @Override
    public Optional<FinancingArrangementRecord> findFinancingArrangement(
        FinancingArrangementId financingArrangementId) {
      return Optional.ofNullable(financingArrangements.get(financingArrangementId));
    }

    @Override
    public Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
        ForeignCurrencyObligationId foreignCurrencyObligationId) {
      return Optional.ofNullable(obligations.get(foreignCurrencyObligationId));
    }
  }
}

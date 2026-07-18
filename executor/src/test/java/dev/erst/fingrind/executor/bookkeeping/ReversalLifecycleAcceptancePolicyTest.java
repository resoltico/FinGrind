package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.bookkeeping.ReversalAcceptancePolicyTest.assertEntrySemanticsCode;
import static dev.erst.fingrind.executor.bookkeeping.ReversalAcceptancePolicyTest.foreignExchange;
import static dev.erst.fingrind.executor.bookkeeping.ReversalAcceptancePolicyTest.lifecyclePosting;
import static dev.erst.fingrind.executor.bookkeeping.ReversalAcceptancePolicyTest.negatedJournal;
import static dev.erst.fingrind.executor.bookkeeping.ReversalAcceptancePolicyTest.reversalRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Lifecycle-context reversal coverage for Fixed Assets, Financing, and Realized FX. */
class ReversalLifecycleAcceptancePolicyTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void rejectionFor_coversDurableLookupAndAdmittedAndConsumedStateForEveryLifecycleContext() {
    FixedAssetId fixedAssetId = new FixedAssetId("asset-delivery-van");
    FixedAssetBookkeepingEntryVariants.Capitalization capitalization = capitalization(fixedAssetId);
    CommittedPosting capitalizationPosting =
        lifecyclePosting("posting-capitalization", capitalization);
    FixedAssetRecord untouchedFixedAsset = fixedAssetRecord(capitalization, Money.zero(EUR), 0);
    FixedAssetRecord depreciatedFixedAsset =
        fixedAssetRecord(capitalization, Money.parse("EUR", "1000.00"), 1);
    FixedAssetRecord disposedFixedAsset = disposedFixedAssetRecord(capitalization);

    assertThrows(
        IllegalStateException.class,
        () ->
            ReversalLifecycleAcceptancePolicy.rejectionFor(
                capitalizationPosting,
                lifecycleStore(capitalizationPosting, Map.of(), Map.of(), Map.of())));
    assertEquals(
        Optional.empty(),
        ReversalLifecycleAcceptancePolicy.rejectionFor(
            capitalizationPosting,
            lifecycleStore(
                capitalizationPosting,
                Map.of(fixedAssetId, untouchedFixedAsset),
                Map.of(),
                Map.of())));
    assertEntrySemanticsCode(
        ReversalLifecycleAcceptancePolicy.rejectionFor(
                capitalizationPosting,
                lifecycleStore(
                    capitalizationPosting,
                    Map.of(fixedAssetId, depreciatedFixedAsset),
                    Map.of(),
                    Map.of()))
            .orElseThrow(),
        "fixed-asset-capitalization-reversal-requires-applications-reversed");
    assertEntrySemanticsCode(
        ReversalLifecycleAcceptancePolicy.rejectionFor(
                capitalizationPosting,
                lifecycleStore(
                    capitalizationPosting,
                    Map.of(fixedAssetId, disposedFixedAsset),
                    Map.of(),
                    Map.of()))
            .orElseThrow(),
        "fixed-asset-capitalization-reversal-requires-applications-reversed");

    FinancingArrangementId financingArrangementId = new FinancingArrangementId("term-loan-2026");
    FinancingBookkeepingEntryVariants.Borrowing borrowing = borrowing(financingArrangementId);
    CommittedPosting borrowingPosting = lifecyclePosting("posting-borrowing", borrowing);
    FinancingArrangementRecord untouchedBorrowing =
        financingArrangement(borrowing, Money.zero(EUR));
    FinancingArrangementRecord repaidBorrowing =
        financingArrangement(borrowing, Money.parse("EUR", "1.00"));
    FinancingArrangementRecord accruedInterestBorrowing =
        financingArrangementWithAccruedInterest(borrowing, Money.parse("EUR", "1.00"));

    assertThrows(
        IllegalStateException.class,
        () ->
            ReversalLifecycleAcceptancePolicy.rejectionFor(
                borrowingPosting, lifecycleStore(borrowingPosting, Map.of(), Map.of(), Map.of())));
    assertEquals(
        Optional.empty(),
        ReversalLifecycleAcceptancePolicy.rejectionFor(
            borrowingPosting,
            lifecycleStore(
                borrowingPosting,
                Map.of(),
                Map.of(financingArrangementId, untouchedBorrowing),
                Map.of())));
    assertEntrySemanticsCode(
        ReversalLifecycleAcceptancePolicy.rejectionFor(
                borrowingPosting,
                lifecycleStore(
                    borrowingPosting,
                    Map.of(),
                    Map.of(financingArrangementId, repaidBorrowing),
                    Map.of()))
            .orElseThrow(),
        "financing-borrowing-reversal-requires-applications-reversed");
    assertEntrySemanticsCode(
        ReversalLifecycleAcceptancePolicy.rejectionFor(
                borrowingPosting,
                lifecycleStore(
                    borrowingPosting,
                    Map.of(),
                    Map.of(financingArrangementId, accruedInterestBorrowing),
                    Map.of()))
            .orElseThrow(),
        "financing-borrowing-reversal-requires-applications-reversed");

    ForeignCurrencyObligationId obligationId = new ForeignCurrencyObligationId("usd-sale-2026");
    RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable obligation =
        foreignCurrencyReceivable(obligationId);
    CommittedPosting obligationPosting = lifecyclePosting("posting-obligation", obligation);

    assertThrows(
        IllegalStateException.class,
        () ->
            ReversalLifecycleAcceptancePolicy.rejectionFor(
                obligationPosting,
                lifecycleStore(obligationPosting, Map.of(), Map.of(), Map.of())));
    assertEquals(
        Optional.empty(),
        ReversalLifecycleAcceptancePolicy.rejectionFor(
            obligationPosting,
            lifecycleStore(
                obligationPosting,
                Map.of(),
                Map.of(),
                Map.of(obligationId, foreignCurrencyObligation(obligation, false)))));
    assertEntrySemanticsCode(
        ReversalLifecycleAcceptancePolicy.rejectionFor(
                obligationPosting,
                lifecycleStore(
                    obligationPosting,
                    Map.of(),
                    Map.of(),
                    Map.of(obligationId, foreignCurrencyObligation(obligation, true))))
            .orElseThrow(),
        "foreign-currency-obligation-reversal-requires-settlement-reversed");
  }

  @Test
  void rejectionFor_rejectsLifecycleOriginsUntilTheirDependentFactsAreReversed() {
    FixedAssetId fixedAssetId = new FixedAssetId("asset-delivery-van");
    FixedAssetBookkeepingEntryVariants.Capitalization capitalization = capitalization(fixedAssetId);
    CommittedPosting capitalizationPosting =
        lifecyclePosting("posting-capitalization", capitalization);
    FixedAssetRecord depreciatedAsset =
        new FixedAssetRecord(
            fixedAssetId,
            LocalDate.parse("2026-06-01"),
            capitalization.assetAccountCode(),
            capitalization.accumulatedDepreciationAccountCode(),
            capitalization.depreciationExpenseAccountCode(),
            capitalization.disposalGainAccountCode(),
            capitalization.disposalLossAccountCode(),
            Money.parse("EUR", "12000.00"),
            capitalization.depreciationSchedule(),
            Money.parse("EUR", "1000.00"),
            1,
            Optional.of(LocalDate.parse("2026-07-01")),
            Optional.empty());
    assertEntrySemanticsCode(
        ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-capitalization",
                    capitalizationPosting.postingId().value(),
                    negatedJournal(
                        capitalizationPosting.journalEntry(), LocalDate.parse("2026-07-01"))),
                new ReversalAcceptancePolicyTest.LifecyclePostingValidationStore(
                    capitalizationPosting,
                    Map.of(fixedAssetId, depreciatedAsset),
                    Map.of(),
                    Map.of()))
            .orElseThrow(),
        "fixed-asset-capitalization-reversal-requires-applications-reversed");

    FinancingArrangementId financingArrangementId = new FinancingArrangementId("term-loan-2026");
    FinancingBookkeepingEntryVariants.Borrowing borrowing = borrowing(financingArrangementId);
    CommittedPosting borrowingPosting = lifecyclePosting("posting-borrowing", borrowing);
    FinancingArrangementRecord repaidBorrowing =
        new FinancingArrangementRecord(
            financingArrangementId,
            LocalDate.parse("2026-06-01"),
            borrowing.principalLiabilityAccountCode(),
            borrowing.interestPayableAccountCode(),
            Money.parse("EUR", "10000.00"),
            Money.parse("EUR", "1000.00"),
            Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
            Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
            Optional.of(LocalDate.parse("2026-07-01")));
    assertEntrySemanticsCode(
        ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-borrowing",
                    borrowingPosting.postingId().value(),
                    negatedJournal(borrowingPosting.journalEntry(), LocalDate.parse("2026-07-01"))),
                new ReversalAcceptancePolicyTest.LifecyclePostingValidationStore(
                    borrowingPosting,
                    Map.of(),
                    Map.of(financingArrangementId, repaidBorrowing),
                    Map.of()))
            .orElseThrow(),
        "financing-borrowing-reversal-requires-applications-reversed");

    ForeignCurrencyObligationId obligationId = new ForeignCurrencyObligationId("usd-sale-2026");
    ForeignExchangeDetails initialForeignExchange =
        foreignExchange("USD", "10000.00", "EUR", "9200.00", "2026-07-01");
    RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable obligation =
        new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
            LocalDate.parse("2026-07-01"),
            obligationId,
            new AccountCode("accounts-receivable"),
            new AccountCode("sales-revenue"),
            new AccountCode("realized-foreign-exchange-gain"),
            new AccountCode("realized-foreign-exchange-loss"),
            initialForeignExchange);
    CommittedPosting obligationPosting = lifecyclePosting("posting-obligation", obligation);
    ForeignCurrencyObligationRecord settledObligation =
        new ForeignCurrencyObligationRecord(
            obligationId,
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-02"),
            obligation.receivableAccountCode(),
            obligation.realizedGainAccountCode(),
            obligation.realizedLossAccountCode(),
            Money.parse("USD", "10000.00"),
            Money.parse("EUR", "9200.00"),
            Optional.of(LocalDate.parse("2026-07-02")),
            Optional.of(Money.parse("EUR", "9300.00")),
            Optional.of(Money.parse("EUR", "100.00")),
            Optional.of(true));
    assertEntrySemanticsCode(
        ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-obligation",
                    obligationPosting.postingId().value(),
                    negatedJournal(
                        obligationPosting.journalEntry(), LocalDate.parse("2026-07-02"))),
                new ReversalAcceptancePolicyTest.LifecyclePostingValidationStore(
                    obligationPosting, Map.of(), Map.of(), Map.of(obligationId, settledObligation)))
            .orElseThrow(),
        "foreign-currency-obligation-reversal-requires-settlement-reversed");
  }

  private static FixedAssetBookkeepingEntryVariants.Capitalization capitalization(
      FixedAssetId fixedAssetId) {
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        LocalDate.parse("2026-06-01"),
        fixedAssetId,
        new AccountCode("delivery-van"),
        new AccountCode("delivery-van-accumulated-depreciation"),
        new AccountCode("depreciation-expense"),
        new AccountCode("fixed-asset-disposal-gain"),
        new AccountCode("fixed-asset-disposal-loss"),
        new AccountCode("cash"),
        MonetaryAmount.of(Money.parse("EUR", "12000.00")),
        new FixedAssetDepreciationSchedule(
            LocalDate.parse("2026-06-01"),
            12,
            MonetaryAmount.of(Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")))));
  }

  private static FinancingBookkeepingEntryVariants.Borrowing borrowing(
      FinancingArrangementId financingArrangementId) {
    return new FinancingBookkeepingEntryVariants.Borrowing(
        LocalDate.parse("2026-06-01"),
        financingArrangementId,
        new AccountCode("cash"),
        new AccountCode("term-loan-principal"),
        new AccountCode("term-loan-interest-payable"),
        MonetaryAmount.of(Money.parse("EUR", "10000.00")));
  }

  private static FixedAssetRecord fixedAssetRecord(
      FixedAssetBookkeepingEntryVariants.Capitalization capitalization,
      Money accumulatedDepreciation,
      int depreciationPeriodsApplied) {
    return new FixedAssetRecord(
        capitalization.fixedAssetId(),
        capitalization.effectiveDate(),
        capitalization.assetAccountCode(),
        capitalization.accumulatedDepreciationAccountCode(),
        capitalization.depreciationExpenseAccountCode(),
        capitalization.disposalGainAccountCode(),
        capitalization.disposalLossAccountCode(),
        capitalization.cost().toMoney(),
        capitalization.depreciationSchedule(),
        accumulatedDepreciation,
        depreciationPeriodsApplied,
        Optional.empty(),
        Optional.empty());
  }

  private static FinancingArrangementRecord financingArrangement(
      FinancingBookkeepingEntryVariants.Borrowing borrowing, Money principalRepaid) {
    return new FinancingArrangementRecord(
        borrowing.financingArrangementId(),
        borrowing.effectiveDate(),
        borrowing.principalLiabilityAccountCode(),
        borrowing.interestPayableAccountCode(),
        borrowing.principalAmount().toMoney(),
        principalRepaid,
        Money.zero(principalRepaid.currencyUnit()),
        Money.zero(principalRepaid.currencyUnit()),
        Optional.empty());
  }

  private static FixedAssetRecord disposedFixedAssetRecord(
      FixedAssetBookkeepingEntryVariants.Capitalization capitalization) {
    LocalDate disposalDate = LocalDate.parse("2026-07-01");
    return new FixedAssetRecord(
        capitalization.fixedAssetId(),
        capitalization.effectiveDate(),
        capitalization.assetAccountCode(),
        capitalization.accumulatedDepreciationAccountCode(),
        capitalization.depreciationExpenseAccountCode(),
        capitalization.disposalGainAccountCode(),
        capitalization.disposalLossAccountCode(),
        capitalization.cost().toMoney(),
        capitalization.depreciationSchedule(),
        Money.zero(EUR),
        0,
        Optional.of(disposalDate),
        Optional.of(disposalDate));
  }

  private static FinancingArrangementRecord financingArrangementWithAccruedInterest(
      FinancingBookkeepingEntryVariants.Borrowing borrowing, Money interestAccrued) {
    return new FinancingArrangementRecord(
        borrowing.financingArrangementId(),
        borrowing.effectiveDate(),
        borrowing.principalLiabilityAccountCode(),
        borrowing.interestPayableAccountCode(),
        borrowing.principalAmount().toMoney(),
        Money.zero(EUR),
        interestAccrued,
        Money.zero(EUR),
        Optional.empty());
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
      foreignCurrencyReceivable(ForeignCurrencyObligationId obligationId) {
    return new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
        LocalDate.parse("2026-07-01"),
        obligationId,
        new AccountCode("accounts-receivable"),
        new AccountCode("sales-revenue"),
        new AccountCode("realized-foreign-exchange-gain"),
        new AccountCode("realized-foreign-exchange-loss"),
        foreignExchange("USD", "10000.00", "EUR", "9200.00", "2026-07-01"));
  }

  private static ForeignCurrencyObligationRecord foreignCurrencyObligation(
      RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable obligation,
      boolean settled) {
    return new ForeignCurrencyObligationRecord(
        obligation.foreignCurrencyObligationId(),
        obligation.effectiveDate(),
        obligation.effectiveDate(),
        obligation.receivableAccountCode(),
        obligation.realizedGainAccountCode(),
        obligation.realizedLossAccountCode(),
        obligation.foreignExchangeDetails().transactionAmount().toMoney(),
        obligation.foreignExchangeDetails().functionalAmount().toMoney(),
        settled ? Optional.of(LocalDate.parse("2026-07-02")) : Optional.empty(),
        settled ? Optional.of(Money.parse("EUR", "9300.00")) : Optional.empty(),
        settled ? Optional.of(Money.parse("EUR", "100.00")) : Optional.empty(),
        settled ? Optional.of(true) : Optional.empty());
  }

  private static ReversalAcceptancePolicyTest.LifecyclePostingValidationStore lifecycleStore(
      CommittedPosting posting,
      Map<FixedAssetId, FixedAssetRecord> fixedAssets,
      Map<FinancingArrangementId, FinancingArrangementRecord> financingArrangements,
      Map<ForeignCurrencyObligationId, ForeignCurrencyObligationRecord> obligations) {
    return new ReversalAcceptancePolicyTest.LifecyclePostingValidationStore(
        posting, fixedAssets, financingArrangements, obligations);
  }
}

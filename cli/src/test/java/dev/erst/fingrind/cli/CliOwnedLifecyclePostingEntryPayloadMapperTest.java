package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Covers the deliberately minimal public payloads for executor-resolved lifecycle entries. */
class CliOwnedLifecyclePostingEntryPayloadMapperTest {
  @Test
  void entryPayload_preservesLifecycleEntryKindsWithoutInventingCallerFacts() {
    assertNull(CliPostingEntryPayloadMapper.entryPayload(null));
    assertEquals(
        "FINANCING_BORROWING",
        Objects.requireNonNull(
                CliPostingEntryPayloadMapper.entryPayload(
                    new FinancingBookkeepingEntryVariants.Borrowing(
                        LocalDate.parse("2026-06-01"),
                        new FinancingArrangementId("working-capital-001"),
                        new AccountCode("1000"),
                        new AccountCode("2100"),
                        new AccountCode("2101"),
                        money("10000"))))
            .entryKind());
    assertEquals(
        "FOREIGN_CURRENCY_OBLIGATION",
        Objects.requireNonNull(
                CliPostingEntryPayloadMapper.entryPayload(
                    new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
                        LocalDate.parse("2026-07-01"),
                        new ForeignCurrencyObligationId("usd-receivable-001"),
                        new AccountCode("1100"),
                        new AccountCode("4000"),
                        new AccountCode("4100"),
                        new AccountCode("5003"),
                        foreignExchangeDetails())))
            .entryKind());
    assertEquals(
        "FIXED_ASSET_CAPITALIZATION",
        Objects.requireNonNull(
                CliPostingEntryPayloadMapper.entryPayload(
                    new FixedAssetBookkeepingEntryVariants.Capitalization(
                        LocalDate.parse("2026-06-01"),
                        new FixedAssetId("delivery-van-001"),
                        new AccountCode("1600"),
                        new AccountCode("1601"),
                        new AccountCode("5000"),
                        new AccountCode("4100"),
                        new AccountCode("5001"),
                        new AccountCode("1000"),
                        money("12000"),
                        new FixedAssetDepreciationSchedule(
                            LocalDate.parse("2026-06-01"), 60, money("0")))))
            .entryKind());
  }

  private static ForeignExchangeDetails foreignExchangeDetails() {
    MonetaryAmount transactionAmount = MonetaryAmount.of(Money.parse("USD", "100.00"));
    MonetaryAmount functionalAmount = MonetaryAmount.of(Money.parse("EUR", "92.00"));
    return new ForeignExchangeDetails(
        transactionAmount,
        functionalAmount,
        new QuotedExchangeRate(
            transactionAmount, functionalAmount, LocalDate.parse("2026-07-01"), "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static MonetaryAmount money(String minorUnits) {
    return MonetaryAmount.of(Money.parse("EUR", minorUnits));
  }
}

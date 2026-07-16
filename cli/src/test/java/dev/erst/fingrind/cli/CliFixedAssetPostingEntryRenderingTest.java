package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliFixedAssetPostingJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Verifies JSON and text projections for every fixed-asset lifecycle state. */
class CliFixedAssetPostingEntryRenderingTest {
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode ASSET = new AccountCode("1600");
  private static final AccountCode ACCUMULATED_DEPRECIATION = new AccountCode("1601");
  private static final AccountCode DEPRECIATION_EXPENSE = new AccountCode("5000");
  private static final AccountCode DISPOSAL_GAIN = new AccountCode("4100");
  private static final AccountCode DISPOSAL_LOSS = new AccountCode("5001");

  @Test
  void entryPayload_rendersCapitalizationAndResolvedDepreciation() {
    CliPostingEntryPayload capitalization =
        CliFixedAssetPostingEntryPayloadMapper.entryPayload(capitalization());
    CliPostingEntryPayload depreciation =
        CliFixedAssetPostingEntryPayloadMapper.entryPayload(
            new FixedAssetBookkeepingEntryVariants.Depreciation(
                LocalDate.parse("2026-06-30"),
                new FixedAssetId("delivery-van-001"),
                new ResolvedFixedAssetDepreciation(
                    DEPRECIATION_EXPENSE, ACCUMULATED_DEPRECIATION, money("200"))));
    var capitalizationFacts = Objects.requireNonNull(capitalization.fixedAsset());
    var depreciationFacts = Objects.requireNonNull(depreciation.fixedAsset());

    assertEquals("CAPITALIZATION", capitalizationFacts.lifecycleKind());
    assertEquals(
        60, Objects.requireNonNull(capitalizationFacts.depreciationSchedule()).usefulLifeMonths());
    assertEquals("DEPRECIATION", depreciationFacts.lifecycleKind());
    assertEquals(
        "5000",
        Objects.requireNonNull(depreciationFacts.resolvedDepreciation())
            .depreciationExpenseAccountCode());
    assertTrue(
        CliPostingEntryTextRenderer.renderEntryFacts(capitalization).contains("Capitalized cost"));
    assertTrue(
        CliPostingEntryTextRenderer.renderEntryFacts(depreciation)
            .contains("Derived depreciation"));
  }

  @Test
  void entryPayload_rendersBothResolvedDisposalOutcomesAndUnresolvedState() {
    CliPostingEntryPayload unresolved =
        CliFixedAssetPostingEntryPayloadMapper.entryPayload(
            new FixedAssetBookkeepingEntryVariants.Disposal(
                LocalDate.parse("2026-07-01"),
                new FixedAssetId("delivery-van-001"),
                CASH,
                money("11000"),
                null));
    CliPostingEntryPayload gain =
        CliFixedAssetPostingEntryPayloadMapper.entryPayload(disposal(true, DISPOSAL_GAIN));
    CliPostingEntryPayload loss =
        CliFixedAssetPostingEntryPayloadMapper.entryPayload(disposal(false, DISPOSAL_LOSS));
    var unresolvedFacts = Objects.requireNonNull(unresolved.fixedAsset());
    var gainFacts = Objects.requireNonNull(gain.fixedAsset());
    var lossFacts = Objects.requireNonNull(loss.fixedAsset());

    assertNull(unresolvedFacts.resolvedDisposal());
    assertTrue(Objects.requireNonNull(gainFacts.resolvedDisposal()).gain());
    assertFalse(Objects.requireNonNull(lossFacts.resolvedDisposal()).gain());
    assertTrue(
        CliPostingEntryTextRenderer.renderEntryFacts(gain).contains("Derived disposal gain"));
    assertTrue(
        CliPostingEntryTextRenderer.renderEntryFacts(loss).contains("Derived disposal loss"));
  }

  @Test
  void entryPayload_keepsDepreciationUnresolvedUntilTheExecutorSuppliesIt() {
    CliPostingEntryPayload payload =
        CliFixedAssetPostingEntryPayloadMapper.entryPayload(
            new FixedAssetBookkeepingEntryVariants.Depreciation(
                LocalDate.parse("2026-06-30"), new FixedAssetId("delivery-van-001"), null));

    assertNull(Objects.requireNonNull(payload.fixedAsset()).resolvedDepreciation());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliFixedAssetPostingJsonModels.FixedAssetDepreciationSchedulePayload(
                "2026-06-01", 0, money("0")));
  }

  private static FixedAssetBookkeepingEntryVariants.Capitalization capitalization() {
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        LocalDate.parse("2026-06-01"),
        new FixedAssetId("delivery-van-001"),
        ASSET,
        ACCUMULATED_DEPRECIATION,
        DEPRECIATION_EXPENSE,
        DISPOSAL_GAIN,
        DISPOSAL_LOSS,
        CASH,
        money("12000"),
        new FixedAssetDepreciationSchedule(LocalDate.parse("2026-06-01"), 60, money("0")));
  }

  private static FixedAssetBookkeepingEntryVariants.Disposal disposal(
      boolean gain, AccountCode gainOrLossAccountCode) {
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        LocalDate.parse("2026-07-01"),
        new FixedAssetId("delivery-van-001"),
        CASH,
        money(gain ? "13000" : "10000"),
        new ResolvedFixedAssetDisposal(
            ASSET,
            ACCUMULATED_DEPRECIATION,
            gainOrLossAccountCode,
            money("12000"),
            money("2000"),
            money("10000"),
            money(gain ? "3000" : "1000"),
            gain));
  }

  private static MonetaryAmount money(String minorUnits) {
    return MonetaryAmount.of(Money.parse("EUR", minorUnits));
  }
}

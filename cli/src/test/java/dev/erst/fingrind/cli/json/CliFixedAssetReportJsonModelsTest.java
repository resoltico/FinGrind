package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Contract tests for fixed-asset machine-report lifecycle fields. */
class CliFixedAssetReportJsonModelsTest {
  @Test
  void fixedAssetRegisterRowPayload_rejectsHistoricalCarryingAmountWithoutDisposal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> fixedAssetRegisterRowPayload(money("800"), nullOf(), nullOf()));
  }

  @Test
  void fixedAssetRegisterRowPayload_requiresHistoricalCarryingAmountAfterDisposal() {
    assertThrows(
        IllegalArgumentException.class,
        () -> fixedAssetRegisterRowPayload(nullOf(), "2026-03-15", "2026-03-15"));
  }

  private static CliFixedAssetReportJsonModels.FixedAssetRegisterRowPayload
      fixedAssetRegisterRowPayload(
          CliReportValueJsonModels.MoneyPayload carryingAmountAtDisposal,
          String latestLifecycleEffectiveDate,
          String disposedOn) {
    return new CliFixedAssetReportJsonModels.FixedAssetRegisterRowPayload(
        "van-001",
        "2026-01-01",
        "1500",
        "1590",
        money("12000"),
        money("4000"),
        money("0"),
        carryingAmountAtDisposal,
        "2026-01-01",
        60,
        money("0"),
        20,
        latestLifecycleEffectiveDate,
        disposedOn);
  }

  private static CliReportValueJsonModels.MoneyPayload money(String minorUnits) {
    return new CliReportValueJsonModels.MoneyPayload("EUR", minorUnits);
  }
}

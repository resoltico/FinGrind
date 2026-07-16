package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterRow;
import dev.erst.fingrind.core.AccountCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers CSV truthfulness for settled, loss-making, and still-open lifecycle records. */
class CliLifecycleContextCsvProjectionTest {
  private static final Instant GENERATED_AT = Instant.parse("2026-07-15T12:00:00Z");

  @Test
  void render_fixedAssetRegister_keepsOptionalLifecycleDatesBlankWhenAbsent() {
    FixedAssetRegisterReport report =
        new FixedAssetRegisterReport(
            CliFixtureSupport.bookIdentity(),
            Optional.empty(),
            List.of(
                new FixedAssetRegisterRow(
                    new FixedAssetId("office-desk-001"),
                    LocalDate.parse("2026-06-01"),
                    new AccountCode("1600"),
                    new AccountCode("1601"),
                    money("12000"),
                    money("0"),
                    money("12000"),
                    new FixedAssetDepreciationSchedule(
                        LocalDate.parse("2026-06-01"), 60, money("0")),
                    0,
                    Optional.empty(),
                    Optional.empty())));

    String csv =
        CliFixedAssetRegisterCsvRenderer.render(
            CliReportPayloadMapper.fixedAssetRegister(report, GENERATED_AT));

    assertTrue(csv.contains("office-desk-001"));
    assertTrue(csv.endsWith(",,"));
  }

  @Test
  void render_realizedForeignExchangeRegister_distinguishesOpenAndLossSettlements() {
    RealizedForeignExchangeRegisterReport report =
        new RealizedForeignExchangeRegisterReport(
            CliFixtureSupport.bookIdentity(),
            List.of(
                foreignExchangeRow(
                    "open-usd-001",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()),
                foreignExchangeRow(
                    "loss-usd-001",
                    Optional.of(LocalDate.parse("2026-07-03")),
                    Optional.of(money("9000")),
                    Optional.of(money("200")),
                    Optional.of(false))));

    String csv =
        CliLifecycleContextRegisterCsvRenderer.render(
            CliReportPayloadMapper.realizedForeignExchangeRegister(report, GENERATED_AT));

    assertTrue(csv.contains("open-usd-001"));
    assertTrue(csv.contains("loss-usd-001"));
    assertTrue(csv.contains(",loss"));
  }

  @Test
  void render_financingRegister_dispatchesTheSharedLifecycleCsvProjection() {
    String csv =
        CliLifecycleContextRegisterCsvRenderer.render(
            CliReportPayloadMapper.financingRegister(
                ReportCrossFormatLifecycleContextFixture.financingRegisterReport(), GENERATED_AT));

    assertTrue(csv.contains("loan-working-capital-001"));
  }

  @Test
  void realizedForeignExchangePayload_rejectsIncompleteSettlementFacts() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels
                .RealizedForeignExchangeRegisterRowPayload(
                "usd-receivable-001",
                "2026-07-01",
                "2026-07-03",
                "1100",
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload(
                    "USD", "10000"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9200"),
                null,
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9500"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "300"),
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels
                .RealizedForeignExchangeRegisterRowPayload(
                "usd-receivable-002",
                "2026-07-01",
                "2026-07-03",
                "1100",
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload(
                    "USD", "10000"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9200"),
                null,
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9500"),
                NullTestSupport.nullOf(
                    dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload.class),
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels
                .RealizedForeignExchangeRegisterRowPayload(
                "usd-receivable-003",
                "2026-07-01",
                "2026-07-03",
                "1100",
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload(
                    "USD", "10000"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9200"),
                null,
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9500"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "300"),
                NullTestSupport.nullOf(Boolean.class)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels
                .RealizedForeignExchangeRegisterRowPayload(
                "usd-receivable-004",
                "2026-07-01",
                "2026-07-03",
                "1100",
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload(
                    "USD", "10000"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9200"),
                null,
                NullTestSupport.nullOf(
                    dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload.class),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "300"),
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels
                .RealizedForeignExchangeRegisterRowPayload(
                "usd-receivable-005",
                "2026-07-01",
                "2026-07-03",
                "1100",
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload(
                    "USD", "10000"),
                new dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload("EUR", "9200"),
                null,
                NullTestSupport.nullOf(
                    dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload.class),
                NullTestSupport.nullOf(
                    dev.erst.fingrind.cli.json.CliReportValueJsonModels.MoneyPayload.class),
                true));
  }

  private static RealizedForeignExchangeRegisterRow foreignExchangeRow(
      String id,
      Optional<LocalDate> settledOn,
      Optional<MonetaryAmount> settlementAmount,
      Optional<MonetaryAmount> gainOrLossAmount,
      Optional<Boolean> gain) {
    return new RealizedForeignExchangeRegisterRow(
        new ForeignCurrencyObligationId(id),
        LocalDate.parse("2026-07-01"),
        LocalDate.parse("2026-07-03"),
        new AccountCode("1100"),
        new MonetaryAmount("USD", "10000"),
        money("9200"),
        settledOn,
        settlementAmount,
        gainOrLossAmount,
        gain);
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }
}

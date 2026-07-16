package dev.erst.fingrind.contract.reportmodel;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract coverage for realized-FX write facts, retained obligations, and public reporting. */
class RealizedForeignExchangeContextContractTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-07-15");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode RECEIVABLE = new AccountCode("1200");
  private static final AccountCode REVENUE = new AccountCode("4000");
  private static final AccountCode GAIN = new AccountCode("4800");
  private static final AccountCode LOSS = new AccountCode("6800");

  @Test
  void foreignCurrencyObligationAndEverySettlementOutcomeDeriveExactJournals() {
    RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable =
        new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
            EFFECTIVE_DATE,
            obligationId(),
            RECEIVABLE,
            REVENUE,
            GAIN,
            LOSS,
            exchange("1100", "1000"));
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement gain =
        settlement("1100", "1000", resolution("900", "100", true));
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement loss =
        settlement("880", "800", resolution("900", "100", false));
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement neutral =
        settlement("990", "900", resolution("900", "0", true));
    RealizedForeignExchangeBookkeepingEntryVariants.Settlement neutralLoss =
        settlement("990", "900", resolution("900", "0", false));

    assertEntry(
        receivable,
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        PostingOriginKind.FOREIGN_CURRENCY_OBLIGATION,
        2);
    assertEntry(
        gain,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        3);
    assertEntry(
        loss,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        3);
    assertEntry(
        neutral,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        2);
    assertEntry(
        neutralLoss,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        PostingOriginKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        2);
    assertEquals(REVENUE, receivable.journalEntry().lines().getLast().accountCode());
    assertEquals(GAIN, gain.journalEntry().lines().getLast().accountCode());
    assertEquals(LOSS, loss.journalEntry().lines().get(1).accountCode());
    assertThrows(
        IllegalStateException.class,
        () -> settlement("1100", "1000", resolution("900", "99", true)).journalEntry());
    assertEquals(
        "realizedForeignExchangeSettlement requires executor-resolved facts.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
                            EFFECTIVE_DATE, obligationId(), CASH, exchange("1100", "1000"), null)
                        .journalEntry())
            .getMessage());
  }

  @Test
  void identifiersAndResolvedSettlementFactsDefendTheirPublishedInvariants() {
    assertEquals(
        "customer-invoice-2026-001",
        new ForeignCurrencyObligationId(" customer-invoice-2026-001 ").value());
    assertThrows(
        IllegalArgumentException.class, () -> new ForeignCurrencyObligationId("UPPERCASE"));
    assertThrows(IllegalArgumentException.class, () -> new ForeignCurrencyObligationId(" "));
    assertThrows(
        IllegalArgumentException.class, () -> new ForeignCurrencyObligationId("a".repeat(121)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedRealizedForeignExchangeSettlement(
                RECEIVABLE, GAIN, money("0"), money("0"), true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedRealizedForeignExchangeSettlement(
                RECEIVABLE, GAIN, money("900"), usd("100"), true));
    assertThrows(
        NullPointerException.class,
        () ->
            new ResolvedRealizedForeignExchangeSettlement(
                nullOf(), GAIN, money("900"), money("100"), true));
  }

  @Test
  void obligationRegisterPreservesOpenGainAndLossStatesAcrossReportsAndResults() {
    RealizedForeignExchangeRegisterReport report =
        new RealizedForeignExchangeRegisterReport(
            ReportModelTestSupport.bookIdentity(),
            List.of(settledRow("1000", "100", true), settledRow("800", "100", false), openRow()));
    ReportModel model = RealizedForeignExchangeRegisterReportModelBuilder.INSTANCE.build(report);
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());
    RealizedForeignExchangeRegisterResult.Reported reported =
        new RealizedForeignExchangeRegisterResult.Reported(report);
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    RealizedForeignExchangeRegisterResult.Rejected rejected =
        new RealizedForeignExchangeRegisterResult.Rejected(rejection);

    assertEquals("realized-foreign-exchange-register", model.family());
    assertEquals("EUR 9.00", model.sections().getFirst().rows().getFirst().cells().get(2));
    assertEquals("gain", csv.rows().getFirst().get(csv.headers().indexOf("realizedResult")));
    assertEquals("loss", csv.rows().get(1).get(csv.headers().indexOf("realizedResult")));
    assertEquals("", csv.rows().get(2).get(csv.headers().indexOf("realizedResult")));
    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertNull(rejected.reported());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(value -> "reported", value -> "rejected"));
    assertEquals("rejected", rejected.fold(value -> "reported", value -> "rejected"));
    assertEquals(
        new RealizedForeignExchangeRegisterQuery(), new RealizedForeignExchangeRegisterQuery());

    ReportModel empty =
        RealizedForeignExchangeRegisterReportModelBuilder.buildModel(
            new RealizedForeignExchangeRegisterReport(
                ReportModelTestSupport.bookIdentity(), List.of()));
    assertTrue(
        empty
            .sections()
            .getFirst()
            .verdicts()
            .getFirst()
            .value()
            .contains("No foreign-currency obligations matched"));
  }

  @Test
  void obligationRowsRejectImpossibleCurrencyHorizonAndSettlementShapes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.minusDays(1),
                usd("1100"),
                money("1000"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.plusMonths(1),
                money("1100"),
                money("1000"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.plusMonths(1),
                usd("1100"),
                money("1000"),
                Optional.of(EFFECTIVE_DATE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.plusMonths(1),
                usd("1100"),
                money("1000"),
                Optional.of(EFFECTIVE_DATE),
                Optional.of(usd("1000")),
                Optional.of(money("100")),
                Optional.of(true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.plusMonths(1),
                usd("1100"),
                money("1000"),
                Optional.of(EFFECTIVE_DATE),
                Optional.of(money("1000")),
                Optional.of(usd("100")),
                Optional.of(true)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.plusMonths(1),
                usd("1100"),
                money("1000"),
                Optional.of(EFFECTIVE_DATE),
                Optional.of(money("1000")),
                Optional.empty(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            row(
                EFFECTIVE_DATE.plusMonths(1),
                usd("1100"),
                money("1000"),
                Optional.of(EFFECTIVE_DATE),
                Optional.of(money("1000")),
                Optional.of(money("100")),
                Optional.empty()));
  }

  private static void assertEntry(
      RealizedForeignExchangeBookkeepingEntryVariants entry,
      BookkeepingEntryKind expectedEntryKind,
      PostingOriginKind expectedOriginKind,
      int expectedLineCount) {
    assertEquals(expectedEntryKind, entry.entryKind());
    assertEquals(expectedOriginKind, entry.postingOriginKind());
    assertEquals(expectedLineCount, entry.journalEntry().lines().size());
  }

  private static RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement(
      String transactionAmount,
      String functionalAmount,
      ResolvedRealizedForeignExchangeSettlement resolvedSettlement) {
    return new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
        EFFECTIVE_DATE,
        obligationId(),
        CASH,
        exchange(transactionAmount, functionalAmount),
        resolvedSettlement);
  }

  private static ResolvedRealizedForeignExchangeSettlement resolution(
      String carryingAmount, String gainOrLossAmount, boolean gain) {
    return new ResolvedRealizedForeignExchangeSettlement(
        RECEIVABLE, gain ? GAIN : LOSS, money(carryingAmount), money(gainOrLossAmount), gain);
  }

  private static RealizedForeignExchangeRegisterRow openRow() {
    return row(
        EFFECTIVE_DATE.plusMonths(1),
        usd("1100"),
        money("1000"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static RealizedForeignExchangeRegisterRow settledRow(
      String functionalSettlementAmount, String gainOrLossAmount, boolean gain) {
    return row(
        EFFECTIVE_DATE.plusMonths(1),
        usd("1100"),
        money("900"),
        Optional.of(EFFECTIVE_DATE.plusDays(1)),
        Optional.of(money(functionalSettlementAmount)),
        Optional.of(money(gainOrLossAmount)),
        Optional.of(gain));
  }

  private static RealizedForeignExchangeRegisterRow row(
      LocalDate horizon,
      MonetaryAmount transactionAmount,
      MonetaryAmount functionalCarryingAmount,
      Optional<LocalDate> settledOn,
      Optional<MonetaryAmount> functionalSettlementAmount,
      Optional<MonetaryAmount> realizedGainOrLossAmount,
      Optional<Boolean> realizedGain) {
    return new RealizedForeignExchangeRegisterRow(
        obligationId(),
        EFFECTIVE_DATE,
        horizon,
        RECEIVABLE,
        transactionAmount,
        functionalCarryingAmount,
        settledOn,
        functionalSettlementAmount,
        realizedGainOrLossAmount,
        realizedGain);
  }

  private static ForeignExchangeDetails exchange(
      String transactionAmount, String functionalAmount) {
    return new ForeignExchangeDetails(
        usd(transactionAmount),
        money(functionalAmount),
        new QuotedExchangeRate(usd("11"), money("10"), EFFECTIVE_DATE, "ECB"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static ForeignCurrencyObligationId obligationId() {
    return new ForeignCurrencyObligationId("customer-invoice-2026-001");
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static MonetaryAmount usd(String minorUnits) {
    return new MonetaryAmount("USD", minorUnits);
  }
}

package dev.erst.fingrind.contract.reportmodel;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDepreciation;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFixedAssetDisposal;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract coverage for fixed-asset write facts, lifecycle state, and public reporting. */
class FixedAssetContextContractTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-07-15");
  private static final AccountCode ASSET = new AccountCode("1500");
  private static final AccountCode ACCUMULATED_DEPRECIATION = new AccountCode("1590");
  private static final AccountCode DEPRECIATION_EXPENSE = new AccountCode("6200");
  private static final AccountCode DISPOSAL_GAIN = new AccountCode("4800");
  private static final AccountCode DISPOSAL_LOSS = new AccountCode("6800");
  private static final AccountCode CASH = new AccountCode("1000");

  @Test
  void capitalizationOwnsItsJournalOriginAndScheduleInvariants() {
    FixedAssetBookkeepingEntryVariants.Capitalization capitalization = capitalization();

    assertEquals(BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION, capitalization.entryKind());
    assertEquals(PostingOriginKind.FIXED_ASSET_CAPITALIZATION, capitalization.postingOriginKind());
    assertEquals(ASSET, capitalization.journalEntry().lines().getFirst().accountCode());
    assertEquals(CASH, capitalization.journalEntry().lines().getLast().accountCode());
    assertEquals("laptop-2026-001", capitalization.fixedAssetId().value());

    assertThrows(IllegalArgumentException.class, () -> new FixedAssetId("UPPERCASE"));
    assertThrows(IllegalArgumentException.class, () -> new FixedAssetId(" "));
    assertThrows(IllegalArgumentException.class, () -> new FixedAssetId("a".repeat(121)));
    assertEquals("laptop-2026-001", new FixedAssetId(" laptop-2026-001 ").value());

    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 0, money("100")));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 1_201, money("100")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            capitalizationWith(new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 60, usd("100"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            capitalizationWith(
                new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 60, money("1000"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            capitalizationWith(
                new FixedAssetDepreciationSchedule(EFFECTIVE_DATE.minusDays(1), 60, money("100"))));
  }

  @Test
  void executorResolvedDepreciationAndEveryDisposalResultDeriveBalancedJournals() {
    FixedAssetBookkeepingEntryVariants.Depreciation depreciation =
        new FixedAssetBookkeepingEntryVariants.Depreciation(
            EFFECTIVE_DATE,
            assetId(),
            new ResolvedFixedAssetDepreciation(
                DEPRECIATION_EXPENSE, ACCUMULATED_DEPRECIATION, money("200")));
    FixedAssetBookkeepingEntryVariants.Depreciation unresolvedDepreciation =
        new FixedAssetBookkeepingEntryVariants.Depreciation(EFFECTIVE_DATE, assetId(), null);

    assertEquals(BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION, depreciation.entryKind());
    assertEquals(PostingOriginKind.FIXED_ASSET_DEPRECIATION, depreciation.postingOriginKind());
    assertEquals(
        DEPRECIATION_EXPENSE, depreciation.journalEntry().lines().getFirst().accountCode());
    assertEquals(
        "fixed-asset depreciation requires executor resolution",
        assertThrows(NullPointerException.class, unresolvedDepreciation::journalEntry)
            .getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedFixedAssetDepreciation(
                DEPRECIATION_EXPENSE, ACCUMULATED_DEPRECIATION, money("0")));

    FixedAssetBookkeepingEntryVariants.Disposal gain =
        disposal("1000", resolvedDisposal("1000", "200", "800", "200", true));
    FixedAssetBookkeepingEntryVariants.Disposal loss =
        disposal("600", resolvedDisposal("1000", "200", "800", "200", false));
    FixedAssetBookkeepingEntryVariants.Disposal neutral =
        disposal("800", resolvedDisposal("1000", "200", "800", "0", true));
    FixedAssetBookkeepingEntryVariants.Disposal noProceeds =
        disposal("0", resolvedDisposal("1000", "1000", "0", "0", false));

    assertEquals(BookkeepingEntryKind.FIXED_ASSET_DISPOSAL, gain.entryKind());
    assertEquals(PostingOriginKind.FIXED_ASSET_DISPOSAL, gain.postingOriginKind());
    assertEquals(4, gain.journalEntry().lines().size());
    assertEquals(4, loss.journalEntry().lines().size());
    assertEquals(3, neutral.journalEntry().lines().size());
    assertEquals(2, noProceeds.journalEntry().lines().size());
    assertThrows(
        IllegalStateException.class,
        () -> disposal("1000", resolvedDisposal("1000", "200", "800", "199", true)).journalEntry());
    assertThrows(
        IllegalArgumentException.class, () -> resolvedDisposal("1000", "200", "799", "201", true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedFixedAssetDisposal(
                ASSET,
                ACCUMULATED_DEPRECIATION,
                DISPOSAL_GAIN,
                money("1000"),
                money("1001"),
                money("0"),
                money("0"),
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedFixedAssetDisposal(
                ASSET,
                ACCUMULATED_DEPRECIATION,
                DISPOSAL_GAIN,
                money("1000"),
                usd("200"),
                money("800"),
                money("200"),
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedFixedAssetDisposal(
                ASSET,
                ACCUMULATED_DEPRECIATION,
                DISPOSAL_GAIN,
                money("1000"),
                money("200"),
                usd("800"),
                money("200"),
                true));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ResolvedFixedAssetDisposal(
                ASSET,
                ACCUMULATED_DEPRECIATION,
                DISPOSAL_GAIN,
                money("1000"),
                money("200"),
                money("800"),
                usd("200"),
                true));
  }

  @Test
  void fixedAssetRegisterKeepsExactLifecycleStateAcrossResultsAndFormats() {
    FixedAssetRegisterRow row = registerRow(Optional.of(EFFECTIVE_DATE.plusMonths(3)));
    FixedAssetRegisterReport report =
        new FixedAssetRegisterReport(
            ReportModelTestSupport.bookIdentity(),
            Optional.of(EFFECTIVE_DATE.plusMonths(3)),
            List.of(row));
    ReportModel model = FixedAssetRegisterReportModelBuilder.INSTANCE.build(report);
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());
    FixedAssetRegisterResult.Reported reported = new FixedAssetRegisterResult.Reported(report);
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    FixedAssetRegisterResult.Rejected rejected = new FixedAssetRegisterResult.Rejected(rejection);

    assertEquals("fixed-asset-register", model.family());
    assertEquals(
        ProtocolCatalog.operation(OperationId.FIXED_ASSET_REGISTER).displayLabel(), model.title());
    assertEquals(
        "laptop-2026-001", model.sections().getFirst().rows().getFirst().cells().getFirst());
    assertEquals("1000", csv.rows().getFirst().get(csv.headers().indexOf("costMinorUnits")));
    assertEquals(
        "800",
        csv.rows().getFirst().get(csv.headers().indexOf("carryingAmountAtDisposalMinorUnits")));
    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertNull(rejected.reported());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(value -> "reported", value -> "rejected"));
    assertEquals("rejected", rejected.fold(value -> "reported", value -> "rejected"));
    assertEquals(
        new FixedAssetRegisterQuery(Optional.of(EFFECTIVE_DATE)),
        new FixedAssetRegisterQuery(Optional.of(EFFECTIVE_DATE)));

    ReportModel empty =
        FixedAssetRegisterReportModelBuilder.buildModel(
            new FixedAssetRegisterReport(
                ReportModelTestSupport.bookIdentity(), Optional.empty(), List.of()));
    assertTrue(
        empty
            .sections()
            .getFirst()
            .verdicts()
            .getFirst()
            .value()
            .contains("No fixed assets matched"));
    assertThrows(NullPointerException.class, () -> new FixedAssetRegisterQuery(nullOf()));
    assertThrows(IllegalArgumentException.class, () -> registerRow(-1, money("200"), money("800")));
    assertThrows(IllegalArgumentException.class, () -> registerRow(1, usd("200"), money("800")));
    assertThrows(IllegalArgumentException.class, () -> registerRow(1, money("200"), usd("800")));
    assertThrows(IllegalArgumentException.class, () -> registerRow(1, money("200"), money("799")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRegisterRow(
                money("800"),
                Optional.of(money("800")),
                Optional.of(EFFECTIVE_DATE.plusMonths(3))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRegisterRow(
                money("0"), Optional.of(usd("800")), Optional.of(EFFECTIVE_DATE.plusMonths(3))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            fixedAssetRegisterRow(
                money("0"), Optional.empty(), Optional.of(EFFECTIVE_DATE.plusMonths(3))));
    assertThrows(
        IllegalArgumentException.class,
        () -> fixedAssetRegisterRow(money("800"), Optional.of(money("800")), Optional.empty()));
  }

  private static FixedAssetBookkeepingEntryVariants.Capitalization capitalization() {
    return capitalizationWith(new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 60, money("100")));
  }

  private static FixedAssetBookkeepingEntryVariants.Capitalization capitalizationWith(
      FixedAssetDepreciationSchedule schedule) {
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        EFFECTIVE_DATE,
        assetId(),
        ASSET,
        ACCUMULATED_DEPRECIATION,
        DEPRECIATION_EXPENSE,
        DISPOSAL_GAIN,
        DISPOSAL_LOSS,
        CASH,
        money("1000"),
        schedule);
  }

  private static FixedAssetBookkeepingEntryVariants.Disposal disposal(
      String proceeds, ResolvedFixedAssetDisposal resolved) {
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        EFFECTIVE_DATE, assetId(), CASH, money(proceeds), resolved);
  }

  private static ResolvedFixedAssetDisposal resolvedDisposal(
      String cost,
      String accumulatedDepreciation,
      String carryingAmount,
      String gainOrLossAmount,
      boolean gain) {
    return new ResolvedFixedAssetDisposal(
        ASSET,
        ACCUMULATED_DEPRECIATION,
        gain ? DISPOSAL_GAIN : DISPOSAL_LOSS,
        money(cost),
        money(accumulatedDepreciation),
        money(carryingAmount),
        money(gainOrLossAmount),
        gain);
  }

  private static FixedAssetRegisterRow registerRow(Optional<LocalDate> disposedOn) {
    return fixedAssetRegisterRow(
        disposedOn.isPresent() ? money("0") : money("800"),
        disposedOn.map(ignored -> money("800")),
        disposedOn);
  }

  private static FixedAssetRegisterRow fixedAssetRegisterRow(
      MonetaryAmount carryingAmount,
      Optional<MonetaryAmount> carryingAmountAtDisposal,
      Optional<LocalDate> disposedOn) {
    return new FixedAssetRegisterRow(
        assetId(),
        EFFECTIVE_DATE,
        ASSET,
        ACCUMULATED_DEPRECIATION,
        money("1000"),
        money("200"),
        carryingAmount,
        carryingAmountAtDisposal,
        new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 60, money("100")),
        1,
        Optional.of(EFFECTIVE_DATE.plusMonths(1)),
        disposedOn);
  }

  private static FixedAssetRegisterRow registerRow(
      int periodsApplied, MonetaryAmount accumulatedDepreciation, MonetaryAmount carryingAmount) {
    return new FixedAssetRegisterRow(
        assetId(),
        EFFECTIVE_DATE,
        ASSET,
        ACCUMULATED_DEPRECIATION,
        money("1000"),
        accumulatedDepreciation,
        carryingAmount,
        Optional.empty(),
        new FixedAssetDepreciationSchedule(EFFECTIVE_DATE, 60, money("100")),
        periodsApplied,
        Optional.of(EFFECTIVE_DATE),
        Optional.empty());
  }

  private static FixedAssetId assetId() {
    return new FixedAssetId("laptop-2026-001");
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static MonetaryAmount usd(String minorUnits) {
    return new MonetaryAmount("USD", minorUnits);
  }
}

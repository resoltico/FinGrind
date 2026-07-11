package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.InventoryValuationAccount;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationMovement;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds the shared report model for exact inventory-pool valuation. */
public final class InventoryValuationReportModelBuilder
    implements ReportModelBuilder<InventoryValuationReport> {
  private static final String REPORT_FAMILY = OperationId.INVENTORY_VALUATION.wireName();
  private static final List<String> CSV_HEADERS =
      List.of(
          "exportFamily",
          "rowId",
          "recordKind",
          "effectiveDateAsOf",
          "inventoryAccountCode",
          "inventoryAccountName",
          "unitOfMeasure",
          "quantityScale",
          "quantityOnHand",
          "roundedMovingAverageUnitCostProjectionCurrencyCode",
          "roundedMovingAverageUnitCostProjectionMinorUnits",
          "carryingValueCurrencyCode",
          "carryingValueMinorUnits",
          "movementPostingId",
          "movementEffectiveDate",
          "movementAccountSequence",
          "movementKind",
          "movementQuantityDeltaScaledUnits",
          "movementCostDeltaMinor",
          "message");

  /** Shared reusable builder instance. */
  public static final InventoryValuationReportModelBuilder INSTANCE =
      new InventoryValuationReportModelBuilder();

  private InventoryValuationReportModelBuilder() {}

  @Override
  public ReportModel build(InventoryValuationReport report) {
    return buildModel(report);
  }

  /** Builds one inventory-valuation report model. */
  public static ReportModel buildModel(InventoryValuationReport report) {
    List<ReportSection> sections = new ArrayList<>();
    sections.add(accountSection(report));
    if (report.includesMovements()) {
      report.accounts().forEach(account -> sections.add(movementSection(account)));
    }
    return new ReportModel(
        REPORT_FAMILY,
        "Inventory Valuation",
        ReportModel.Orientation.LANDSCAPE,
        ReportModelSupport.context(
            report.bookIdentity(),
            null,
            null,
            null,
            report.effectiveDateAsOf().orElse(null),
            EffectiveDateRange.unbounded(),
            List.of(
                new ReportVerdict(
                    "Carrying value truth", "Exact cost pool in currency minor units"),
                new ReportVerdict(
                    "Unit-cost projection",
                    "Informational only; never used to calculate carrying value or cost of sales"))),
        List.of(
            new ReportVerdict(
                "Valued inventory accounts", Integer.toString(report.accounts().size())),
            new ReportVerdict(
                "Movement detail", report.includesMovements() ? "Included" : "Not requested")),
        sections,
        csvProjection(report));
  }

  private static ReportCsvProjection csvProjection(InventoryValuationReport report) {
    if (report.accounts().isEmpty()) {
      return new ReportCsvProjection(
          CSV_HEADERS,
          List.of(
              emptyCsvRow(
                  report,
                  REPORT_FAMILY + ":scope-empty",
                  ReportModelNarrative.noMatches("inventory accounts"))));
    }
    List<List<String>> rows = new ArrayList<>();
    report.accounts().forEach(account -> addAccountCsvRows(report, account, rows));
    return new ReportCsvProjection(CSV_HEADERS, List.copyOf(rows));
  }

  private static void addAccountCsvRows(
      InventoryValuationReport report, InventoryValuationAccount account, List<List<String>> rows) {
    if (!report.includesMovements() || account.movements().isEmpty()) {
      rows.add(
          csvRow(
              report,
              REPORT_FAMILY + ":account:" + account.inventoryAccountCode().value(),
              account,
              null,
              ""));
      return;
    }
    account
        .movements()
        .forEach(
            movement ->
                rows.add(
                    csvRow(
                        report,
                        REPORT_FAMILY
                            + ":movement:"
                            + account.inventoryAccountCode().value()
                            + ":"
                            + movement.accountSequence(),
                        account,
                        movement,
                        "")));
  }

  private static List<String> emptyCsvRow(
      InventoryValuationReport report, String rowId, String message) {
    return csvRow(report, rowId, null, null, message);
  }

  private static List<String> csvRow(
      InventoryValuationReport report,
      String rowId,
      @Nullable InventoryValuationAccount account,
      @Nullable InventoryValuationMovement movement,
      String message) {
    List<String> row = new ArrayList<>(CSV_HEADERS.size());
    row.add(REPORT_FAMILY);
    row.add(rowId);
    row.add(REPORT_FAMILY);
    row.add(report.effectiveDateAsOf().map(Object::toString).orElse(""));
    if (account == null) {
      for (int index = 0; index < 9; index++) {
        row.add("");
      }
    } else {
      row.add(account.inventoryAccountCode().value());
      row.add(account.inventoryAccountName().value());
      row.add(account.unitOfMeasure().token());
      row.add(Integer.toString(account.unitOfMeasure().quantityScale()));
      row.add(account.quantityOnHand().canonicalDecimal());
      row.add(
          account.roundedMovingAverageUnitCostProjection() == null
              ? ""
              : account.roundedMovingAverageUnitCostProjection().currencyCode());
      row.add(
          account.roundedMovingAverageUnitCostProjection() == null
              ? ""
              : account.roundedMovingAverageUnitCostProjection().minorUnits());
      row.add(account.carryingValue().currencyCode());
      row.add(account.carryingValue().minorUnits());
    }
    if (movement == null) {
      for (int index = 0; index < 6; index++) {
        row.add("");
      }
    } else {
      row.add(movement.postingId().value());
      row.add(movement.effectiveDate().toString());
      row.add(Long.toString(movement.accountSequence()));
      row.add(movement.kind().wireValue());
      row.add(Long.toString(movement.quantityDeltaScaledUnits()));
      row.add(Long.toString(movement.costDeltaMinor()));
    }
    row.add(message);
    return List.copyOf(row);
  }

  private static ReportSection accountSection(InventoryValuationReport report) {
    return ReportModelSupport.section(
        "accounts",
        "Inventory accounts",
        report.accounts().isEmpty()
            ? List.of(
                new ReportVerdict("Outcome", ReportModelNarrative.noMatches("inventory accounts")))
            : List.of(),
        List.of(
            ReportModelSupport.leftColumn("accountCode", "Account"),
            ReportModelSupport.leftColumn("accountName", "Name"),
            ReportModelSupport.leftColumn("unitOfMeasure", "Unit of measure"),
            ReportModelSupport.rightColumn("quantityOnHand", "Quantity on hand"),
            ReportModelSupport.rightColumn(
                "roundedMovingAverageUnitCostProjection", "Unit cost (informational)"),
            ReportModelSupport.rightColumn("carryingValue", "Carrying value (exact pool)")),
        report.accounts().stream().map(InventoryValuationReportModelBuilder::accountRow).toList(),
        List.of());
  }

  private static ReportRow accountRow(InventoryValuationAccount account) {
    return ReportModelSupport.row(
        account.inventoryAccountCode().value(),
        account.inventoryAccountCode().value(),
        account.inventoryAccountName().value(),
        account.unitOfMeasure().token(),
        account.quantityOnHand().canonicalDecimal(),
        account.roundedMovingAverageUnitCostProjection() == null
            ? "Not applicable"
            : ReportModelDisplay.displayAmount(account.roundedMovingAverageUnitCostProjection()),
        ReportModelDisplay.displayAmount(account.carryingValue()));
  }

  private static ReportSection movementSection(InventoryValuationAccount account) {
    return ReportModelSupport.section(
        "movements-" + account.inventoryAccountCode().value(),
        "Movements: " + account.inventoryAccountCode().value(),
        account.movements().isEmpty()
            ? List.of(
                new ReportVerdict("Outcome", ReportModelNarrative.noMatches("inventory movements")))
            : List.of(),
        List.of(
            ReportModelSupport.leftColumn("effectiveDate", "Effective date"),
            ReportModelSupport.rightColumn("accountSequence", "Account sequence"),
            ReportModelSupport.leftColumn("kind", "Kind"),
            ReportModelSupport.rightColumn("quantityDeltaScaledUnits", "Quantity delta (scaled)"),
            ReportModelSupport.rightColumn("costDeltaMinor", "Cost delta (minor)"),
            ReportModelSupport.leftColumn("postingId", "Posting id")),
        account.movements().stream()
            .map(InventoryValuationReportModelBuilder::movementRow)
            .toList(),
        List.of());
  }

  private static ReportRow movementRow(InventoryValuationMovement movement) {
    return ReportModelSupport.row(
        movement.postingId().value(),
        movement.effectiveDate().toString(),
        Long.toString(movement.accountSequence()),
        movement.kind().wireValue(),
        Long.toString(movement.quantityDeltaScaledUnits()),
        Long.toString(movement.costDeltaMinor()),
        movement.postingId().value());
  }
}

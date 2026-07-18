package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.BookDoctrineDisplay;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared display and validation helpers for report-model builders. */
final class ReportModelSupport {
  private ReportModelSupport() {}

  static ReportColumn leftColumn(String key, String title) {
    return new ReportColumn(key, title, ReportColumn.Alignment.LEFT);
  }

  static ReportColumn rightColumn(String key, String title) {
    return new ReportColumn(key, title, ReportColumn.Alignment.RIGHT);
  }

  static String reportTitle(OperationId operationId) {
    return ProtocolCatalog.operation(
            ContractDescriptorValidation.requireValue(operationId, "operationId"))
        .displayLabel();
  }

  static ReportRow row(String rowId, String... cells) {
    return new ReportRow(rowId, List.of(cells));
  }

  static ReportTotals totals(
      String key, String title, List<ReportColumn> columns, List<ReportRow> rows) {
    return new ReportTotals(key, title, columns, rows);
  }

  static ReportSection section(
      String key,
      String title,
      List<ReportVerdict> verdicts,
      List<ReportColumn> columns,
      List<ReportRow> rows,
      List<ReportTotals> totals) {
    return new ReportSection(key, title, verdicts, columns, rows, totals);
  }

  static ReportContext context(
      BookIdentity bookIdentity,
      @Nullable PostingCoverage postingCoverage,
      @Nullable LocalDate periodStart,
      @Nullable LocalDate periodEnd,
      @Nullable LocalDate asOf,
      EffectiveDateRange comparativePeriod,
      List<ReportVerdict> supplementalRows) {
    ContractDescriptorValidation.requireValue(bookIdentity, "bookIdentity");
    ContractDescriptorValidation.requireValue(comparativePeriod, "comparativePeriod");
    return new ReportContext(
        bookIdentity.entityName().value(),
        BookDoctrineDisplay.bookTemplate(bookIdentity.bookDoctrine().bookTemplateId()),
        BookDoctrineDisplay.accountingBasis(bookIdentity.bookDoctrine().accountingBasis()),
        bookIdentity.functionalCurrency().code(),
        bookIdentity.fiscalYearStart().wireValue(),
        bookIdentity.bookStartEffectiveDate().toString(),
        postingCoverage == null ? null : ReportModelDisplay.displayPostingCoverage(postingCoverage),
        periodStart == null ? null : periodStart.toString(),
        periodEnd == null ? null : periodEnd.toString(),
        asOf == null ? null : asOf.toString(),
        comparativePeriod.effectiveDateFrom().map(LocalDate::toString).orElse(null),
        comparativePeriod.effectiveDateTo().map(LocalDate::toString).orElse(null),
        null,
        null,
        null,
        null,
        supplementalRows);
  }

  static ReportContext taxContext(
      BookIdentity bookIdentity,
      String registrationId,
      String registrationName,
      String jurisdiction,
      LocalDate periodStart,
      LocalDate periodEnd,
      LocalDate dueDate) {
    return new ReportContext(
        bookIdentity.entityName().value(),
        BookDoctrineDisplay.bookTemplate(bookIdentity.bookDoctrine().bookTemplateId()),
        BookDoctrineDisplay.accountingBasis(bookIdentity.bookDoctrine().accountingBasis()),
        bookIdentity.functionalCurrency().code(),
        bookIdentity.fiscalYearStart().wireValue(),
        bookIdentity.bookStartEffectiveDate().toString(),
        null,
        periodStart.toString(),
        periodEnd.toString(),
        null,
        null,
        null,
        registrationId,
        registrationName,
        jurisdiction,
        dueDate.toString(),
        List.of());
  }

  static List<ReportColumn> balanceColumns() {
    return List.of(
        leftColumn("currency", "Currency"),
        rightColumn("debitTotal", "Debit total"),
        rightColumn("creditTotal", "Credit total"),
        rightColumn("netAmount", "Net amount"),
        leftColumn("balanceSide", "Balance side"));
  }

  static List<ReportRow> balanceRows(List<CurrencyBalance> balances) {
    return balances.stream()
        .map(
            balance ->
                row(
                    balance.netAmount().currencyUnit().code(),
                    balance.netAmount().currencyUnit().code(),
                    ReportModelDisplay.displayMoney(balance.debitTotal()),
                    ReportModelDisplay.displayMoney(balance.creditTotal()),
                    ReportModelDisplay.displayMoney(balance.netAmount()),
                    ReportModelDisplay.displayBalanceSide(balance.balanceSide())))
        .toList();
  }

  static void requireCellWidth(List<ReportColumn> columns, List<ReportRow> rows, String fieldName) {
    if (columns.isEmpty()) {
      return;
    }
    for (int index = 0; index < rows.size(); index++) {
      if (rows.get(index).cells().size() != columns.size()) {
        throw new IllegalArgumentException(
            fieldName
                + "["
                + index
                + "] must have "
                + columns.size()
                + " cells but had "
                + rows.get(index).cells().size()
                + ".");
      }
    }
  }
}

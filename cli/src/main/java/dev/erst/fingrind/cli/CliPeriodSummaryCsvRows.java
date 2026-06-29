package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.List;

/** Builds CSV row families for the period-summary report surface. */
final class CliPeriodSummaryCsvRows {
  private static final String EXPORT_FAMILY = CliCsvExportFamilies.PERIOD_SUMMARY;
  private static final String OPERATION_ID = OperationId.PERIOD_SUMMARY.wireName();
  private static final String RECORD_KIND = CliCsvExportFamilies.PERIOD_SUMMARY;

  private CliPeriodSummaryCsvRows() {}

  static List<List<String>> rows(PeriodSummaryReport report) {
    List<List<String>> rows = new ArrayList<>();
    rows.addAll(summaryRows(report));
    rows.addAll(currencyRows(report));
    rows.addAll(accountRows(report));
    return List.copyOf(rows);
  }

  private static List<List<String>> summaryRows(PeriodSummaryReport report) {
    return List.of(
        metricRow(
            rowId("posting-count"),
            "",
            "summary-metric",
            RECORD_KIND,
            new MetricSubject("book", "", ""),
            new MetricValue("postingCount", Integer.toString(report.postingCount()), "", "count"),
            ""),
        metricRow(
            rowId("posting-line-count"),
            "",
            "summary-metric",
            RECORD_KIND,
            new MetricSubject("book", "", ""),
            new MetricValue(
                "postingLineCount", Integer.toString(report.postingLineCount()), "", "count"),
            ""),
        metricRow(
            rowId("accounts-touched"),
            "",
            "summary-metric",
            RECORD_KIND,
            new MetricSubject("book", "", ""),
            new MetricValue(
                "accountsTouched", Integer.toString(report.accountsTouched()), "", "count"),
            ""));
  }

  private static List<List<String>> currencyRows(PeriodSummaryReport report) {
    if (report.currencyTotals().isEmpty()) {
      return List.of(
          sectionEmptyRow(
              rowId("currency-empty"),
              "currency",
              CliQueryScopeText.noMatchesLabel("currency totals")));
    }
    List<List<String>> rows = new ArrayList<>();
    report.currencyTotals().forEach(summary -> rows.addAll(currencyMetricRows(summary)));
    return List.copyOf(rows);
  }

  private static List<List<String>> accountRows(PeriodSummaryReport report) {
    if (report.accountActivity().isEmpty()) {
      return List.of(
          sectionEmptyRow(
              rowId("account-empty"),
              "account",
              CliQueryScopeText.noMatchesLabel("account activity")));
    }
    List<List<String>> rows = new ArrayList<>();
    report.accountActivity().forEach(activity -> rows.addAll(accountMetricRows(activity)));
    return List.copyOf(rows);
  }

  private static List<List<String>> currencyMetricRows(PeriodCurrencySummary summary) {
    String currencyCode = summary.totals().netAmount().currencyUnit().code();
    String parentRowId = rowId("currency:" + currencyCode);
    MetricSubject subject = new MetricSubject("currency", currencyCode, currencyCode);
    return List.of(
        metricRow(
            parentRowId + ":debit",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue(
                "debitTotal",
                CliQueryScopeText.displayMoney(summary.totals().debitTotal()),
                currencyCode,
                "money"),
            ""),
        metricRow(
            parentRowId + ":credit",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue(
                "creditTotal",
                CliQueryScopeText.displayMoney(summary.totals().creditTotal()),
                currencyCode,
                "money"),
            ""),
        metricRow(
            parentRowId + ":net",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue(
                "netAmount",
                CliQueryScopeText.displayMoney(summary.totals().netAmount()),
                currencyCode,
                "money"),
            ""),
        metricRow(
            parentRowId + ":side",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue("balanceSide", summary.totals().balanceSide().wireValue(), "", "enum"),
            ""));
  }

  private static List<List<String>> accountMetricRows(PeriodAccountActivityRow row) {
    String parentRowId = rowId("account:" + row.account().accountCode().value());
    String currencyCode = row.movement().netAmount().currencyUnit().code();
    MetricSubject subject =
        new MetricSubject(
            "account", row.account().accountCode().value(), row.account().accountName().value());
    return List.of(
        metricRow(
            parentRowId + ":type",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue("accountType", row.account().accountType().wireValue(), "", "enum"),
            ""),
        metricRow(
            parentRowId + ":normal-balance",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue("normalBalance", row.account().normalBalance().wireValue(), "", "enum"),
            ""),
        metricRow(
            parentRowId + ":active",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue("active", Boolean.toString(row.account().active()), "", "flag"),
            ""),
        metricRow(
            parentRowId + ":declared-at",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue("declaredAt", row.account().declaredAt().toString(), "", "timestamp"),
            ""),
        metricRow(
            parentRowId + ":debit",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue(
                "debitTotal",
                CliQueryScopeText.displayMoney(row.movement().debitTotal()),
                currencyCode,
                "money"),
            ""),
        metricRow(
            parentRowId + ":credit",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue(
                "creditTotal",
                CliQueryScopeText.displayMoney(row.movement().creditTotal()),
                currencyCode,
                "money"),
            ""),
        metricRow(
            parentRowId + ":net",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue(
                "netAmount",
                CliQueryScopeText.displayMoney(row.movement().netAmount()),
                currencyCode,
                "money"),
            ""),
        metricRow(
            parentRowId + ":side",
            parentRowId,
            "metric",
            RECORD_KIND,
            subject,
            new MetricValue("balanceSide", row.movement().balanceSide().wireValue(), "", "enum"),
            ""));
  }

  private static List<String> metricRow(
      String rowId,
      String parentRowId,
      String relationKind,
      String recordKind,
      MetricSubject subject,
      MetricValue metric,
      String message) {
    return List.of(
        EXPORT_FAMILY,
        rowId,
        parentRowId,
        relationKind,
        recordKind,
        subject.kind(),
        subject.code(),
        subject.name(),
        metric.name(),
        metric.value(),
        metric.currencyCode(),
        metric.unit(),
        message);
  }

  private static List<String> sectionEmptyRow(String rowId, String subjectKind, String message) {
    return metricRow(
        rowId,
        "",
        "section-empty",
        RECORD_KIND,
        new MetricSubject(subjectKind, "", ""),
        new MetricValue("", "", "", ""),
        message);
  }

  private static String rowId(String suffix) {
    return OPERATION_ID + ":" + suffix;
  }

  private record MetricSubject(String kind, String code, String name) {}

  private record MetricValue(String name, String value, String currencyCode, String unit) {}
}

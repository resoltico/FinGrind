package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountReportJsonModels;
import dev.erst.fingrind.cli.json.CliReportValueJsonModels;
import java.util.ArrayList;
import java.util.List;

/** Renders the typed CSV row tables for account-centric report families. */
final class CliAccountReportCsvRenderer {
  private CliAccountReportCsvRenderer() {}

  static String render(CliAccountReportJsonModels.AccountReportPayload report) {
    return switch (report) {
      case CliAccountReportJsonModels.AccountBalancePayload accountBalance ->
          render(accountBalance);
      case CliAccountReportJsonModels.TrialBalancePayload trialBalance -> render(trialBalance);
      case CliAccountReportJsonModels.AccountLedgerPayload accountLedger -> render(accountLedger);
      case CliAccountReportJsonModels.PeriodSummaryPayload periodSummary -> render(periodSummary);
    };
  }

  static String render(CliAccountReportJsonModels.AccountBalancePayload report) {
    List<List<String>> rows = new ArrayList<>();
    for (CliReportValueJsonModels.BalancePayload balance : report.balances()) {
      rows.add(
          List.of(
              report.family(),
              report.account().accountCode(),
              report.account().accountName(),
              report.account().accountType(),
              report.account().normalBalance(),
              Boolean.toString(report.account().active()),
              balance.currencyCode(),
              balance.debitTotal().currencyCode(),
              balance.debitTotal().minorUnits(),
              balance.creditTotal().currencyCode(),
              balance.creditTotal().minorUnits(),
              balance.netAmount().currencyCode(),
              balance.netAmount().minorUnits(),
              balance.balanceSide()));
    }
    return CliTextFormat.renderCsv(accountBalanceHeaders(), rows);
  }

  static String render(CliAccountReportJsonModels.TrialBalancePayload report) {
    List<List<String>> rows = new ArrayList<>();
    appendAccountBalances(rows, report.family(), "current", report.rows());
    appendAccountBalances(
        rows,
        report.family(),
        "comparative",
        report.comparative() == null ? List.of() : report.comparative().rows());
    return CliTextFormat.renderCsv(
        List.of(
            "family",
            "reportPeriod",
            "accountCode",
            "accountName",
            "accountType",
            "normalBalance",
            "active",
            "currencyCode",
            "debitTotalCurrencyCode",
            "debitTotalMinorUnits",
            "creditTotalCurrencyCode",
            "creditTotalMinorUnits",
            "netAmountCurrencyCode",
            "netAmountMinorUnits",
            "balanceSide"),
        rows);
  }

  static String render(CliAccountReportJsonModels.AccountLedgerPayload report) {
    List<List<String>> rows = new ArrayList<>();
    for (CliAccountReportJsonModels.AccountLedgerRowPayload row : report.rows()) {
      CliReportValueJsonModels.BalancePayload movement = row.movement();
      rows.add(
          List.of(
              report.family(),
              report.account().accountCode(),
              row.postingId(),
              row.effectiveDate(),
              movement.currencyCode(),
              movement.debitTotal().currencyCode(),
              movement.debitTotal().minorUnits(),
              movement.creditTotal().currencyCode(),
              movement.creditTotal().minorUnits(),
              movement.netAmount().currencyCode(),
              movement.netAmount().minorUnits(),
              movement.balanceSide(),
              row.runningNetAmount().currencyCode(),
              row.runningNetAmount().minorUnits(),
              row.runningBalanceSide()));
    }
    return CliTextFormat.renderCsv(
        List.of(
            "family",
            "accountCode",
            "postingId",
            "effectiveDate",
            "movementCurrencyCode",
            "debitTotalCurrencyCode",
            "debitTotalMinorUnits",
            "creditTotalCurrencyCode",
            "creditTotalMinorUnits",
            "netAmountCurrencyCode",
            "netAmountMinorUnits",
            "balanceSide",
            "runningNetAmountCurrencyCode",
            "runningNetAmountMinorUnits",
            "runningBalanceSide"),
        rows);
  }

  static String render(CliAccountReportJsonModels.PeriodSummaryPayload report) {
    List<List<String>> rows = new ArrayList<>();
    appendAccountBalances(rows, report.family(), "activity", report.accountActivity());
    return CliTextFormat.renderCsv(
        List.of(
            "family",
            "recordScope",
            "accountCode",
            "accountName",
            "accountType",
            "normalBalance",
            "active",
            "currencyCode",
            "debitTotalCurrencyCode",
            "debitTotalMinorUnits",
            "creditTotalCurrencyCode",
            "creditTotalMinorUnits",
            "netAmountCurrencyCode",
            "netAmountMinorUnits",
            "balanceSide"),
        rows);
  }

  private static List<String> accountBalanceHeaders() {
    return List.of(
        "family",
        "accountCode",
        "accountName",
        "accountType",
        "normalBalance",
        "active",
        "currencyCode",
        "debitTotalCurrencyCode",
        "debitTotalMinorUnits",
        "creditTotalCurrencyCode",
        "creditTotalMinorUnits",
        "netAmountCurrencyCode",
        "netAmountMinorUnits",
        "balanceSide");
  }

  private static void appendAccountBalances(
      List<List<String>> target,
      String family,
      String reportPeriod,
      List<CliAccountReportJsonModels.AccountBalanceRowPayload> rows) {
    for (CliAccountReportJsonModels.AccountBalanceRowPayload row : rows) {
      target.add(
          List.of(
              family,
              reportPeriod,
              row.accountCode(),
              row.accountName(),
              row.accountType(),
              row.normalBalance(),
              Boolean.toString(row.active()),
              row.currencyCode(),
              row.debitTotal().currencyCode(),
              row.debitTotal().minorUnits(),
              row.creditTotal().currencyCode(),
              row.creditTotal().minorUnits(),
              row.netAmount().currencyCode(),
              row.netAmount().minorUnits(),
              row.balanceSide()));
    }
  }
}

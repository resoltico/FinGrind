package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.cli.json.CliStatementReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementGrossProfitSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.Instant;

/** Projects statement reports into semantic machine payloads. */
final class CliStatementReportPayloadMapper {
  private CliStatementReportPayloadMapper() {}

  static CliStatementReportJsonModels.FinancialPositionPayload financialPosition(
      FinancialPositionReport report, Instant generatedAt) {
    return new CliStatementReportJsonModels.FinancialPositionPayload(
        CliReportPayloadMappingSupport.family(OperationId.FINANCIAL_POSITION),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        new CliReportJsonModels.AsOfResolvedQuery(
            CliReportPayloadMappingSupport.date(report.resolvedEffectiveDateAsOf().orElse(null)),
            report.postingCoverage().name(),
            CliReportPayloadMappingSupport.comparativeRange(
                report.comparativeEffectiveDateRange())),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliReportPayloadMappingSupport.balanceState(report.accountingEquationBalanced()),
        report.sections().stream()
            .map(CliStatementReportPayloadMapper::financialPositionSection)
            .toList(),
        report.comparativeSections().stream()
            .map(CliStatementReportPayloadMapper::financialPositionSection)
            .toList());
  }

  static CliStatementReportJsonModels.IncomeStatementPayload incomeStatement(
      IncomeStatementReport report, Instant generatedAt) {
    return new CliStatementReportJsonModels.IncomeStatementPayload(
        CliReportPayloadMappingSupport.family(OperationId.INCOME_STATEMENT),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        CliReportPayloadMappingSupport.periodQuery(
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            report.postingCoverage(),
            CliReportPayloadMappingSupport.comparativeRange(
                report.comparativeEffectiveDateRange())),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.sections().stream()
            .map(CliStatementReportPayloadMapper::incomeStatementSection)
            .toList(),
        CliReportPayloadMappingSupport.balances(
            IncomeStatementGrossProfitSupport.grossProfitTotals(report)),
        CliReportPayloadMappingSupport.balances(report.netIncomeTotals()),
        report.comparativeSections().stream()
            .map(CliStatementReportPayloadMapper::incomeStatementSection)
            .toList(),
        CliReportPayloadMappingSupport.balances(
            IncomeStatementGrossProfitSupport.comparativeGrossProfitTotals(report)),
        CliReportPayloadMappingSupport.balances(report.comparativeNetIncomeTotals()));
  }

  static CliStatementReportJsonModels.CashFlowStatementPayload cashFlowStatement(
      CashFlowStatementReport report, Instant generatedAt) {
    CliStatementReportJsonModels.@org.jspecify.annotations.Nullable CashFlowComparativePayload
        comparative =
            report.comparativeSections().isEmpty()
                    && report.comparativeOpeningCashTotals().isEmpty()
                    && report.comparativeMovementTotals().isEmpty()
                    && report.comparativeClosingCashTotals().isEmpty()
                ? null
                : new CliStatementReportJsonModels.CashFlowComparativePayload(
                    CliReportPayloadMappingSupport.balances(report.comparativeOpeningCashTotals()),
                    report.comparativeSections().stream()
                        .map(CliStatementReportPayloadMapper::cashFlowSection)
                        .toList(),
                    CliReportPayloadMappingSupport.balances(report.comparativeMovementTotals()),
                    CliReportPayloadMappingSupport.balances(report.comparativeClosingCashTotals()));
    return new CliStatementReportJsonModels.CashFlowStatementPayload(
        CliReportPayloadMappingSupport.family(OperationId.CASH_FLOW_STATEMENT),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        CliReportPayloadMappingSupport.periodQuery(
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            report.postingCoverage(),
            CliReportPayloadMappingSupport.comparativeRange(
                report.comparativeEffectiveDateRange())),
        CliReportPayloadMappingSupport.instant(generatedAt),
        CliReportPayloadMappingSupport.balances(report.openingCashTotals()),
        report.sections().stream().map(CliStatementReportPayloadMapper::cashFlowSection).toList(),
        CliReportPayloadMappingSupport.balances(report.movementTotals()),
        CliReportPayloadMappingSupport.balances(report.closingCashTotals()),
        comparative);
  }

  static CliStatementReportJsonModels.ChangesInEquityPayload changesInEquity(
      ChangesInEquityReport report, Instant generatedAt) {
    CliStatementReportJsonModels.@org.jspecify.annotations.Nullable ChangesInEquityComparativePayload
        comparative =
            report.comparativeRows().isEmpty()
                    && report.comparativeOpeningTotals().isEmpty()
                    && report.comparativeMovementTotals().isEmpty()
                    && report.comparativeClosingTotals().isEmpty()
                ? null
                : new CliStatementReportJsonModels.ChangesInEquityComparativePayload(
                    report.comparativeRows().stream()
                        .map(CliStatementReportPayloadMapper::changesInEquityRow)
                        .toList(),
                    CliReportPayloadMappingSupport.balances(report.comparativeOpeningTotals()),
                    CliReportPayloadMappingSupport.balances(report.comparativeMovementTotals()),
                    CliReportPayloadMappingSupport.balances(report.comparativeClosingTotals()));
    return new CliStatementReportJsonModels.ChangesInEquityPayload(
        CliReportPayloadMappingSupport.family(OperationId.CHANGES_IN_EQUITY),
        CliReportPayloadMappingSupport.bookIdentity(report.bookIdentity()),
        CliReportPayloadMappingSupport.periodQuery(
            report.effectiveDateFrom(),
            report.effectiveDateTo(),
            report.postingCoverage(),
            CliReportPayloadMappingSupport.comparativeRange(
                report.comparativeEffectiveDateRange())),
        CliReportPayloadMappingSupport.instant(generatedAt),
        report.rows().stream().map(CliStatementReportPayloadMapper::changesInEquityRow).toList(),
        CliReportPayloadMappingSupport.balances(report.openingTotals()),
        CliReportPayloadMappingSupport.balances(report.movementTotals()),
        CliReportPayloadMappingSupport.balances(report.closingTotals()),
        comparative);
  }

  private static CliStatementReportJsonModels.StatementSectionPayload financialPositionSection(
      FinancialPositionSection section) {
    return new CliStatementReportJsonModels.StatementSectionPayload(
        section.accountType().name(),
        section.rows().stream().map(CliStatementReportPayloadMapper::financialPositionRow).toList(),
        CliReportPayloadMappingSupport.balances(section.totals()));
  }

  private static CliStatementReportJsonModels.StatementRowPayload financialPositionRow(
      FinancialPositionRow row) {
    return new CliStatementReportJsonModels.StatementRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().name(),
        row.lineClassification().map(Enum::name).orElse(null),
        null,
        row.lineKind().name(),
        CliReportPayloadMappingSupport.balance(row.balance()));
  }

  private static CliStatementReportJsonModels.StatementSectionPayload incomeStatementSection(
      IncomeStatementSection section) {
    return new CliStatementReportJsonModels.StatementSectionPayload(
        section.accountType().name(),
        section.rows().stream().map(CliStatementReportPayloadMapper::incomeStatementRow).toList(),
        CliReportPayloadMappingSupport.balances(section.totals()));
  }

  private static CliStatementReportJsonModels.StatementRowPayload incomeStatementRow(
      IncomeStatementRow row) {
    return new CliStatementReportJsonModels.StatementRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().name(),
        null,
        row.lineClassification().name(),
        row.lineKind().name(),
        CliReportPayloadMappingSupport.balance(row.movement()));
  }

  private static CliStatementReportJsonModels.StatementSectionPayload cashFlowSection(
      CashFlowSection section) {
    return new CliStatementReportJsonModels.StatementSectionPayload(
        section.sectionKind().name(),
        section.rows().stream().map(CliStatementReportPayloadMapper::cashFlowRow).toList(),
        CliReportPayloadMappingSupport.balances(section.totals()));
  }

  private static CliStatementReportJsonModels.StatementRowPayload cashFlowRow(CashFlowRow row) {
    return new CliStatementReportJsonModels.StatementRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().name(),
        row.financialPositionLineClassification().map(Enum::name).orElse(null),
        row.profitAndLossLineClassification().map(Enum::name).orElse(null),
        row.lineKind().name(),
        CliReportPayloadMappingSupport.balance(row.movement()));
  }

  private static CliStatementReportJsonModels.ChangesInEquityRowPayload changesInEquityRow(
      ChangesInEquityRow row) {
    return new CliStatementReportJsonModels.ChangesInEquityRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().map(Enum::name).orElse(null),
        row.lineClassification().map(Enum::name).orElse(null),
        row.lineKind().name(),
        CliReportPayloadMappingSupport.balance(row.openingBalance()),
        CliReportPayloadMappingSupport.balance(row.movement()),
        CliReportPayloadMappingSupport.balance(row.closingBalance()));
  }
}

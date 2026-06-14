package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Statement-oriented JSON records emitted by the CLI transport layer. */
public interface CliStatementJsonModels extends CliReportSupportJsonModels {

  record FinancialPositionPayload(
      @Nullable String effectiveDateAsOf,
      String effectiveDateAsOfMeaning,
      ReportContextPayload context,
      List<FinancialPositionSectionPayload> sections,
      List<FinancialPositionSectionPayload> comparativeSections)
      implements CliSuccessPayload {
    public FinancialPositionPayload {
      effectiveDateAsOf = requireOptionalText(effectiveDateAsOf, "effectiveDateAsOf");
      effectiveDateAsOfMeaning = requireText(effectiveDateAsOfMeaning, "effectiveDateAsOfMeaning");
      Objects.requireNonNull(context, "context");
      sections = copyList(sections, "sections");
      comparativeSections = copyList(comparativeSections, "comparativeSections");
    }
  }

  record FinancialPositionSectionPayload(
      String accountType,
      List<FinancialPositionRowPayload> rows,
      List<BalanceBucketPayload> totals) {
    public FinancialPositionSectionPayload {
      accountType = requireText(accountType, "accountType");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  record FinancialPositionRowPayload(
      String lineCode,
      String lineName,
      String lineType,
      @Nullable String lineRole,
      @Nullable String lineClassification,
      String lineKind,
      BalanceBucketPayload balance) {
    public FinancialPositionRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireText(lineType, "lineType");
      lineRole = requireOptionalText(lineRole, "lineRole");
      lineClassification = requireOptionalText(lineClassification, "lineClassification");
      lineKind = requireText(lineKind, "lineKind");
      Objects.requireNonNull(balance, "balance");
    }
  }

  record IncomeStatementPayload(
      String effectiveDateFrom,
      String effectiveDateTo,
      ReportContextPayload context,
      List<IncomeStatementSectionPayload> sections,
      List<BalanceBucketPayload> netIncomeTotals,
      List<IncomeStatementSectionPayload> comparativeSections,
      List<BalanceBucketPayload> comparativeNetIncomeTotals)
      implements CliSuccessPayload {
    public IncomeStatementPayload {
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      Objects.requireNonNull(context, "context");
      sections = copyList(sections, "sections");
      netIncomeTotals = copyList(netIncomeTotals, "netIncomeTotals");
      comparativeSections = copyList(comparativeSections, "comparativeSections");
      comparativeNetIncomeTotals =
          copyList(comparativeNetIncomeTotals, "comparativeNetIncomeTotals");
    }
  }

  record IncomeStatementSectionPayload(
      String accountType, List<IncomeStatementRowPayload> rows, List<BalanceBucketPayload> totals) {
    public IncomeStatementSectionPayload {
      accountType = requireText(accountType, "accountType");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  record IncomeStatementRowPayload(
      String lineCode,
      String lineName,
      String lineType,
      @Nullable String lineRole,
      String lineClassification,
      String lineKind,
      BalanceBucketPayload movement) {
    public IncomeStatementRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireText(lineType, "lineType");
      lineRole = requireOptionalText(lineRole, "lineRole");
      lineClassification = requireText(lineClassification, "lineClassification");
      lineKind = requireText(lineKind, "lineKind");
      Objects.requireNonNull(movement, "movement");
    }
  }

  record ChangesInEquityPayload(
      String effectiveDateFrom,
      String effectiveDateTo,
      ReportContextPayload context,
      List<ChangesInEquityRowPayload> rows,
      List<BalanceBucketPayload> openingTotals,
      List<BalanceBucketPayload> movementTotals,
      List<BalanceBucketPayload> closingTotals,
      List<ChangesInEquityRowPayload> comparativeRows,
      List<BalanceBucketPayload> comparativeOpeningTotals,
      List<BalanceBucketPayload> comparativeMovementTotals,
      List<BalanceBucketPayload> comparativeClosingTotals)
      implements CliSuccessPayload {
    public ChangesInEquityPayload {
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      Objects.requireNonNull(context, "context");
      rows = copyList(rows, "rows");
      openingTotals = copyList(openingTotals, "openingTotals");
      movementTotals = copyList(movementTotals, "movementTotals");
      closingTotals = copyList(closingTotals, "closingTotals");
      comparativeRows = copyList(comparativeRows, "comparativeRows");
      comparativeOpeningTotals = copyList(comparativeOpeningTotals, "comparativeOpeningTotals");
      comparativeMovementTotals = copyList(comparativeMovementTotals, "comparativeMovementTotals");
      comparativeClosingTotals = copyList(comparativeClosingTotals, "comparativeClosingTotals");
    }
  }

  record ChangesInEquityRowPayload(
      String lineCode,
      String lineName,
      @Nullable String lineType,
      @Nullable String lineRole,
      @Nullable String lineClassification,
      String lineKind,
      BalanceBucketPayload openingBalance,
      BalanceBucketPayload movement,
      BalanceBucketPayload closingBalance) {
    public ChangesInEquityRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireOptionalText(lineType, "lineType");
      lineRole = requireOptionalText(lineRole, "lineRole");
      lineClassification = requireOptionalText(lineClassification, "lineClassification");
      lineKind = requireText(lineKind, "lineKind");
      Objects.requireNonNull(openingBalance, "openingBalance");
      Objects.requireNonNull(movement, "movement");
      Objects.requireNonNull(closingBalance, "closingBalance");
    }
  }
}

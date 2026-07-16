package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Semantic machine payloads for financial statements and changes in equity. */
public interface CliStatementReportJsonModels {
  /** Sealed family for financial statements with a shared CSV projection owner. */
  sealed interface StatementReportPayload extends CliReportJsonModels.ReportPayload
      permits FinancialPositionPayload,
          IncomeStatementPayload,
          CashFlowStatementPayload,
          ChangesInEquityPayload {}

  record FinancialPositionPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.AsOfResolvedQuery resolvedQuery,
      String generatedAt,
      String accountingEquationState,
      List<StatementSectionPayload> sections,
      List<StatementSectionPayload> comparativeSections)
      implements StatementReportPayload {
    public FinancialPositionPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      accountingEquationState = requireText(accountingEquationState, "accountingEquationState");
      sections = copyList(sections, "sections");
      comparativeSections = copyList(comparativeSections, "comparativeSections");
    }
  }

  record IncomeStatementPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.PeriodResolvedQuery resolvedQuery,
      String generatedAt,
      List<StatementSectionPayload> sections,
      List<CliReportValueJsonModels.BalancePayload> grossProfitTotals,
      List<CliReportValueJsonModels.BalancePayload> netIncomeTotals,
      List<StatementSectionPayload> comparativeSections,
      List<CliReportValueJsonModels.BalancePayload> comparativeGrossProfitTotals,
      List<CliReportValueJsonModels.BalancePayload> comparativeNetIncomeTotals)
      implements StatementReportPayload {
    public IncomeStatementPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      sections = copyList(sections, "sections");
      grossProfitTotals = copyList(grossProfitTotals, "grossProfitTotals");
      netIncomeTotals = copyList(netIncomeTotals, "netIncomeTotals");
      comparativeSections = copyList(comparativeSections, "comparativeSections");
      comparativeGrossProfitTotals =
          copyList(comparativeGrossProfitTotals, "comparativeGrossProfitTotals");
      comparativeNetIncomeTotals =
          copyList(comparativeNetIncomeTotals, "comparativeNetIncomeTotals");
    }
  }

  record StatementSectionPayload(
      String sectionKind,
      List<StatementRowPayload> rows,
      List<CliReportValueJsonModels.BalancePayload> totals) {
    public StatementSectionPayload {
      sectionKind = requireText(sectionKind, "sectionKind");
      rows = copyList(rows, "rows");
      totals = copyList(totals, "totals");
    }
  }

  record StatementRowPayload(
      String lineCode,
      String lineName,
      @Nullable String lineType,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification,
      String lineKind,
      CliReportValueJsonModels.BalancePayload balance) {
    public StatementRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireOptionalText(lineType, "lineType");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      profitAndLossLineClassification =
          requireOptionalText(profitAndLossLineClassification, "profitAndLossLineClassification");
      lineKind = requireText(lineKind, "lineKind");
      Objects.requireNonNull(balance, "balance");
    }
  }

  record CashFlowStatementPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.PeriodResolvedQuery resolvedQuery,
      String generatedAt,
      List<CliReportValueJsonModels.BalancePayload> openingCashTotals,
      List<StatementSectionPayload> sections,
      List<CliReportValueJsonModels.BalancePayload> movementTotals,
      List<CliReportValueJsonModels.BalancePayload> closingCashTotals,
      @Nullable CashFlowComparativePayload comparative)
      implements StatementReportPayload {
    public CashFlowStatementPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      openingCashTotals = copyList(openingCashTotals, "openingCashTotals");
      sections = copyList(sections, "sections");
      movementTotals = copyList(movementTotals, "movementTotals");
      closingCashTotals = copyList(closingCashTotals, "closingCashTotals");
    }
  }

  record CashFlowComparativePayload(
      List<CliReportValueJsonModels.BalancePayload> openingCashTotals,
      List<StatementSectionPayload> sections,
      List<CliReportValueJsonModels.BalancePayload> movementTotals,
      List<CliReportValueJsonModels.BalancePayload> closingCashTotals) {
    public CashFlowComparativePayload {
      openingCashTotals = copyList(openingCashTotals, "openingCashTotals");
      sections = copyList(sections, "sections");
      movementTotals = copyList(movementTotals, "movementTotals");
      closingCashTotals = copyList(closingCashTotals, "closingCashTotals");
    }
  }

  record ChangesInEquityPayload(
      String family,
      CliAdministrationJsonModels.BookIdentityPayload bookIdentity,
      CliReportJsonModels.PeriodResolvedQuery resolvedQuery,
      String generatedAt,
      List<ChangesInEquityRowPayload> rows,
      List<CliReportValueJsonModels.BalancePayload> openingTotals,
      List<CliReportValueJsonModels.BalancePayload> movementTotals,
      List<CliReportValueJsonModels.BalancePayload> closingTotals,
      @Nullable ChangesInEquityComparativePayload comparative)
      implements StatementReportPayload {
    public ChangesInEquityPayload {
      family = requireText(family, "family");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(resolvedQuery, "resolvedQuery");
      generatedAt = requireText(generatedAt, "generatedAt");
      rows = copyList(rows, "rows");
      openingTotals = copyList(openingTotals, "openingTotals");
      movementTotals = copyList(movementTotals, "movementTotals");
      closingTotals = copyList(closingTotals, "closingTotals");
    }
  }

  record ChangesInEquityRowPayload(
      String lineCode,
      String lineName,
      @Nullable String lineType,
      @Nullable String financialPositionLineClassification,
      String lineKind,
      CliReportValueJsonModels.BalancePayload openingBalance,
      CliReportValueJsonModels.BalancePayload movement,
      CliReportValueJsonModels.BalancePayload closingBalance) {
    public ChangesInEquityRowPayload {
      lineCode = requireText(lineCode, "lineCode");
      lineName = requireText(lineName, "lineName");
      lineType = requireOptionalText(lineType, "lineType");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      lineKind = requireText(lineKind, "lineKind");
      Objects.requireNonNull(openingBalance, "openingBalance");
      Objects.requireNonNull(movement, "movement");
      Objects.requireNonNull(closingBalance, "closingBalance");
    }
  }

  record ChangesInEquityComparativePayload(
      List<ChangesInEquityRowPayload> rows,
      List<CliReportValueJsonModels.BalancePayload> openingTotals,
      List<CliReportValueJsonModels.BalancePayload> movementTotals,
      List<CliReportValueJsonModels.BalancePayload> closingTotals) {
    public ChangesInEquityComparativePayload {
      rows = copyList(rows, "rows");
      openingTotals = copyList(openingTotals, "openingTotals");
      movementTotals = copyList(movementTotals, "movementTotals");
      closingTotals = copyList(closingTotals, "closingTotals");
    }
  }
}

package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliFixedAssetPostingJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Renders caller-authored posting-entry payload facts for text-mode detail views. */
final class CliPostingEntryTextRenderer {
  private CliPostingEntryTextRenderer() {}

  static String renderEntryFacts(CliPostingEntryPayload entry) {
    List<List<String>> summaryRows = summaryRows(entry);
    List<String> sections = new ArrayList<>();
    sections.add(CliTextFormat.renderKeyValueBlock(List.copyOf(summaryRows)));
    if (entry.openingBalances() != null && !entry.openingBalances().isEmpty()) {
      sections.add(renderOpeningBalances(entry));
    }
    return CliReportRenderSupport.joinSections(sections.toArray(String[]::new));
  }

  private static List<List<String>> summaryRows(CliPostingEntryPayload entry) {
    List<List<String>> summaryRows = new ArrayList<>();
    summaryRows.add(List.of("Entry kind", CliTextDisplay.wireLabel(entry.entryKind())));
    appendOptionalSummaryRow(summaryRows, "Cash account", entry.cashAccountCode());
    appendOptionalSummaryRow(summaryRows, "Receivable account", entry.receivableAccountCode());
    appendOptionalSummaryRow(summaryRows, "Payable account", entry.payableAccountCode());
    appendOptionalSummaryRow(summaryRows, "Revenue account", entry.revenueAccountCode());
    appendOptionalSummaryRow(summaryRows, "Inventory account", entry.inventoryAccountCode());
    appendOptionalSummaryRow(summaryRows, "Expense account", entry.expenseAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Write-down loss account", entry.writeDownLossAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Shrinkage loss account", entry.shrinkageLossAccountCode());
    appendOptionalSummaryRow(summaryRows, "Count gain account", entry.countGainAccountCode());
    appendOptionalSummaryRow(summaryRows, "Equity account", entry.equityAccountCode());
    appendAmountRow(summaryRows, entry);
    appendQuantityRows(summaryRows, entry);
    appendInventoryReliefRows(summaryRows, entry);
    appendResolvedInventoryCostingRows(summaryRows, entry);
    appendAccrualCutoffRows(summaryRows, entry);
    appendFixedAssetRows(summaryRows, entry);
    CliLatvianPayrollPostingEntryTextRenderer.appendRows(summaryRows, entry);
    appendSettlementAdjunctRows(summaryRows, entry);
    appendForeignExchangeRows(summaryRows, entry);
    appendTaxSelectionRows(summaryRows, entry);
    appendAppliedTaxRows(summaryRows, entry);
    appendReversalRows(summaryRows, entry);
    return summaryRows;
  }

  private static String renderOpeningBalances(CliPostingEntryPayload entry) {
    List<dev.erst.fingrind.cli.json.CliOpeningBalancePayload> openingBalances =
        Objects.requireNonNull(entry.openingBalances());
    return CliReportRenderSupport.section(
        "Opening balances",
        CliTextFormat.renderAdaptiveTable(
            CliReportRenderSupport.TEXT_TABLE_WIDTH,
            List.of("Account", "Side", "Quantity", "Currency", "Amount"),
            openingBalances.stream()
                .map(
                    balance ->
                        List.of(
                            balance.accountCode(),
                            CliTextDisplay.wireLabel(balance.side()),
                            balance.quantity() == null ? "" : balance.quantity(),
                            balance.amount().currencyCode(),
                            balance.amount().canonicalDecimal()))
                .toList(),
            4));
  }

  private static void appendOptionalSummaryRow(
      List<List<String>> summaryRows, String label, @Nullable String value) {
    if (value != null) {
      summaryRows.add(List.of(label, value));
    }
  }

  private static void appendAmountRow(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.amount() != null) {
      summaryRows.add(List.of("Amount", CliTextFormat.displayMoney(entry.amount().toMoney())));
    }
  }

  private static void appendSettlementAdjunctRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.settlementAdjunct() == null) {
      return;
    }
    summaryRows.add(List.of("Settlement adjunct account", entry.settlementAdjunct().accountCode()));
    summaryRows.add(
        List.of(
            "Settlement adjunct amount",
            CliTextFormat.displayMoney(entry.settlementAdjunct().amount().toMoney())));
  }

  private static void appendQuantityRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.quantity() != null) {
      summaryRows.add(List.of("Quantity", entry.quantity()));
    }
    if (entry.unitCost() != null) {
      summaryRows.add(List.of("Unit cost", CliTextFormat.displayMoney(entry.unitCost().toMoney())));
    }
  }

  private static void appendInventoryReliefRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.inventoryRelief() == null) {
      return;
    }
    summaryRows.add(List.of("Inventory account", entry.inventoryRelief().inventoryAccountCode()));
    summaryRows.add(
        List.of("Cost of sales account", entry.inventoryRelief().costOfSalesAccountCode()));
    summaryRows.add(List.of("Inventory relief quantity", entry.inventoryRelief().quantity()));
  }

  private static void appendResolvedInventoryCostingRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.resolvedInventoryCosting() == null) {
      return;
    }
    summaryRows.add(
        List.of(
            "Derived cost of sales",
            CliTextFormat.displayMoney(entry.resolvedInventoryCosting().costOfSales().toMoney())));
    summaryRows.add(
        List.of("Derived quantity relieved", entry.resolvedInventoryCosting().quantityRelieved()));
    summaryRows.add(
        List.of(
            "Moving-average unit cost (informational)",
            CliTextFormat.displayMoney(
                entry
                    .resolvedInventoryCosting()
                    .roundedMovingAverageUnitCostProjection()
                    .toMoney())));
  }

  private static void appendAccrualCutoffRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.accrualCutoff() == null) {
      return;
    }
    CliPostingEntryPayload.AccrualCutoffPayload accrualCutoff = entry.accrualCutoff();
    summaryRows.add(List.of("Accrual cut-off id", accrualCutoff.accrualCutoffId()));
    appendOptionalSummaryRow(summaryRows, "Accrual cut-off kind", accrualCutoff.aggregateKind());
    appendOptionalSummaryRow(
        summaryRows, "Prepaid expense account", accrualCutoff.prepaymentAssetAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Deferred revenue account", accrualCutoff.deferredRevenueAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Accrued expense account", accrualCutoff.accruedExpenseLiabilityAccountCode());
    if (accrualCutoff.recognitionInterval() != null) {
      summaryRows.add(
          List.of(
              "Recognition interval",
              accrualCutoff.recognitionInterval().startDate()
                  + " through "
                  + accrualCutoff.recognitionInterval().endDate()));
    }
    if (accrualCutoff.resolvedApplication() != null) {
      summaryRows.add(
          List.of(
              "Resolved accrual application",
              CliTextDisplay.wireLabel(accrualCutoff.resolvedApplication().applicationKind())));
      summaryRows.add(
          List.of(
              "Resolved debit account", accrualCutoff.resolvedApplication().debitAccountCode()));
      summaryRows.add(
          List.of(
              "Resolved credit account", accrualCutoff.resolvedApplication().creditAccountCode()));
    }
  }

  private static void appendFixedAssetRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.fixedAsset() == null) {
      return;
    }
    CliFixedAssetPostingJsonModels.FixedAssetPayload fixedAsset = entry.fixedAsset();
    summaryRows.add(List.of("Fixed asset id", fixedAsset.fixedAssetId()));
    summaryRows.add(
        List.of(
            "Fixed-asset lifecycle event", CliTextDisplay.wireLabel(fixedAsset.lifecycleKind())));
    appendOptionalSummaryRow(summaryRows, "Fixed-asset account", fixedAsset.assetAccountCode());
    appendOptionalSummaryRow(
        summaryRows,
        "Accumulated depreciation account",
        fixedAsset.accumulatedDepreciationAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Depreciation expense account", fixedAsset.depreciationExpenseAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Disposal gain account", fixedAsset.disposalGainAccountCode());
    appendOptionalSummaryRow(
        summaryRows, "Disposal loss account", fixedAsset.disposalLossAccountCode());
    if (fixedAsset.cost() != null) {
      summaryRows.add(
          List.of("Capitalized cost", CliTextFormat.displayMoney(fixedAsset.cost().toMoney())));
    }
    if (fixedAsset.depreciationSchedule() != null) {
      summaryRows.add(
          List.of(
              "Depreciation schedule",
              fixedAsset.depreciationSchedule().inServiceDate()
                  + ", "
                  + fixedAsset.depreciationSchedule().usefulLifeMonths()
                  + " months, residual "
                  + CliTextFormat.displayMoney(
                      fixedAsset.depreciationSchedule().residualValue().toMoney())));
    }
    if (fixedAsset.resolvedDepreciation() != null) {
      summaryRows.add(
          List.of(
              "Derived depreciation",
              CliTextFormat.displayMoney(fixedAsset.resolvedDepreciation().amount().toMoney())));
      summaryRows.add(
          List.of(
              "Resolved depreciation debit account",
              fixedAsset.resolvedDepreciation().depreciationExpenseAccountCode()));
      summaryRows.add(
          List.of(
              "Resolved depreciation credit account",
              fixedAsset.resolvedDepreciation().accumulatedDepreciationAccountCode()));
    }
    if (fixedAsset.resolvedDisposal() != null) {
      summaryRows.add(
          List.of(
              "Resolved disposal carrying amount",
              CliTextFormat.displayMoney(
                  fixedAsset.resolvedDisposal().carryingAmount().toMoney())));
      summaryRows.add(
          List.of(
              fixedAsset.resolvedDisposal().gain()
                  ? "Derived disposal gain"
                  : "Derived disposal loss",
              CliTextFormat.displayMoney(
                  fixedAsset.resolvedDisposal().gainOrLossAmount().toMoney())));
      summaryRows.add(
          List.of(
              "Resolved gain-or-loss account",
              fixedAsset.resolvedDisposal().gainOrLossAccountCode()));
    }
  }

  private static void appendTaxSelectionRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.taxSelection() == null) {
      return;
    }
    summaryRows.add(List.of("Tax registration id", entry.taxSelection().taxRegistrationId()));
    summaryRows.add(List.of("Tax code", entry.taxSelection().taxCode()));
  }

  private static void appendAppliedTaxRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.appliedTax() == null) {
      return;
    }
    summaryRows.add(List.of("Resolved tax code name", entry.appliedTax().taxCodeName()));
    summaryRows.add(
        List.of("Resolved tax rate", entry.appliedTax().ratePartsPerMillion() + " ppm"));
    summaryRows.add(
        List.of(
            "Tax inclusion mode", CliTextDisplay.wireLabel(entry.appliedTax().inclusionMode())));
    summaryRows.add(
        List.of(
            "Tax application kind",
            CliTextDisplay.wireLabel(entry.appliedTax().applicationKind())));
    summaryRows.add(
        List.of(
            "Taxable amount",
            monetarySummary(
                entry.appliedTax().taxableAmount().currencyCode(),
                entry.appliedTax().taxableAmount().canonicalDecimal())));
    summaryRows.add(
        List.of(
            "Tax amount",
            monetarySummary(
                entry.appliedTax().taxAmount().currencyCode(),
                entry.appliedTax().taxAmount().canonicalDecimal())));
    summaryRows.add(
        List.of(
            "Gross amount",
            monetarySummary(
                entry.appliedTax().grossAmount().currencyCode(),
                entry.appliedTax().grossAmount().canonicalDecimal())));
    summaryRows.add(
        List.of(
            "Tax account",
            entry.appliedTax().taxAccountCode() == null
                ? "(none)"
                : entry.appliedTax().taxAccountCode()));
  }

  private static void appendForeignExchangeRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.foreignExchange() == null) {
      return;
    }
    summaryRows.add(
        List.of(
            "Transaction amount",
            CliTextFormat.displayMoney(entry.foreignExchange().transactionAmount().toMoney())));
    summaryRows.add(
        List.of(
            "Functional amount",
            CliTextFormat.displayMoney(entry.foreignExchange().functionalAmount().toMoney())));
    summaryRows.add(
        List.of("FX treatment", CliTextDisplay.wireLabel(entry.foreignExchange().treatmentKind())));
    summaryRows.add(
        List.of(
            "Quoted rate",
            monetarySummary(
                    entry.foreignExchange().quotedRate().transactionCurrencyAmount().currencyCode(),
                    entry
                        .foreignExchange()
                        .quotedRate()
                        .transactionCurrencyAmount()
                        .canonicalDecimal())
                + " per "
                + monetarySummary(
                    entry.foreignExchange().quotedRate().functionalCurrencyAmount().currencyCode(),
                    entry
                        .foreignExchange()
                        .quotedRate()
                        .functionalCurrencyAmount()
                        .canonicalDecimal())));
    summaryRows.add(List.of("Quote observed on", entry.foreignExchange().quotedRate().quotedOn()));
    summaryRows.add(List.of("Quote source", entry.foreignExchange().quotedRate().quoteSource()));
  }

  private static void appendReversalRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.reversal() == null) {
      return;
    }
    summaryRows.add(List.of("Prior posting id", entry.reversal().priorPostingId()));
    summaryRows.add(List.of("Reason", entry.reversal().reason()));
  }

  private static String monetarySummary(String currencyCode, String canonicalDecimal) {
    return currencyCode + " " + canonicalDecimal;
  }
}

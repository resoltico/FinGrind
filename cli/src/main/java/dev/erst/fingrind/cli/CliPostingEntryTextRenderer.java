package dev.erst.fingrind.cli;

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
    appendOptionalSummaryRow(summaryRows, "Equity account", entry.equityAccountCode());
    appendAmountRow(summaryRows, entry);
    appendInventoryReliefRows(summaryRows, entry);
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
            List.of("Account", "Side", "Currency", "Amount"),
            openingBalances.stream()
                .map(
                    balance ->
                        List.of(
                            balance.accountCode(),
                            CliTextDisplay.wireLabel(balance.side()),
                            balance.amount().currencyCode(),
                            balance.amount().canonicalDecimal()))
                .toList(),
            3));
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

  private static void appendInventoryReliefRows(
      List<List<String>> summaryRows, CliPostingEntryPayload entry) {
    if (entry.inventoryRelief() == null) {
      return;
    }
    summaryRows.add(List.of("Inventory account", entry.inventoryRelief().inventoryAccountCode()));
    summaryRows.add(
        List.of("Cost of sales account", entry.inventoryRelief().costOfSalesAccountCode()));
    summaryRows.add(
        List.of(
            "Inventory relief amount",
            CliTextFormat.displayMoney(entry.inventoryRelief().amount().toMoney())));
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

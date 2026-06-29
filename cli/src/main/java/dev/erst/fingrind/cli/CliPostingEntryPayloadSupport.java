package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliOpeningBalancePayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps and renders caller-authored posting entry facts for detailed CLI payloads. */
final class CliPostingEntryPayloadSupport {
  private CliPostingEntryPayloadSupport() {}

  static @Nullable CliPostingEntryPayload entryPayload(@Nullable BookkeepingEntry entry) {
    if (entry == null) {
      return null;
    }
    return switch (entry) {
      case BookkeepingEntry.DirectJournal _ ->
          new CliPostingEntryPayload(
              entry.entryKind().wireValue(),
              null,
              null,
              null,
              null,
              null,
              foreignExchangePayload(entry.foreignExchangeDetails()),
              null,
              null,
              null,
              null);
      case BookkeepingEntry.Sale sale ->
          new CliPostingEntryPayload(
              sale.entryKind().wireValue(),
              sale.cashAccountCode().value(),
              sale.revenueAccountCode().value(),
              null,
              null,
              sale.amount(),
              foreignExchangePayload(sale.foreignExchangeDetails()),
              sale.taxSelection() == null
                  ? null
                  : CliTaxPayloadMapper.taxSelectionPayload(sale.taxSelection()),
              sale.appliedTax() == null
                  ? null
                  : CliTaxPayloadMapper.appliedTaxPayload(sale.appliedTax()),
              null,
              null);
      case BookkeepingEntry.Expense expense ->
          new CliPostingEntryPayload(
              expense.entryKind().wireValue(),
              expense.cashAccountCode().value(),
              null,
              expense.expenseAccountCode().value(),
              null,
              expense.amount(),
              foreignExchangePayload(expense.foreignExchangeDetails()),
              expense.taxSelection() == null
                  ? null
                  : CliTaxPayloadMapper.taxSelectionPayload(expense.taxSelection()),
              expense.appliedTax() == null
                  ? null
                  : CliTaxPayloadMapper.appliedTaxPayload(expense.appliedTax()),
              null,
              null);
      case BookkeepingEntry.OwnerContribution contribution ->
          new CliPostingEntryPayload(
              contribution.entryKind().wireValue(),
              contribution.cashAccountCode().value(),
              null,
              null,
              contribution.equityAccountCode().value(),
              contribution.amount(),
              foreignExchangePayload(contribution.foreignExchangeDetails()),
              null,
              null,
              null,
              null);
      case BookkeepingEntry.OwnerWithdrawal withdrawal ->
          new CliPostingEntryPayload(
              withdrawal.entryKind().wireValue(),
              withdrawal.cashAccountCode().value(),
              null,
              null,
              withdrawal.equityAccountCode().value(),
              withdrawal.amount(),
              foreignExchangePayload(withdrawal.foreignExchangeDetails()),
              null,
              null,
              null,
              null);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          new CliPostingEntryPayload(
              openingPosition.entryKind().wireValue(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              openingPosition.balances().stream()
                  .map(CliPostingEntryPayloadSupport::openingBalancePayload)
                  .toList());
      case BookkeepingEntry.Reversal reversal ->
          new CliPostingEntryPayload(
              reversal.entryKind().wireValue(),
              null,
              null,
              null,
              null,
              null,
              foreignExchangePayload(reversal.foreignExchangeDetails()),
              null,
              null,
              new CliBookQueryJsonModels.ReversalPayload(
                  reversal.reversal().reference().priorPostingId().value(),
                  reversal.reversal().reason().value()),
              null);
    };
  }

  static String renderEntryFacts(CliPostingEntryPayload entry) {
    List<List<String>> summaryRows = new ArrayList<>();
    summaryRows.add(List.of("Entry kind", CliTextDisplay.wireLabel(entry.entryKind())));
    appendOptionalSummaryRow(summaryRows, "Cash account", entry.cashAccountCode());
    appendOptionalSummaryRow(summaryRows, "Revenue account", entry.revenueAccountCode());
    appendOptionalSummaryRow(summaryRows, "Expense account", entry.expenseAccountCode());
    appendOptionalSummaryRow(summaryRows, "Equity account", entry.equityAccountCode());
    appendAmountRow(summaryRows, entry);
    appendForeignExchangeRows(summaryRows, entry);
    appendTaxSelectionRows(summaryRows, entry);
    appendAppliedTaxRows(summaryRows, entry);
    appendReversalRows(summaryRows, entry);
    List<String> sections = new ArrayList<>();
    sections.add(CliTextFormat.renderKeyValueBlock(List.copyOf(summaryRows)));
    if (entry.openingBalances() != null && !entry.openingBalances().isEmpty()) {
      sections.add(
          CliReportRenderSupport.section(
              "Opening balances",
              CliTextFormat.renderAdaptiveTable(
                  CliReportRenderSupport.TEXT_TABLE_WIDTH,
                  List.of("Account", "Side", "Currency", "Amount"),
                  entry.openingBalances().stream()
                      .map(
                          balance ->
                              List.of(
                                  balance.accountCode(),
                                  CliTextDisplay.wireLabel(balance.side()),
                                  balance.amount().currencyCode(),
                                  balance.amount().canonicalDecimal()))
                      .toList(),
                  3)));
    }
    return CliReportRenderSupport.joinSections(sections.toArray(String[]::new));
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

  private static CliOpeningBalancePayload openingBalancePayload(
      BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance) {
    return new CliOpeningBalancePayload(
        balance.accountCode().value(), balance.side().wireValue(), balance.amount());
  }

  private static CliForeignExchangeJsonModels.@Nullable ForeignExchangePayload
      foreignExchangePayload(@Nullable ForeignExchangeDetails foreignExchangeDetails) {
    if (foreignExchangeDetails == null) {
      return null;
    }
    return new CliForeignExchangeJsonModels.ForeignExchangePayload(
        foreignExchangeDetails.transactionAmount(),
        foreignExchangeDetails.functionalAmount(),
        new CliForeignExchangeJsonModels.QuotedExchangeRatePayload(
            foreignExchangeDetails.quotedExchangeRate().transactionCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().functionalCurrencyAmount(),
            foreignExchangeDetails.quotedExchangeRate().quotedOn().toString(),
            foreignExchangeDetails.quotedExchangeRate().quoteSource()),
        foreignExchangeDetails.treatmentKind().wireValue());
  }
}

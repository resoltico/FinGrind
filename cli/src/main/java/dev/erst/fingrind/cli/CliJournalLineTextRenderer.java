package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;

/** Shared text-table rendering for journal lines shown across posting and mutation surfaces. */
final class CliJournalLineTextRenderer {
  private CliJournalLineTextRenderer() {}

  static String renderLines(List<JournalLine> lines) {
    return CliTextFormat.renderTable(
        List.of("Account", "Side", "Currency", "Amount"),
        lines.stream()
            .map(
                line ->
                    List.of(
                        line.accountCode().value(),
                        CliTextDisplay.wireLabel(line.side().wireValue()),
                        line.amount().currencyUnit().code(),
                        CliTextFormat.displayMoney(line.amount().money())))
            .toList(),
        3);
  }

  static String renderPayloadLines(List<CliBookQueryJsonModels.JournalLinePayload> lines) {
    return CliTextFormat.renderAdaptiveTable(
        CliReportRenderSupport.TEXT_TABLE_WIDTH,
        List.of("Account", "Side", "Currency", "Amount"),
        lines.stream()
            .map(
                line ->
                    List.of(
                        line.accountCode(),
                        CliTextDisplay.wireLabel(line.side()),
                        line.amount().currencyCode(),
                        line.amount().canonicalDecimal()))
            .toList(),
        3);
  }
}

package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanLedgerFactJsonModels;
import dev.erst.fingrind.cli.json.CliPlanResultJsonModels;
import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Renders typed execute-plan payload details after the summary/journal shell is established. */
final class CliPlanDetailTextRenderer {
  private CliPlanDetailTextRenderer() {}

  static String renderStepData(CliPlanStepDataJsonModels.LedgerStepDataPayload dataPayload) {
    return switch (Objects.requireNonNull(dataPayload, "dataPayload")) {
      case CliPlanStepDataJsonModels.LedgerAdministrativeStepDataPayload administrative ->
          CliPlanAdministrativeTextRenderer.renderStepData(administrative);
      case CliPlanStepDataJsonModels.LedgerBookkeepingStepDataPayload bookkeeping ->
          CliPlanBookkeepingTextRenderer.renderStepData(bookkeeping);
      case CliPlanStepDataJsonModels.LedgerControlStepDataPayload control ->
          CliPlanControlTextRenderer.renderStepData(control);
    };
  }

  static String renderFailure(LedgerStepFailure failure) {
    CliPlanResultJsonModels.LedgerStepFailurePayload failurePayload =
        CliLedgerStepDataPayloadMapper.ledgerStepFailurePayload(failure);
    List<String> sections = new ArrayList<>();
    sections.add(
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Failure code", failurePayload.code()),
                List.of("Failure message", failurePayload.message()))));
    if (!failurePayload.details().isEmpty()) {
      sections.add(
          CliReportRenderSupport.section(
              "Failure details", renderFactDetails(failurePayload.details())));
    }
    return CliReportRenderSupport.joinSections(sections.toArray(String[]::new));
  }

  static String displayLabel(String value) {
    String normalized =
        Objects.requireNonNull(value, "value")
            .replace('-', ' ')
            .replace('_', ' ')
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .trim();
    return Arrays.stream(normalized.split("\\s+"))
        .filter(token -> !token.isBlank())
        .map(
            token ->
                token.substring(0, 1).toUpperCase(Locale.ROOT)
                    + token.substring(1).toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private static String renderFactDetails(
      List<CliPlanLedgerFactJsonModels.LedgerFactPayload> facts) {
    List<List<String>> valueRows = new ArrayList<>();
    List<String> groupSections = new ArrayList<>();
    for (CliPlanLedgerFactJsonModels.LedgerFactPayload fact : facts) {
      switch (fact) {
        case CliPlanLedgerFactJsonModels.TextLedgerFactPayload text ->
            valueRows.add(List.of(displayLabel(text.name()), text.value()));
        case CliPlanLedgerFactJsonModels.FlagLedgerFactPayload flag ->
            valueRows.add(
                List.of(
                    displayLabel(flag.name()),
                    CliQueryScopeText.displayBooleanLabel(flag.value())));
        case CliPlanLedgerFactJsonModels.CountLedgerFactPayload count ->
            valueRows.add(List.of(displayLabel(count.name()), Integer.toString(count.value())));
        case CliPlanLedgerFactJsonModels.MoneyLedgerFactPayload money ->
            valueRows.add(List.of(displayLabel(money.name()), displayMoney(money.value())));
        case CliPlanLedgerFactJsonModels.GroupLedgerFactPayload group ->
            groupSections.add(
                CliReportRenderSupport.section(
                    CliTextDisplay.wireLabel(group.name()), renderFactDetails(group.facts())));
      }
    }
    List<String> sections = new ArrayList<>();
    if (!valueRows.isEmpty()) {
      sections.add(CliTextFormat.renderKeyValueBlock(List.copyOf(valueRows)));
    }
    sections.addAll(groupSections);
    return CliReportRenderSupport.joinSections(sections.toArray(String[]::new));
  }

  private static String displayMoney(MonetaryAmount value) {
    return value.canonicalDecimal() + " " + value.currencyCode();
  }
}

package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
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

  static String renderStepData(CliPlanJsonModels.LedgerStepDataPayload dataPayload) {
    return switch (Objects.requireNonNull(dataPayload, "dataPayload")) {
      case CliPlanJsonModels.EnsureBookStepDataPayload ensureBook ->
          CliTextFormat.renderKeyValueBlock(
              List.of(
                  List.of("Initialized at", ensureBook.initializedAt()),
                  List.of("Entity name", ensureBook.entityName()),
                  List.of("Functional currency", ensureBook.functionalCurrency()),
                  List.of("Fiscal year start", ensureBook.fiscalYearStart())));
      case CliPlanJsonModels.DeclaredAccountStepDataPayload declaredAccount ->
          CliPlanBookkeepingTextRenderer.renderDeclaredAccount(declaredAccount.account());
      case CliPlanJsonModels.PreflightEntryStepDataPayload preflightEntry ->
          CliTextFormat.renderKeyValueBlock(
              List.of(
                  List.of("Idempotency key", preflightEntry.idempotencyKey()),
                  List.of("Effective date", preflightEntry.effectiveDate())));
      case CliPlanJsonModels.CommittedEntryStepDataPayload committedEntry ->
          CliTextFormat.renderKeyValueBlock(
              List.of(
                  List.of("Posting id", committedEntry.postingId()),
                  List.of("Idempotency key", committedEntry.idempotencyKey()),
                  List.of("Effective date", committedEntry.effectiveDate()),
                  List.of("Recorded at", committedEntry.recordedAt())));
      case CliPlanJsonModels.BookInspectionStepDataPayload inspection ->
          CliTextFormat.renderKeyValueBlock(
              List.of(
                  List.of("State", CliTextDisplay.wireLabel(inspection.state())),
                  List.of(
                      "Initialized",
                      CliQueryScopeText.displayBooleanLabel(inspection.initialized())),
                  List.of(
                      "Compatible with current binary",
                      CliQueryScopeText.displayBooleanLabel(
                          inspection.compatibleWithCurrentBinary()))));
      case CliPlanJsonModels.AccountPageStepDataPayload accountPage ->
          CliPlanBookkeepingTextRenderer.renderAccountPage(accountPage);
      case CliPlanJsonModels.PostingStepDataPayload posting ->
          CliPlanBookkeepingTextRenderer.renderPosting(posting.posting());
      case CliPlanJsonModels.PostingPageStepDataPayload postingPage ->
          CliPlanBookkeepingTextRenderer.renderPostingPage(postingPage);
      case CliPlanJsonModels.AccountBalanceStepDataPayload accountBalance ->
          CliPlanBookkeepingTextRenderer.renderAccountBalance(accountBalance);
      case CliPlanJsonModels.AccountCodeAssertionStepDataPayload accountCodeAssertion ->
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of("Account code", accountCodeAssertion.accountCode())));
      case CliPlanJsonModels.PostingIdAssertionStepDataPayload postingIdAssertion ->
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of("Posting id", postingIdAssertion.postingId())));
      case CliPlanJsonModels.PlanBoundaryStepDataPayload boundary ->
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of("Phase", CliTextDisplay.wireLabel(boundary.phase()))));
    };
  }

  static String renderFailure(LedgerStepFailure failure) {
    CliPlanJsonModels.LedgerStepFailurePayload failurePayload =
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

  private static String renderFactDetails(List<CliPlanJsonModels.LedgerFactPayload> facts) {
    List<List<String>> valueRows = new ArrayList<>();
    List<String> groupSections = new ArrayList<>();
    for (CliPlanJsonModels.LedgerFactPayload fact : facts) {
      switch (fact) {
        case CliPlanJsonModels.TextLedgerFactPayload text ->
            valueRows.add(List.of(displayLabel(text.name()), text.value()));
        case CliPlanJsonModels.FlagLedgerFactPayload flag ->
            valueRows.add(
                List.of(
                    displayLabel(flag.name()),
                    CliQueryScopeText.displayBooleanLabel(flag.value())));
        case CliPlanJsonModels.CountLedgerFactPayload count ->
            valueRows.add(List.of(displayLabel(count.name()), Integer.toString(count.value())));
        case CliPlanJsonModels.MoneyLedgerFactPayload money ->
            valueRows.add(List.of(displayLabel(money.name()), displayMoney(money.value())));
        case CliPlanJsonModels.GroupLedgerFactPayload group ->
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

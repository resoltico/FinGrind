package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared human-readable rendering for execute-plan results. */
final class CliPlanTextRenderer {
  private CliPlanTextRenderer() {}

  static String renderLedgerPlanResult(LedgerPlanResult result, PlanResultDetail resultDetail) {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(resultDetail, "resultDetail");
    LedgerExecutionJournal journal = result.journal();
    List<List<String>> summaryRows = new ArrayList<>();
    summaryRows.add(List.of("Plan id", result.planId().value()));
    summaryRows.add(List.of("Status", CliTextDisplay.wireLabel(result.status().wireValue())));
    summaryRows.add(List.of("Result detail", resultDetail.wireValue()));
    summaryRows.add(List.of("Started at", CliTextDisplay.instant(journal.startedAt())));
    summaryRows.add(List.of("Finished at", CliTextDisplay.instant(journal.finishedAt())));
    summaryRows.add(List.of("Step count", Integer.toString(journal.steps().size())));
    summaryRows.add(List.of("Terminal step", displayStepKind(journal.terminalStep())));
    if (journal.status() != LedgerPlanStatus.SUCCEEDED) {
      LedgerJournalEntry.Failed failedStep = journal.requiredFailedStep();
      LedgerStepFailure failure = failedStep.requiredFailure();
      summaryRows.add(List.of("Failed step id", failedStep.stepId().value()));
      summaryRows.add(List.of("Failure code", failure.code()));
      summaryRows.add(List.of("Failure message", failure.message()));
    }
    String summary =
        CliTextFormat.renderTitledBlock(
            "Execute Plan", CliTextFormat.renderKeyValueBlock(summaryRows));
    if (resultDetail != PlanResultDetail.FULL) {
      return summary;
    }
    return summary
        + System.lineSeparator()
        + System.lineSeparator()
        + CliTextFormat.renderTitledBlock("Journal", renderJournal(journal.steps()));
  }

  private static String renderJournal(List<LedgerJournalEntry> steps) {
    List<String> sections = new ArrayList<>();
    for (int index = 0; index < steps.size(); index++) {
      sections.add(renderJournalStep(index, steps.get(index)));
    }
    return String.join(System.lineSeparator() + System.lineSeparator(), sections);
  }

  private static String renderJournalStep(int index, LedgerJournalEntry step) {
    String heading =
        "%02d. %s [%s]"
            .formatted(
                index + 1,
                displayStepKind(step),
                CliTextDisplay.wireLabel(step.status().wireValue())
                    .toLowerCase(java.util.Locale.ROOT));
    List<List<String>> detailRows = new ArrayList<>();
    detailRows.add(List.of("Step id", step.stepId().value()));
    detailRows.add(List.of("Started at", CliTextDisplay.instant(step.startedAt())));
    detailRows.add(List.of("Finished at", CliTextDisplay.instant(step.finishedAt())));
    CliPlanJsonModels.LedgerStepDataPayload dataPayload =
        CliLedgerStepDataPayloadMapper.ledgerStepDataPayload(step);
    List<String> sections = new ArrayList<>();
    sections.add(CliTextFormat.renderKeyValueBlock(detailRows));
    if (dataPayload != null) {
      sections.add(
          CliReportRenderSupport.section(
              "Outcome", CliPlanDetailTextRenderer.renderStepData(dataPayload)));
    }
    step.optionalFailure()
        .ifPresent(
            failure ->
                sections.add(
                    CliReportRenderSupport.section(
                        "Failure", CliPlanDetailTextRenderer.renderFailure(failure))));
    return CliTextFormat.renderSummaryBlock(
        heading, CliReportRenderSupport.joinSections(sections.toArray(String[]::new)));
  }

  private static String displayStepKind(LedgerJournalEntry step) {
    String base =
        step.kind() == dev.erst.fingrind.contract.workflow.LedgerJournalKind.PLAN_BOUNDARY
            ? "Plan Boundary"
            : CliPlanDetailTextRenderer.displayLabel(step.kind().wireValue());
    if (step.detailKind() != null) {
      return base
          + " ("
          + CliPlanDetailTextRenderer.displayLabel(step.detailKind().wireValue())
          + ")";
    }
    LedgerBoundaryPhase boundaryPhase = step.boundaryPhase();
    if (boundaryPhase != null) {
      return base + " (" + CliPlanDetailTextRenderer.displayLabel(boundaryPhase.wireValue()) + ")";
    }
    return base;
  }
}

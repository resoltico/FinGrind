package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonValidationFixtures.accountPayload;
import static dev.erst.fingrind.cli.CliJsonValidationFixtures.postingSummaryPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanLedgerFactJsonModels;
import dev.erst.fingrind.cli.json.CliPlanResultJsonModels;
import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
import dev.erst.fingrind.cli.json.CliPostingRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerFactKind;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validates ledger-plan result, journal, and step-data transport invariants. */
class CliLedgerPlanJsonModelValidationTest {
  @Test
  void planAndRejectionPayloads_rejectEmptyRequiredLists() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanLedgerFactJsonModels.GroupLedgerFactPayload(
                LedgerFactKind.GROUP, "facts", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerExecutionJournalPayload(
                "start", "finish", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliPostingRejectionJsonModels.AccountStateViolationsDetails(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidRequestDetails(List.of()));
  }

  @Test
  void ledgerPlanPayloads_rejectInvalidResultDetailAndSummaryInvariants() {
    CliPlanResultJsonModels.LedgerPlanSummaryPayload summary =
        new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 1, 0, null);
    CliAttestationJsonModels.AttestationCommitPayload attestationCommit =
        new CliAttestationJsonModels.AttestationCommitPayload("1", "a".repeat(64));
    CliPlanResultJsonModels.LedgerExecutionJournalPayload journal =
        new CliPlanResultJsonModels.LedgerExecutionJournalPayload(
            "2026-05-14T10:00:00Z",
            "2026-05-14T10:00:01Z",
            List.of(
                new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                    "step-1",
                    LedgerStepKind.INSPECT_BOOK,
                    null,
                    null,
                    LedgerStepStatus.SUCCEEDED,
                    "2026-05-14T10:00:00Z",
                    "2026-05-14T10:00:01Z",
                    new CliPlanStepDataJsonModels.BookInspectionStepDataPayload(
                        "initialized", true, true),
                    null)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.FULL,
                summary,
                LedgerPlanAttestationDisposition.READ_ONLY,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.READ_ONLY,
                null,
                journal));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.REJECTED,
                PlanResultDetail.SUMMARY,
                new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                    "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, 1, "step-1"),
                null,
                new CliAttestationJsonModels.AttestationCommitPayload("1", "a".repeat(64)),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.APPENDED,
                null,
                null));
    assertEquals(
        LedgerPlanAttestationDisposition.APPENDED,
        new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.APPENDED,
                attestationCommit,
                null)
            .attestationDisposition());
    assertEquals(
        LedgerPlanAttestationDisposition.READ_ONLY,
        new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.READ_ONLY,
                null,
                null)
            .attestationDisposition());
    assertEquals(
        LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
        new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
                null,
                null)
            .attestationDisposition());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.READ_ONLY,
                attestationCommit,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanPayload(
                "plan-1",
                LedgerPlanStatus.SUCCEEDED,
                PlanResultDetail.SUMMARY,
                summary,
                LedgerPlanAttestationDisposition.NO_DURABLE_CHILD_MUTATION,
                attestationCommit,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 0, 0, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, -1, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, -1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 2, 1, 0, "step-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 2, 1, 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, 2, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 2, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 1, 0, "step-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, 1, null));
  }

  @Test
  void ledgerPlanPayloads_requireAttestationFactsOnlyForTheirApplicableOutcome() {
    CliPlanResultJsonModels.LedgerPlanSummaryPayload succeededSummary =
        new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 1, 0, null);
    CliPlanResultJsonModels.LedgerPlanSummaryPayload rejectedSummary =
        new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, 1, "step-1");

    assertEquals(
        "attestationDisposition is required when status is SUCCEEDED.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new CliPlanResultJsonModels.LedgerPlanPayload(
                        "plan-1",
                        LedgerPlanStatus.SUCCEEDED,
                        PlanResultDetail.SUMMARY,
                        succeededSummary,
                        null,
                        null,
                        null))
            .getMessage());
    assertEquals(
        "attestation disposition and commit must be absent unless status is SUCCEEDED.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new CliPlanResultJsonModels.LedgerPlanPayload(
                        "plan-1",
                        LedgerPlanStatus.REJECTED,
                        PlanResultDetail.SUMMARY,
                        rejectedSummary,
                        LedgerPlanAttestationDisposition.READ_ONLY,
                        null,
                        null))
            .getMessage());
  }

  @Test
  void ledgerPlanStepPayloads_rejectContradictoryKindsStatusesAndPages() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.INSPECT_BOOK,
                null,
                null,
                LedgerStepStatus.SUCCEEDED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                new CliPlanResultJsonModels.LedgerStepFailurePayload(
                    "failure", "message", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.INSPECT_BOOK,
                null,
                null,
                LedgerStepStatus.REJECTED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountPageStepDataPayload(
                -1, 1, null, false, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountPageStepDataPayload(
                1, 0, null, false, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountPageStepDataPayload(
                2,
                1,
                null,
                false,
                List.of(accountPayload("1000", "Cash"), accountPayload("2000", "Receivable"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountPageStepDataPayload(
                2, 2, null, false, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
                1, 2, "cursor-1", false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
                -1, 1, null, false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
                1, 0, null, false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
                2, 2, null, false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
                1, 2, null, true, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountBalanceStepDataPayload(
                accountPayload("1000", "Cash"), null, null, -1, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.INSPECT_BOOK,
                LedgerAssertionKind.ACCOUNT_DECLARED,
                null,
                LedgerStepStatus.SUCCEEDED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY,
                null,
                null,
                LedgerStepStatus.REJECTED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                new CliPlanResultJsonModels.LedgerStepFailurePayload(
                    "failure", "message", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.INSPECT_BOOK,
                null,
                LedgerBoundaryCheckpoint.BEGIN,
                LedgerStepStatus.SUCCEEDED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.INSPECT_BOOK,
                null,
                null,
                LedgerStepStatus.ASSERTION_FAILED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                new CliPlanResultJsonModels.LedgerStepFailurePayload(
                    "failure", "message", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountPageStepDataPayload(
                1, 2, null, true, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.PostingPageStepDataPayload(
                2, 1, "cursor-1", true, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanStepDataJsonModels.AccountBalanceStepDataPayload(
                accountPayload("1000", "Cash"), null, null, 2, List.of()));
  }
}

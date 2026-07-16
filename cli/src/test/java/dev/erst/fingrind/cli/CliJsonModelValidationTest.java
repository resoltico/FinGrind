package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCapabilitiesJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliPlanLedgerFactJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerFactKind;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins constructor invariants for package-private CLI JSON transport models. */
class CliJsonModelValidationTest {
  @Test
  void responseModels_trimTextAndRejectBlankValues() {
    CliEnvelopeJsonModels.Envelope<?> envelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            " query-book-not-initialized ",
            " The book is not initialized. ",
            " Repair hint. ",
            null,
            " idem-1 ",
            null,
            null);
    assertEquals(ProtocolEnvelopeStatus.REJECTED, envelope.status());
    assertEquals("query-book-not-initialized", envelope.code());
    assertEquals("The book is not initialized.", envelope.message());
    assertEquals("Repair hint.", envelope.hint());
    assertEquals("idem-1", envelope.idempotencyKey());
    assertThrows(
        NullPointerException.class,
        () ->
            new CliEnvelopeJsonModels.Envelope<>(
                nullOf(), null, "code", "message", null, null, null, null, null));
  }

  @Test
  void envelopeModels_enforceStatusSpecificDetailFamiliesAndForbiddenFields() {
    String planOperation = ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN);
    CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            null,
            "unknown-account",
            "Unknown account.",
            "Declare the account and retry.",
            null,
            null,
            new CliRejectionJsonModels.UnknownAccountDetails("9999"),
            null);
    assertInstanceOf(
        CliRejectionJsonModels.UnknownAccountDetails.class, rejectedEnvelope.details());

    CliEnvelopeJsonModels.Envelope<?> errorEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.ERROR,
            null,
            "invalid-request",
            "Request violates the schema.",
            "Fix the listed fields and retry.",
            "--request-file",
            null,
            new CliErrorJsonModels.InvalidRequestDetails(List.of("accountCode is required")),
            null);
    assertInstanceOf(CliErrorJsonModels.InvalidRequestDetails.class, errorEnvelope.details());

    IllegalArgumentException okForbiddenField =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.OK,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    "query-count",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
    assertEquals("code must be absent for this envelope status.", okForbiddenField.getMessage());

    IllegalArgumentException rejectedMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    null,
                    "unknown-account",
                    "Unknown account.",
                    null,
                    null,
                    null,
                    new CliErrorJsonModels.InvalidRequestDetails(
                        List.of("accountCode is required")),
                    null));
    assertEquals("Rejected envelopes only admit rejection details.", rejectedMismatch.getMessage());

    IllegalArgumentException errorMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.ERROR,
                    null,
                    "invalid-request",
                    "Request violates the schema.",
                    null,
                    null,
                    null,
                    new CliRejectionJsonModels.UnknownAccountDetails("9999"),
                    null));
    assertEquals("Error envelopes only admit error details.", errorMismatch.getMessage());

    IllegalArgumentException nonPlanPayloadMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    "unknown-account",
                    "Unknown account.",
                    null,
                    null,
                    null,
                    new CliRejectionJsonModels.UnknownAccountDetails("9999"),
                    null));
    assertEquals(
        "payload must be absent unless this non-success envelope carries a "
            + planOperation
            + " result.",
        nonPlanPayloadMismatch.getMessage());

    IllegalArgumentException rejectedPlanStatusMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.REJECTED,
                    samplePlanPayload(LedgerPlanStatus.ASSERTION_FAILED),
                    "assertion-failed",
                    "Plan assertion failed.",
                    null,
                    null,
                    null,
                    new CliRejectionJsonModels.UnknownAccountDetails("9999"),
                    null));
    assertEquals(
        "Rejected " + planOperation + " envelopes must carry a rejected plan payload.",
        rejectedPlanStatusMismatch.getMessage());

    IllegalArgumentException errorPlanStatusMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.ERROR,
                    samplePlanPayload(LedgerPlanStatus.REJECTED),
                    "plan-rejected",
                    "Plan rejected.",
                    null,
                    null,
                    null,
                    new CliErrorJsonModels.InvalidRequestDetails(List.of("request failed")),
                    null));
    assertEquals(
        "Error " + planOperation + " envelopes must carry an assertion-failed plan payload.",
        errorPlanStatusMismatch.getMessage());

    CliEnvelopeJsonModels.Envelope<?> rejectedPlanEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.REJECTED,
            samplePlanPayload(LedgerPlanStatus.REJECTED),
            "plan-rejected",
            "Plan rejected.",
            null,
            null,
            null,
            new CliRejectionJsonModels.UnknownAccountDetails("9999"),
            null);
    assertInstanceOf(CliPlanJsonModels.LedgerPlanPayload.class, rejectedPlanEnvelope.payload());

    CliEnvelopeJsonModels.Envelope<?> errorPlanEnvelope =
        new CliEnvelopeJsonModels.Envelope<>(
            ProtocolEnvelopeStatus.ERROR,
            samplePlanPayload(LedgerPlanStatus.ASSERTION_FAILED),
            "assertion-failed",
            "Plan assertion failed.",
            null,
            null,
            null,
            new CliErrorJsonModels.InvalidRequestDetails(List.of("assertion failed")),
            null);
    assertInstanceOf(CliPlanJsonModels.LedgerPlanPayload.class, errorPlanEnvelope.payload());

    IllegalArgumentException emptyArtifacts =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.OK,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()));
    assertEquals("artifacts must not be empty when present.", emptyArtifacts.getMessage());

    IllegalArgumentException duplicateArtifacts =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliEnvelopeJsonModels.Envelope<>(
                    ProtocolEnvelopeStatus.OK,
                    new CliDiscoveryCommonJsonModels.CommandCountPayload("query", 1),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        new CliEnvelopeJsonModels.SuccessArtifact("pdf", "/tmp/report.pdf"),
                        new CliEnvelopeJsonModels.SuccessArtifact("pdf", "/tmp/report.pdf"))));
    assertEquals("artifacts must not contain duplicate entries.", duplicateArtifacts.getMessage());
  }

  @Test
  void planAndRejectionPayloads_rejectEmptyRequiredLists() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanLedgerFactJsonModels.GroupLedgerFactPayload(
                LedgerFactKind.GROUP, "facts", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliPlanJsonModels.LedgerExecutionJournalPayload("start", "finish", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliRejectionJsonModels.AccountStateViolationsDetails(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidRequestDetails(List.of()));
  }

  @Test
  void discoveryPayloads_requireFullContractParity() {
    HelpDescriptor helpDescriptor = MachineContract.help(identity(), environment());
    CapabilitiesDescriptor capabilitiesDescriptor = MachineContract.capabilities(identity());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                "Discovery overview",
                DiscoveryDetail.FULL,
                null,
                List.of(),
                List.of(),
                List.of(),
                "Run fingrind capabilities --output json.",
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryHelpJsonModels.HelpOverviewPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                "Discovery overview",
                DiscoveryDetail.COMPACT,
                null,
                List.of(),
                List.of(),
                List.of(),
                "Run fingrind capabilities --output json.",
                helpDescriptor));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                DiscoveryDetail.FULL,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.storage(),
                capabilitiesDescriptor.commands(),
                capabilitiesDescriptor.requestInput(),
                List.of("Prefer --output json for agents."),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                DiscoveryDetail.COMPACT,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.storage(),
                capabilitiesDescriptor.commands(),
                capabilitiesDescriptor.requestInput(),
                List.of("Prefer --output json for agents."),
                capabilitiesDescriptor));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryHelpJsonModels.HelpOverviewMinimalPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                "Discovery overview",
                DiscoveryDetail.COMPACT,
                null,
                List.of(),
                "Run fingrind help --output json --detail compact.",
                "Run fingrind help --output json --detail full."));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliDiscoveryCapabilitiesJsonModels.CapabilitiesMinimalPayload(
                "FinGrind",
                "0.57.0",
                MachineContract.protocolVersion(),
                DiscoveryDetail.FULL,
                DiscoveryFocus.OVERVIEW,
                capabilitiesDescriptor.bookkeepingKernel().scope(),
                capabilitiesDescriptor.bookkeepingKernel().builtInStatements(),
                capabilitiesDescriptor.storage().bookBoundary(),
                capabilitiesDescriptor.currencyModel().scope(),
                capabilitiesDescriptor.currencyModel().multiCurrencyStatus(),
                new CliDiscoveryCommonJsonModels.RequestInputCompactPayload(
                    "--book-file",
                    List.of(
                        "--book-key-file", "--book-passphrase-stdin", "--book-passphrase-prompt"),
                    "--request-file",
                    List.of("post-entry"),
                    "-",
                    "--output"),
                "Run fingrind capabilities --output json --detail compact.",
                "Run fingrind capabilities --output json --detail full."));

    CliEnvelopeJsonModels.Envelope<CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload>
        envelope =
            new CliEnvelopeJsonModels.Envelope<>(
                ProtocolEnvelopeStatus.OK,
                new CliDiscoveryCapabilitiesJsonModels.CapabilitiesPayload(
                    "FinGrind",
                    "0.57.0",
                    MachineContract.protocolVersion(),
                    DiscoveryDetail.FULL,
                    DiscoveryFocus.OVERVIEW,
                    capabilitiesDescriptor.storage(),
                    capabilitiesDescriptor.commands(),
                    capabilitiesDescriptor.requestInput(),
                    List.of("Prefer --output json for agents."),
                    capabilitiesDescriptor),
                null,
                null,
                null,
                null,
                null,
                null,
                new ArrayList<>(
                    List.of(new CliEnvelopeJsonModels.SuccessArtifact("pdf", "/tmp/report.pdf"))));
    List<CliEnvelopeJsonModels.SuccessArtifact> artifacts = envelope.artifacts();
    assertNotNull(artifacts);
    assertEquals(1, artifacts.size());
    assertThrows(
        UnsupportedOperationException.class,
        () -> artifacts.add(new CliEnvelopeJsonModels.SuccessArtifact("json", "/tmp/out.json")));
  }

  @Test
  void parentAccountRejectionPayloads_validateRequiredFields() {
    CliRejectionJsonModels.ParentAccountDetails parentAccountDetails =
        new CliRejectionJsonModels.ParentAccountDetails("4100", "4000");
    CliRejectionJsonModels.ParentAccountTypeConflictDetails parentAccountTypeConflictDetails =
        new CliRejectionJsonModels.ParentAccountTypeConflictDetails(
            "4100", "EXPENSE", "4000", "REVENUE");
    CliRejectionJsonModels.ParentAccountNodeKindDetails parentAccountNodeKindDetails =
        new CliRejectionJsonModels.ParentAccountNodeKindDetails("4100", "4000", "POSTABLE");
    CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails
        parentAccountTaxonomyConflictDetails =
            new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
                "4100",
                new CliRejectionJsonModels.AccountTaxonomyDetails(
                    "POSTABLE", "4050", null, "OPERATING_EXPENSE"),
                "4000",
                new CliRejectionJsonModels.AccountTaxonomyDetails(
                    "POSTABLE", null, null, "COST_OF_SALES"));

    assertEquals("4100", parentAccountDetails.accountCode());
    assertEquals("4000", parentAccountDetails.parentAccountCode());
    assertEquals("EXPENSE", parentAccountTypeConflictDetails.requestedAccountType());
    assertEquals("REVENUE", parentAccountTypeConflictDetails.parentAccountType());
    assertEquals("POSTABLE", parentAccountNodeKindDetails.parentAccountNodeKind());
    assertEquals(
        "4050",
        parentAccountTaxonomyConflictDetails.requestedAccountTaxonomy().parentAccountCode());
    assertEquals(
        "COST_OF_SALES",
        parentAccountTaxonomyConflictDetails
            .parentAccountTaxonomy()
            .profitAndLossLineClassification());

    assertThrows(
        IllegalArgumentException.class,
        () -> new CliRejectionJsonModels.ParentAccountDetails(" ", "4000"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliRejectionJsonModels.ParentAccountTypeConflictDetails(
                "4100", " ", "4000", "REVENUE"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliRejectionJsonModels.ParentAccountNodeKindDetails("4100", "4000", " "));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
                "4100", nullOf(), "4000", nullOf()));
  }

  @Test
  void ledgerPlanPayloads_rejectInvalidResultDetailAndSummaryInvariants() {
    CliPlanJsonModels.LedgerPlanSummaryPayload summary =
        new CliPlanJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 1, 0, null);
    CliPlanJsonModels.LedgerExecutionJournalPayload journal =
        new CliPlanJsonModels.LedgerExecutionJournalPayload(
            "2026-05-14T10:00:00Z",
            "2026-05-14T10:00:01Z",
            List.of(
                new CliPlanJsonModels.LedgerJournalEntryPayload(
                    "step-1",
                    LedgerStepKind.ENSURE_BOOK,
                    null,
                    null,
                    LedgerStepStatus.SUCCEEDED,
                    "2026-05-14T10:00:00Z",
                    "2026-05-14T10:00:01Z",
                    new CliPlanJsonModels.EnsureBookStepDataPayload(
                        "2026-05-14T10:00:00Z", "Acme Studio", "EUR", "01-01"),
                    null)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanPayload(
                "plan-1", LedgerPlanStatus.SUCCEEDED, PlanResultDetail.FULL, summary, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanPayload(
                "plan-1", LedgerPlanStatus.SUCCEEDED, PlanResultDetail.SUMMARY, summary, journal));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 0, 0, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, -1, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, -1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 2, 1, 0, "step-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 2, 1, 1, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, 2, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 2, 0, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 1, 0, "step-1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, 1, null));
  }

  @Test
  void ledgerPlanStepPayloads_rejectContradictoryKindsStatusesAndPages() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.ENSURE_BOOK,
                null,
                null,
                LedgerStepStatus.SUCCEEDED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                new CliPlanJsonModels.LedgerStepFailurePayload("failure", "message", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.ENSURE_BOOK,
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
            new CliPlanJsonModels.AccountPageStepDataPayload(
                -1, 1, null, false, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.AccountPageStepDataPayload(
                1, 0, null, false, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.AccountPageStepDataPayload(
                2,
                1,
                null,
                false,
                List.of(accountPayload("1000", "Cash"), accountPayload("2000", "Receivable"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.AccountPageStepDataPayload(
                2, 2, null, false, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.PostingPageStepDataPayload(
                1, 2, "cursor-1", false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.PostingPageStepDataPayload(
                -1, 1, null, false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.PostingPageStepDataPayload(
                1, 0, null, false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.PostingPageStepDataPayload(
                2, 2, null, false, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.PostingPageStepDataPayload(
                1, 2, null, true, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.AccountBalanceStepDataPayload(
                accountPayload("1000", "Cash"), null, null, -1, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.ENSURE_BOOK,
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
            new CliPlanJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY,
                null,
                null,
                LedgerStepStatus.REJECTED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                new CliPlanJsonModels.LedgerStepFailurePayload("failure", "message", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.ENSURE_BOOK,
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
            new CliPlanJsonModels.LedgerJournalEntryPayload(
                "step-1",
                LedgerStepKind.ENSURE_BOOK,
                null,
                null,
                LedgerStepStatus.ASSERTION_FAILED,
                "2026-05-14T10:00:00Z",
                "2026-05-14T10:00:01Z",
                null,
                new CliPlanJsonModels.LedgerStepFailurePayload("failure", "message", List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.AccountPageStepDataPayload(
                1, 2, null, true, List.of(accountPayload("1000", "Cash"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.PostingPageStepDataPayload(
                2, 1, "cursor-1", true, List.of(postingSummaryPayload("posting-1"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.AccountBalanceStepDataPayload(
                accountPayload("1000", "Cash"), null, null, 2, List.of()));
  }

  @Test
  void cliFailure_normalizesTextAndRejectsBlankFields() {
    CliFailure failure =
        new CliFailure(
            " invalid-request ",
            " Message ",
            null,
            " --limit ",
            new CliErrorJsonModels.InvalidRequestDetails(List.of("One problem.")));
    assertEquals("invalid-request", failure.code());
    assertEquals("Message", failure.message());
    assertEquals("--limit", failure.argument());
    assertEquals(
        List.of("One problem."),
        assertInstanceOf(CliErrorJsonModels.InvalidRequestDetails.class, failure.details())
            .violations());
    assertThrows(IllegalArgumentException.class, () -> new CliFailure(" ", "message", null, null));
    assertThrows(
        IllegalArgumentException.class, () -> new CliFailure("invalid-request", " ", null, null));
    CliFailure blankOptionalFields = new CliFailure("invalid-request", "message", " ", " ");
    assertEquals("invalid-request", blankOptionalFields.code());
    assertEquals("message", blankOptionalFields.message());
    assertEquals(null, blankOptionalFields.hint());
    assertEquals(null, blankOptionalFields.argument());
  }

  @Test
  void cliFailure_preservesTypedInvalidJsonDetails() {
    CliFailure failure =
        new CliFailure(
            " invalid-request ",
            " Message ",
            null,
            null,
            new CliErrorJsonModels.InvalidJsonDetails(" Unexpected token ", 3, 14));
    assertEquals("invalid-request", failure.code());
    CliErrorJsonModels.InvalidJsonDetails details =
        assertInstanceOf(CliErrorJsonModels.InvalidJsonDetails.class, failure.details());
    assertEquals("Unexpected token", details.parseMessage());
    assertEquals(3, details.line());
    assertEquals(14, details.column());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 0, 14));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 3, 0));
  }

  @Test
  void parsedBookArguments_rejectNullCommandArguments() {
    assertEquals(
        "commandArguments",
        assertThrows(
                NullPointerException.class,
                () ->
                    new CliBookArgumentParser.ParsedBookArguments(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE),
                        nullOf(),
                        nullOf()))
            .getMessage());
  }

  @Test
  void scalarParsers_rejectUnsupportedAndParserFailureCases() {
    IllegalArgumentException unsupportedValue =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliJsonScalarParsers.parseWireValue(
                    "BAD", "pricingMode", List.of("GOOD"), value -> value));
    IllegalArgumentException parserFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliJsonScalarParsers.parseWireValue(
                    "GOOD",
                    "pricingMode",
                    List.of("GOOD"),
                    value -> {
                      throw new IllegalArgumentException("parser failed");
                    }));

    assertEquals(
        "Unsupported value for pricingMode: BAD. Accepted values: GOOD.",
        unsupportedValue.getMessage());
    assertEquals(
        "Unsupported value for pricingMode: GOOD. Accepted values: GOOD.",
        parserFailure.getMessage());
    assertInstanceOf(IllegalArgumentException.class, parserFailure.getCause());
  }

  private static ApplicationIdentity identity() {
    return new ApplicationIdentity(
        "FinGrind",
        "0.57.0",
        "Command-line double-entry bookkeeping with one protected book per accounting entity");
  }

  private static EnvironmentDescriptor environment() {
    return CliResponseWriterTestSupport.environmentDescriptor(
        RuntimeDistribution.SELF_CONTAINED_BUNDLE.wireValue(),
        dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus.VERIFIED,
        "ready",
        ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
        ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
        null);
  }

  private static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(
      String accountCode, String accountName) {
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        accountCode,
        accountName,
        "ASSET",
        "POSTABLE",
        null,
        "CURRENT_ASSET",
        null,
        null,
        null,
        "DEBIT",
        true,
        "2026-05-14T10:00:00Z");
  }

  private static CliPlanJsonModels.LedgerPlanPayload samplePlanPayload(LedgerPlanStatus status) {
    return new CliPlanJsonModels.LedgerPlanPayload(
        "plan-1",
        status,
        PlanResultDetail.SUMMARY,
        new CliPlanJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z",
            "2026-05-14T10:00:01Z",
            1,
            status == LedgerPlanStatus.SUCCEEDED ? 1 : 0,
            status == LedgerPlanStatus.SUCCEEDED ? 0 : 1,
            status == LedgerPlanStatus.SUCCEEDED ? null : "step-1"),
        null);
  }

  private static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      String postingId) {
    return new CliBookQueryJsonModels.PostingSummaryPayload(
        postingId,
        "STANDARD",
        "SALE_SETTLED",
        "ACTIVE",
        null,
        null,
        "2026-05-14",
        "2026-05-14T10:00:00Z",
        MonetaryAmount.of(dev.erst.fingrind.core.Money.parse("EUR", "0.00")),
        MonetaryAmount.of(dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
        List.of("4000"),
        List.of("invoice-1"),
        List.of());
  }
}

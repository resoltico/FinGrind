package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliAccountRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliAccountStateViolationPayload;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliAttestationRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliCloseRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliEntrySemanticsViolationPayload;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanResultJsonModels;
import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels;
import dev.erst.fingrind.cli.json.CliPostingRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliQueryPlanRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for plain-language deterministic CLI failure rendering. */
class CliFailureOutputRendererTest {
  @Test
  void renderFailureText_rendersArgumentHintAndStructuredErrorDetails() {
    String invalidJson =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "invalid-request",
                "Malformed request.",
                "Fix the JSON syntax and retry.",
                "--request-file",
                new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 3, 9)));
    String invalidRequest =
        CliFailureOutputRenderer.renderDeterministicFailureText(
            new CliFailure(
                "invalid-request",
                "Request violates the schema.",
                "Correct the listed fields and rerun.",
                "--request-file",
                new CliErrorJsonModels.InvalidRequestDetails(
                    List.of("accountCode is required", "amount must be positive"))));

    assertTrue(invalidJson.contains("Error"));
    assertTrue(invalidJson.contains("Argument"));
    assertTrue(invalidJson.contains("--request-file"));
    assertTrue(invalidJson.contains("Hint"));
    assertTrue(invalidJson.contains("Unexpected token"));
    assertTrue(invalidJson.contains("line 3, column 9"));
    assertTrue(invalidRequest.contains("Rejected"));
    assertTrue(invalidRequest.contains("Violations"));
    assertTrue(invalidRequest.contains("accountCode is required"));
    assertTrue(invalidRequest.contains("amount must be positive"));

    String staleHead =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "stale-head",
                "Signed head is stale.",
                "Re-sign.",
                null,
                new CliErrorJsonModels.StaleHeadDetails("a".repeat(64), "b".repeat(64), "17")));
    assertTrue(staleHead.contains("Observed head"));
    assertTrue(staleHead.contains("Current head"));
    assertTrue(staleHead.contains("Current order"));

    String publicationUncertain =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "artifact-publication-durability-uncertain",
                "Artifact durability is unconfirmed.",
                "Inspect the artifact.",
                "--pdf-out",
                new CliMaintenanceErrorJsonModels.ArtifactPublicationDurabilityUncertainDetails(
                    new CliMaintenanceErrorJsonModels.PublishedArtifact(
                        "/Users/private-fixture/FinGrind/reports/report.pdf",
                        "/Users/private-fixture/FinGrind/reports/.fingrind-report-stage.tmp"))));
    assertTrue(publicationUncertain.contains("Published artifact"));
    assertTrue(publicationUncertain.contains("<redacted>/FinGrind/reports/report.pdf"));
    assertTrue(publicationUncertain.contains("Retained stage path"));
    assertTrue(
        publicationUncertain.contains("<redacted>/FinGrind/reports/.fingrind-report-stage.tmp"));
    assertFalse(publicationUncertain.contains("cleanup"), publicationUncertain);
    assertFalse(publicationUncertain.contains("/Users/private-fixture"), publicationUncertain);

    String pairPublicationUncertain =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "protected-book-pair-publication-uncertain",
                "Protected-book pair completion is uncertain.",
                "Preserve both pair members and rerun the exact operation.",
                "--book-file",
                new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails(
                    "restore-book",
                    new CliMaintenanceErrorJsonModels.PairPublication(
                        new CliMaintenanceErrorJsonModels.PairPublicationMember(
                            "/Users/private-fixture/FinGrind/books/restored.sqlite",
                            CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload
                                .NOT_ATTEMPTED),
                        new CliMaintenanceErrorJsonModels.PairPublicationMember(
                            "/Users/private-fixture/FinGrind/keys/restored.book-key",
                            CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload
                                .NOT_ATTEMPTED),
                        CliMaintenanceErrorJsonModels.PairPublicationRecoveryRecordStatePayload
                            .DURABILITY_UNCONFIRMED,
                        new CliMaintenanceErrorJsonModels.PairPublicationRetention(
                            new CliMaintenanceErrorJsonModels.PublishedArtifact(
                                "/Users/private-fixture/FinGrind/books/restored.sqlite",
                                "/Users/private-fixture/FinGrind/books/.restored-book-stage"),
                            new CliMaintenanceErrorJsonModels.PublishedArtifact(
                                "/Users/private-fixture/FinGrind/keys/restored.book-key",
                                "/Users/private-fixture/FinGrind/keys/.restored-secret-stage"))))));
    assertTrue(pairPublicationUncertain.contains("Operation"));
    assertTrue(pairPublicationUncertain.contains("restore-book"));
    assertTrue(pairPublicationUncertain.contains("Book target"));
    assertTrue(pairPublicationUncertain.contains("Generated secret target"));
    assertTrue(pairPublicationUncertain.contains("Recovery record state"));
    assertTrue(pairPublicationUncertain.contains("Book retained stage"));
    assertTrue(pairPublicationUncertain.contains("Generated secret retained stage"));
    assertTrue(pairPublicationUncertain.contains("durability-unconfirmed"));
    assertTrue(pairPublicationUncertain.contains("<redacted>/FinGrind/books/restored.sqlite"));
    assertTrue(pairPublicationUncertain.contains("<redacted>/FinGrind/keys/restored.book-key"));
    assertFalse(
        pairPublicationUncertain.contains("/Users/private-fixture"), pairPublicationUncertain);

    String outcomeUncertain =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "artifact-publication-outcome-uncertain",
                "Artifact publication outcome is unconfirmed.",
                "Inspect the candidate.",
                "--pdf-out",
                new CliMaintenanceErrorJsonModels.ArtifactPublicationOutcomeUncertainDetails(
                    "/Users/private-fixture/FinGrind/reports/candidate.pdf",
                    "/Users/private-fixture/FinGrind/reports/.fingrind-candidate-stage.tmp")));
    assertTrue(outcomeUncertain.contains("Candidate artifact path"));
    assertTrue(outcomeUncertain.contains("<redacted>/FinGrind/reports/candidate.pdf"));
    assertTrue(outcomeUncertain.contains("Retained stage path"));
    assertTrue(
        outcomeUncertain.contains("<redacted>/FinGrind/reports/.fingrind-candidate-stage.tmp"));
    assertFalse(outcomeUncertain.contains("cleanup"), outcomeUncertain);
    assertFalse(outcomeUncertain.contains("/Users/private-fixture"), outcomeUncertain);

    String unsupportedFormat =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure(
                "unsupported-book-format-version",
                "The selected FinGrind book uses format version 7, but this FinGrind binary"
                    + " supports version 8 only.",
                "Use a matching binary.",
                "--book-file",
                new CliErrorJsonModels.UnsupportedBookFormatVersionDetails(7, 8)));
    assertTrue(unsupportedFormat.contains("Detected book format version"));
    assertTrue(unsupportedFormat.contains("7"));
    assertTrue(unsupportedFormat.contains("Supported book format version"));
    assertTrue(unsupportedFormat.contains("8"));
  }

  @Test
  void renderFailureText_omitsHintRowWhenNoHintIsProvided() {
    String rendered =
        CliFailureOutputRenderer.renderFailureText(
            new CliFailure("storage-runtime-failure", "Runtime exploded.", null, "--book-file"));

    assertTrue(rendered.contains("Argument"));
    assertTrue(rendered.contains("--book-file"));
    assertFalse(rendered.contains("Hint"), rendered);
  }

  @Test
  void renderRejectedText_rendersPostingAndAccountStateRejectionDetails() {
    assertRenderedNestedRepairableRejection(
        "account-state-violations",
        "Posting rejected with 3 account-state issues.",
        new CliPostingRejectionJsonModels.AccountStateViolationsDetails(
            List.of(
                new CliAccountStateViolationPayload(
                    "unknown-account",
                    "lines[].accountCode",
                    "Journal line references undeclared account '1000'.",
                    "account-registry",
                    "Declare the missing account before retrying the posting.",
                    "1000",
                    null),
                new CliAccountStateViolationPayload(
                    "inactive-account",
                    "lines[].accountCode",
                    "Journal line references inactive account '2000'.",
                    "account-activation",
                    "Reactivate the account or replace it with an active posting account before"
                        + " retrying.",
                    "2000",
                    null),
                new CliAccountStateViolationPayload(
                    "non-postable-account",
                    "lines[].accountCode",
                    "Journal line references header account '3000', declared as 'HEADER', which"
                        + " cannot accept direct postings.",
                    "account-node-kind",
                    "Replace the header account with a postable account before retrying.",
                    "3000",
                    "HEADER"))),
        "Summary",
        "Issue 1 | unknown-account",
        "Issue 2 | inactive-account",
        "Issue 3 | non-postable-account",
        "Account node kind",
        "HEADER",
        "Why");
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.PriorPostingDetails("posting-9"),
        "Prior posting id",
        "posting-9");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.AccountDependenciesDetails(
            "1100", List.of("postings", "tax-registrations")),
        "Account code",
        "1100",
        "Durable dependencies",
        "postings, tax-registrations");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.AccountCodeDetails("1100"), "Account code", "1100");
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAccountRejectionJsonModels.AccountDependenciesDetails("1100", List.of()));
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.AccountTypeConflictDetails("3200", "EQUITY", "LIABILITY"),
        "Existing account type",
        "Requested account type",
        "LIABILITY");
  }

  @Test
  void renderRejectedText_rendersAccountTaxonomyAndHierarchyRejectionDetails() {
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.AccountTaxonomyConflictDetails(
            "3200",
            new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "3000", "OTHER_EQUITY", null),
            new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "3010", "RESULT_HOLDING", null)),
        "Existing parent account",
        "3000",
        "Existing financial position classification",
        "OTHER_EQUITY",
        "Requested parent account",
        "3010",
        "Requested financial position classification",
        "RESULT_HOLDING");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.AccountTaxonomyConflictDetails(
            "4100",
            new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", null, null, "COST_OF_SALES"),
            new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "4000", null, "OPERATING_EXPENSE")),
        "Existing parent account",
        "(none)",
        "Existing financial position classification",
        "(none)",
        "Existing profit-and-loss classification",
        "COST_OF_SALES",
        "Requested parent account",
        "4000",
        "Requested financial position classification",
        "(none)",
        "Requested profit-and-loss classification",
        "OPERATING_EXPENSE");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.ContraAccountDetails(
            "4090", "4000", "statement-taxonomy-mismatch"),
        "Account code",
        "4090",
        "Contra account code",
        "4000",
        "Contra relationship",
        "statement-taxonomy-mismatch");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.ParentAccountDetails("4100", "4000"),
        "Account code",
        "4100",
        "Parent account code",
        "4000");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.ParentAccountTypeConflictDetails(
            "4100", "EXPENSE", "4000", "REVENUE"),
        "Requested account type",
        "EXPENSE",
        "Parent account type",
        "REVENUE");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.ParentAccountNodeKindDetails("4100", "4000", "POSTABLE"),
        "Parent account code",
        "4000",
        "Parent account node kind",
        "POSTABLE");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
            "4100",
            new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", "4050", null, "OPERATING_EXPENSE"),
            "4000",
            new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                "POSTABLE", null, null, "COST_OF_SALES")),
        "Requested parent account",
        "4050",
        "Requested profit-and-loss classification",
        "OPERATING_EXPENSE",
        "Parent account code",
        "4000",
        "Parent parent account",
        "(none)",
        "Parent profit-and-loss classification",
        "COST_OF_SALES");
  }

  @Test
  void renderRejectedText_rendersPostingPolicyAndCloseTargetRejectionDetails() {
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.FunctionalCurrencyMismatchDetails("EUR", "USD"),
        "Functional currency",
        "Attempted currency",
        "USD");
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.PostingEffectiveDateBeforeBookStartDetails(
            "2026-06-29", "2026-06-30"),
        "Attempted effective date",
        "2026-06-29",
        "Book start effective date",
        "2026-06-30");
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.PostingEffectiveDateInFutureDetails(
            "2026-07-01", "2026-06-30"),
        "Attempted effective date",
        "2026-07-01",
        "Current UTC date",
        "2026-06-30");
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.OpeningPositionWindowClosedDetails(
            "STANDARD", "2026-04-07"),
        "First blocking posting kind",
        "STANDARD",
        "First blocking effective date",
        "2026-04-07");
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.OpeningPositionNominalAccountDetails("4000", "REVENUE"),
        "Account code",
        "4000",
        "Account type",
        "REVENUE");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.ReservedResultClassificationDetails(
            "3200", "result-holding"),
        "Account code",
        "3200",
        "Financial position classification",
        "result-holding");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.CloseTargetAccountCandidateMissingDetails(
            "retained-earnings", List.of("3200")),
        "Required financial position classification",
        "retained-earnings",
        "Inactive candidate account codes",
        "3200");
    assertRenderedRejection(
        new CliAccountRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails(
            "other-equity", List.of("3200", "3210")),
        "Required financial position classification",
        "other-equity",
        "Candidate account codes",
        "3200, 3210");
  }

  @Test
  void renderRejectedText_rendersCloseWindowAndQueryPlanRejectionDetails() {
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.InterimResultSweepStartDetails("2026-04-01"),
        "Required start date",
        "2026-04-01");
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.InterimResultSweepFutureDateDetails("2026-05-01"),
        "Attempted end date",
        "2026-05-01");
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.InterimResultSweepFiscalYearDetails(
            "2026-12-15", "2027-01-15", "01-01"),
        "Attempted start date",
        "Attempted end date",
        "Fiscal year start");
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.FiscalYearCloseStartDetails("2026-01-01"),
        "Required start date",
        "2026-01-01");
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.FiscalYearCloseEndDetails("2026-12-31"),
        "Required end date",
        "2026-12-31");
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.FiscalYearCloseTransferredThroughDetails(
            "2025-12-31", "2026-03-31"),
        "Attempted end date",
        "2025-12-31",
        "Transferred-through date",
        "2026-03-31");
    assertRenderedRejection(
        new CliCloseRejectionJsonModels.FiscalYearCloseFutureDateDetails("2027-01-01"),
        "Attempted end date",
        "2027-01-01");
    assertRenderedRejection(
        new CliPostingRejectionJsonModels.SweptInterimResultViolationDetails(
            "2026-04-30", "2026-05-01"),
        "Transferred through",
        "Attempted effective date",
        "2026-05-01");
    assertRenderedRejection(
        new CliQueryPlanRejectionJsonModels.UnknownAccountDetails("9999"), "Account code", "9999");
    assertRenderedRejection(
        new CliQueryPlanRejectionJsonModels.PostingNotFoundDetails("posting-404"),
        "Posting id",
        "posting-404");
    assertRenderedRejection(
        new CliQueryPlanRejectionJsonModels.PlanRejectionDetails(samplePlan()),
        "Plan id",
        "plan-1");
  }

  @Test
  void renderRejectedText_rendersTaxRejectionDetails() {
    assertRenderedRejection(
        new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails(
            List.of(
                new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels
                    .TaxDefinitionViolationDetails(
                    "missing-tax-code", "taxCodes[0].taxCode", "Tax code is required."),
                new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels
                    .TaxDefinitionViolationDetails(
                    "invalid-jurisdiction", null, "Jurisdiction must be ISO 3166-1 alpha-2."))),
        "Violation 1",
        "taxCodes[0].taxCode [missing-tax-code]: Tax code is required.",
        "Violation 2",
        "invalid-jurisdiction [invalid-jurisdiction]: Jurisdiction must be ISO 3166-1 alpha-2.");
    assertRenderedRejection(
        new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails(
            "vat-missing"),
        "Tax registration id",
        "vat-missing");
    assertRenderedRejection(
        new dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails(
            "MONTHLY", "2026-04-01", "2026-04-15"),
        "Obligation frequency",
        "Monthly",
        "Requested period start",
        "2026-04-01",
        "Requested period end",
        "2026-04-15");
  }

  @Test
  void renderRejectedText_rendersEntrySemanticsViolationsWithAndWithoutField() {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "entry-semantics-violations",
            "Posting rejected with 2 entry-semantics issues.",
            null,
            null,
            new CliPostingRejectionJsonModels.EntrySemanticsViolationsDetails(
                List.of(
                    new CliEntrySemanticsViolationPayload(
                        "account-type-mismatch",
                        "cashAccountCode",
                        "cash account must be declared as ASSET",
                        "account-type",
                        "Use accounts whose declared account type matches the violated field"
                            + " requirement."),
                    new CliEntrySemanticsViolationPayload(
                        "source-document-type-not-accepted",
                        null,
                        "invoice does not prove cash receipt",
                        "source-document-type",
                        "Use an accepted source document type for the selected entry kind's"
                            + " source-document policy."))));

    assertTrue(rendered.contains("entry-semantics-violations"));
    assertTrue(rendered.contains("Summary"));
    assertTrue(rendered.contains("Issue 1 | account-type-mismatch"));
    assertTrue(rendered.contains("Issue 2 | source-document-type-not-accepted"));
    assertTrue(rendered.contains("Field"));
    assertTrue(rendered.contains("cashAccountCode"));
    assertTrue(rendered.contains("Repair"));
    assertTrue(rendered.contains("Why"));
    assertTrue(rendered.contains("invoice does not prove cash receipt"));
    assertFalse(rendered.contains("Hint"));
    assertFalse(rendered.contains("Idempotency key"));
  }

  @Test
  void renderRejectedText_rendersPayrollProfileFactsWithoutAFragmentedSentence() {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "entry-semantics-violations",
            "Posting rejected with 1 entry-semantics issue.",
            null,
            null,
            new CliPostingRejectionJsonModels.EntrySemanticsViolationsDetails(
                List.of(
                    new CliEntrySemanticsViolationPayload(
                        "latvian-payroll-profile-not-admitted",
                        "taxBookHeldAtEmployer",
                        "entryKind 'LATVIAN_MONTHLY_PAYROLL' does not admit taxBookHeldAtEmployer"
                            + " 'false'.",
                        "latvian-payroll-profile",
                        "Use EUR gross wages up to EUR 8,775.00, a 2026 payroll month,"
                            + " taxBookHeldAtEmployer true, and dependantCount 0; record any other"
                            + " case in an owned context that admits it."))));

    assertTrue(rendered.contains("does not admit taxBookHeldAtEmployer 'false'."), rendered);
    assertTrue(rendered.contains("taxBookHeldAtEmployer true"), rendered);
    assertFalse(rendered.contains("false.."), rendered);
  }

  @Test
  void renderRejectedText_rendersAttestationReviewRequiredDetailsAsGroupedEvidence() {
    String credentialKeyId = "c".repeat(64);
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "attestation-review-required",
            "The verified chain requires incident review.",
            "Resolve the incident before accepting further work.",
            null,
            new CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails(
                "book-1",
                new CliAttestationJsonModels.AttestationHeadPayload("8", "a".repeat(64)),
                "b".repeat(64),
                List.of(
                    new CliAttestationJsonModels.AttestationReviewFindingPayload(
                        credentialKeyId, "4", "8", "5"),
                    new CliAttestationJsonModels.AttestationReviewFindingPayload(
                        credentialKeyId, "4", "8", "6"),
                    new CliAttestationJsonModels.AttestationReviewFindingPayload(
                        credentialKeyId, "4", "8", "8"))));

    assertTrue(rendered.contains("Book ID"), rendered);
    assertTrue(rendered.contains("book-1"), rendered);
    assertTrue(rendered.contains("Attestation order"), rendered);
    assertTrue(rendered.contains("Attestation head"), rendered);
    assertTrue(rendered.contains("Previous attestation head"), rendered);
    assertTrue(rendered.contains("Review findings"), rendered);
    assertEquals(1, occurrences(rendered, "Review declaration"), rendered);
    assertTrue(rendered.contains("Credential key ID: " + credentialKeyId), rendered);
    assertTrue(rendered.contains("Review window: 4 through 8"), rendered);
    assertTrue(rendered.contains("Affected operation orders: 5-6, 8"), rendered);
  }

  @Test
  void renderRejectedText_rendersOpenEndedAttestationReviewDeclarationsThroughTheHead() {
    String credentialKeyId = "c".repeat(64);
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "attestation-review-required",
            "The verified chain requires incident review.",
            "Resolve the incident before accepting further work.",
            null,
            new CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails(
                "book-1",
                new CliAttestationJsonModels.AttestationHeadPayload("8", "a".repeat(64)),
                "b".repeat(64),
                List.of(
                    new CliAttestationJsonModels.AttestationReviewFindingPayload(
                        credentialKeyId, "4", null, "8"))));

    assertTrue(rendered.contains("Review window: 4 through head"), rendered);
    assertTrue(rendered.contains("Affected operation orders: 8"), rendered);
  }

  @Test
  void renderRejectedText_rejectsRoutingNestedPostingDetailsThroughTheSharedRowAppender() {
    assertThrows(
        IllegalStateException.class,
        () ->
            CliPostingRejectionTextRenderer.appendRows(
                new ArrayList<>(),
                new CliPostingRejectionJsonModels.AccountStateViolationsDetails(
                    List.of(
                        new CliAccountStateViolationPayload(
                            "unknown-account",
                            "lines[].accountCode",
                            "Journal line references undeclared account '1000'.",
                            "account-registry",
                            "Declare the missing account before retrying the posting.",
                            "1000",
                            null)))));
    assertThrows(
        IllegalStateException.class,
        () ->
            CliPostingRejectionTextRenderer.appendRows(
                new ArrayList<>(),
                new CliPostingRejectionJsonModels.EntrySemanticsViolationsDetails(
                    List.of(
                        new CliEntrySemanticsViolationPayload(
                            "account-type-mismatch",
                            "cashAccountCode",
                            "cash account must be declared as ASSET",
                            "account-type",
                            "Use accounts whose declared account type matches the violated field"
                                + " requirement.")))));
  }

  private static void assertRenderedRejection(
      CliRejectionJsonModels.RejectionDetails details, String... expectedFragments) {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(
            "rejected-code", "Rejected message.", "Repair hint.", "idem-1", details);
    assertTrue(rendered.contains("Rejected"));
    assertTrue(rendered.contains("Idempotency key"));
    assertTrue(rendered.contains("idem-1"));
    assertTrue(rendered.contains("Repair hint."));
    for (String expectedFragment : expectedFragments) {
      assertTrue(rendered.contains(expectedFragment));
    }
  }

  private static void assertRenderedNestedRepairableRejection(
      String code,
      String summary,
      CliRejectionJsonModels.RejectionDetails details,
      String... expectedFragments) {
    String rendered =
        CliFailureOutputRenderer.renderRejectedText(code, summary, null, "idem-1", details);
    assertTrue(rendered.contains("Rejected"));
    assertTrue(rendered.contains("Idempotency key"));
    assertTrue(rendered.contains("idem-1"));
    assertTrue(rendered.contains("Summary"));
    assertTrue(rendered.contains(summary));
    assertFalse(rendered.contains("Hint"));
    for (String expectedFragment : expectedFragments) {
      assertTrue(rendered.contains(expectedFragment));
    }
  }

  private static CliPlanResultJsonModels.LedgerPlanPayload samplePlan() {
    return new CliPlanResultJsonModels.LedgerPlanPayload(
        "plan-1",
        LedgerPlanStatus.REJECTED,
        PlanResultDetail.FULL,
        new CliPlanResultJsonModels.LedgerPlanSummaryPayload(
            "2026-05-13T10:15:30Z", "2026-05-13T10:15:31Z", 1, 0, 1, "step-1"),
        null,
        null,
        new CliPlanResultJsonModels.LedgerExecutionJournalPayload(
            "2026-05-13T10:15:30Z",
            "2026-05-13T10:15:31Z",
            List.of(
                new CliPlanResultJsonModels.LedgerJournalEntryPayload(
                    "step-1",
                    LedgerStepKind.ASSERT,
                    LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                    null,
                    LedgerStepStatus.ASSERTION_FAILED,
                    "2026-05-13T10:15:30Z",
                    "2026-05-13T10:15:31Z",
                    new CliPlanStepDataJsonModels.AccountCodeAssertionStepDataPayload("1000"),
                    new CliPlanResultJsonModels.LedgerStepFailurePayload(
                        "assertion-failed", "Rejected message.", List.of())))));
  }

  private static int occurrences(String text, String fragment) {
    return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
  }
}

package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins constructor invariants for package-private CLI JSON transport models. */
class CliJsonModelValidationTest {
  @Test
  void responseModels_trimTextAndRejectBlankValues() {
    CliEnvelopeJsonModels.RejectedEnvelope envelope =
        new CliEnvelopeJsonModels.RejectedEnvelope(
            ProtocolRejectionStatus.REJECTED,
            " query-book-not-initialized ",
            " The book is not initialized. ",
            " Repair hint. ",
            " idem-1 ",
            null);
    assertEquals(ProtocolRejectionStatus.REJECTED, envelope.status());
    assertEquals("query-book-not-initialized", envelope.code());
    assertEquals("The book is not initialized.", envelope.message());
    assertEquals("Repair hint.", envelope.hint());
    assertEquals("idem-1", envelope.idempotencyKey());
    assertThrows(
        NullPointerException.class,
        () ->
            new CliEnvelopeJsonModels.RejectedEnvelope(
                nullOf(), "code", "message", null, null, null));
  }

  @Test
  void planAndRejectionPayloads_rejectEmptyRequiredLists() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliPlanJsonModels.GroupLedgerFactPayload("group", "facts", List.of()));
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
  void parentAccountRejectionPayloads_validateRequiredFields() {
    CliRejectionJsonModels.ParentAccountDetails parentAccountDetails =
        new CliRejectionJsonModels.ParentAccountDetails("4100", "4000");
    CliRejectionJsonModels.ParentAccountTypeConflictDetails parentAccountTypeConflictDetails =
        new CliRejectionJsonModels.ParentAccountTypeConflictDetails(
            "4100", "EXPENSE", "4000", "REVENUE");
    CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails
        parentAccountTaxonomyConflictDetails =
            new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
                "4100",
                new CliRejectionJsonModels.AccountTaxonomyDetails(
                    "4050", null, "OPERATING_EXPENSE"),
                "4000",
                new CliRejectionJsonModels.AccountTaxonomyDetails(null, null, "COST_OF_SALES"));

    assertEquals("4100", parentAccountDetails.accountCode());
    assertEquals("4000", parentAccountDetails.parentAccountCode());
    assertEquals("EXPENSE", parentAccountTypeConflictDetails.requestedAccountType());
    assertEquals("REVENUE", parentAccountTypeConflictDetails.parentAccountType());
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
        NullPointerException.class,
        () ->
            new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
                "4100", nullOf(), "4000", nullOf()));
  }

  @Test
  void ledgerPlanPayloads_rejectInvalidResultDetailAndSummaryInvariants() {
    CliPlanJsonModels.LedgerPlanSummaryPayload summary =
        new CliPlanJsonModels.LedgerPlanSummaryPayload(
            "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 1, 0, null, null, null);
    CliPlanJsonModels.LedgerExecutionJournalPayload journal =
        new CliPlanJsonModels.LedgerExecutionJournalPayload(
            "2026-05-14T10:00:00Z",
            "2026-05-14T10:00:01Z",
            List.of(
                new CliPlanJsonModels.LedgerJournalEntryPayload(
                    "step-1",
                    LedgerJournalKind.OPEN_BOOK,
                    null,
                    null,
                    LedgerStepStatus.SUCCEEDED,
                    "2026-05-14T10:00:00Z",
                    "2026-05-14T10:00:01Z",
                    List.of(new CliPlanJsonModels.TextLedgerFactPayload("text", "detail", "value")),
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
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 0, 0, 0, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, -1, 0, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPlanJsonModels.LedgerPlanSummaryPayload(
                "2026-05-14T10:00:00Z", "2026-05-14T10:00:01Z", 1, 0, -1, null, null, null));
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
    assertThrows(IllegalArgumentException.class, () -> new CliFailure("code", " ", null, null));
    assertThrows(
        IllegalArgumentException.class, () -> new CliFailure("code", "message", " ", null));
    assertThrows(
        IllegalArgumentException.class, () -> new CliFailure("code", "message", null, " "));
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
}

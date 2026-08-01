package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonValidationFixtures.accountPayload;
import static dev.erst.fingrind.cli.CliJsonValidationFixtures.taxRegistrationPayload;
import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliAccountRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliDeclareAccountPayload;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import org.junit.jupiter.api.Test;

/** Validates account and tax lifecycle JSON payload invariants. */
class CliAdministrativeJsonModelValidationTest {
  @Test
  void contraAndPayrollPayloads_preserveValidFactsAndRejectInvalidValues() {
    CliAccountRejectionJsonModels.ContraAccountDetails contra =
        new CliAccountRejectionJsonModels.ContraAccountDetails(
            "4090", "4000", "statement-taxonomy-mismatch");
    assertEquals("4090", contra.accountCode());
    assertEquals("4000", contra.contraOfAccountCode());
    assertEquals("statement-taxonomy-mismatch", contra.violation());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAccountRejectionJsonModels.ContraAccountDetails(" ", "4000", "target-missing"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliPostingEntryPayload.LatvianMonthlyPayrollPayload(
                "payroll-run-1",
                "employee-1",
                "2026-07",
                false,
                -1,
                "5000",
                "5010",
                "2200",
                "2210",
                "2220",
                "2230",
                new MonetaryAmount("EUR", "200000"),
                null));
  }

  @Test
  void administrativeMutationPayloads_keepExactLifecycleOutcomeAndAttestationCommitCoupled() {
    CliAttestationJsonModels.AttestationCommitPayload commit =
        new CliAttestationJsonModels.AttestationCommitPayload("12", "a".repeat(64));

    assertEquals(
        commit,
        new CliDeclareAccountPayload(
                CliDeclareAccountPayload.Outcome.DECLARED, accountPayload("1000", "Cash"), commit)
            .attestationCommit());
    assertEquals(
        null,
        new CliDeclareAccountPayload(
                CliDeclareAccountPayload.Outcome.UNCHANGED, accountPayload("1000", "Cash"), null)
            .attestationCommit());
    assertEquals(
        "attestationCommit",
        assertThrows(
                NullPointerException.class,
                () ->
                    new CliDeclareAccountPayload(
                        CliDeclareAccountPayload.Outcome.AMENDED,
                        accountPayload("1000", "Cash"),
                        null))
            .getMessage());
    assertEquals(
        "An unchanged account mutation must not report a newly appended attestation operation.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new CliDeclareAccountPayload(
                        CliDeclareAccountPayload.Outcome.UNCHANGED,
                        accountPayload("1000", "Cash"),
                        commit))
            .getMessage());

    assertEquals(
        commit,
        new CliTaxJsonModels.TaxRegistrationMutationPayload(
                CliTaxJsonModels.TaxRegistrationMutationOutcome.DECLARED,
                taxRegistrationPayload(),
                commit)
            .attestationCommit());
    assertEquals(
        null,
        new CliTaxJsonModels.TaxRegistrationMutationPayload(
                CliTaxJsonModels.TaxRegistrationMutationOutcome.UNCHANGED,
                taxRegistrationPayload(),
                null)
            .attestationCommit());
    assertEquals(
        "attestationCommit",
        assertThrows(
                NullPointerException.class,
                () ->
                    new CliTaxJsonModels.TaxRegistrationMutationPayload(
                        CliTaxJsonModels.TaxRegistrationMutationOutcome.UPDATED,
                        taxRegistrationPayload(),
                        null))
            .getMessage());
    assertEquals(
        "An unchanged tax registration must not report a newly appended attestation operation.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new CliTaxJsonModels.TaxRegistrationMutationPayload(
                        CliTaxJsonModels.TaxRegistrationMutationOutcome.UNCHANGED,
                        taxRegistrationPayload(),
                        commit))
            .getMessage());
  }

  @Test
  void parentAccountRejectionPayloads_validateRequiredFields() {
    CliAccountRejectionJsonModels.ParentAccountDetails parentAccountDetails =
        new CliAccountRejectionJsonModels.ParentAccountDetails("4100", "4000");
    CliAccountRejectionJsonModels.ParentAccountTypeConflictDetails
        parentAccountTypeConflictDetails =
            new CliAccountRejectionJsonModels.ParentAccountTypeConflictDetails(
                "4100", "EXPENSE", "4000", "REVENUE");
    CliAccountRejectionJsonModels.ParentAccountNodeKindDetails parentAccountNodeKindDetails =
        new CliAccountRejectionJsonModels.ParentAccountNodeKindDetails("4100", "4000", "POSTABLE");
    CliAccountRejectionJsonModels.ParentAccountTaxonomyConflictDetails
        parentAccountTaxonomyConflictDetails =
            new CliAccountRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
                "4100",
                new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
                    "POSTABLE", "4050", null, "OPERATING_EXPENSE"),
                "4000",
                new CliAccountRejectionJsonModels.AccountTaxonomyDetails(
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
        () -> new CliAccountRejectionJsonModels.ParentAccountDetails(" ", "4000"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAccountRejectionJsonModels.ParentAccountTypeConflictDetails(
                "4100", " ", "4000", "REVENUE"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAccountRejectionJsonModels.ParentAccountNodeKindDetails("4100", "4000", " "));
    assertThrows(
        NullPointerException.class,
        () ->
            new CliAccountRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
                "4100", nullOf(), "4000", nullOf()));
  }
}

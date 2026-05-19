package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ReportingObligationStatus;
import org.junit.jupiter.api.Test;

/** Coverage and invariant tests for contract-owned template descriptors. */
class ContractTemplatesValidationTest {
  @Test
  void ledgerPlanAndQueryTemplates_validateEmptyAndInRangeCases() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractTemplates.LedgerPlanTemplateDescriptor("plan-1", java.util.List.of()));
    ContractTemplates.LedgerPlanQueryTemplateDescriptor boundedQuery =
        new ContractTemplates.LedgerPlanQueryTemplateDescriptor(
            "1000", "2026-04-25", "2026-04-26", InteractionLimits.DEFAULT_PAGE_LIMIT, "cursor-1");
    ContractTemplates.LedgerPlanQueryTemplateDescriptor openQuery =
        new ContractTemplates.LedgerPlanQueryTemplateDescriptor(
            null, "2026-04-25", null, null, null);
    assertEquals("1000", boundedQuery.accountCode());
    assertEquals(InteractionLimits.DEFAULT_PAGE_LIMIT, boundedQuery.limit());
    assertEquals("cursor-1", boundedQuery.cursor());
    assertEquals("2026-04-25", openQuery.effectiveDateFrom());
    assertEquals(null, openQuery.limit());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerPlanQueryTemplateDescriptor(
                "1000", null, null, InteractionLimits.PAGE_LIMIT_MIN - 1, null));
  }

  @Test
  void declareAccountTemplateDescriptor_validatesOptionalParentAccountCodeWhenPresent() {
    ContractTemplates.DeclareAccountTemplateDescriptor template =
        new ContractTemplates.DeclareAccountTemplateDescriptor(
            "1000",
            "Cash",
            AccountType.ASSET,
            AccountRole.ORDINARY,
            "3000",
            FinancialPositionLineClassification.CURRENT_ASSET,
            null);

    assertEquals("3000", template.parentAccountCode());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.DeclareAccountTemplateDescriptor(
                "1000",
                "Cash",
                AccountType.ASSET,
                AccountRole.ORDINARY,
                "cash account",
                FinancialPositionLineClassification.CURRENT_ASSET,
                null));
  }

  @Test
  void ledgerPlanStepTemplates_coverEveryCanonicalShape() {
    assertDoesNotThrow(
        () -> {
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "open",
              LedgerStepKind.OPEN_BOOK,
              new ContractTemplates.OpenBookTemplateDescriptor(
                  "Acme Studio",
                  EntityForm.FREELANCER,
                  OwnerModel.SOLE_OWNER,
                  ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                  java.util.List.of("translation-services"),
                  "EUR",
                  "01-01",
                  AccountingBasis.ACCRUAL),
              null,
              null,
              null,
              null,
              null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "inspect", LedgerStepKind.INSPECT_BOOK, null, null, null, null, null, null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "preflight",
              LedgerStepKind.PREFLIGHT_ENTRY,
              null,
              postingTemplate(),
              null,
              null,
              null,
              null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "post", LedgerStepKind.POST_ENTRY, null, postingTemplate(), null, null, null, null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "declare",
              LedgerStepKind.DECLARE_ACCOUNT,
              null,
              null,
              new ContractTemplates.DeclareAccountTemplateDescriptor(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  AccountRole.ORDINARY,
                  null,
                  FinancialPositionLineClassification.CURRENT_ASSET,
                  null),
              null,
              null,
              null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "list-accounts", LedgerStepKind.LIST_ACCOUNTS, null, null, null, null, null, null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "list-postings",
              LedgerStepKind.LIST_POSTINGS,
              null,
              null,
              null,
              new ContractTemplates.LedgerPlanQueryTemplateDescriptor(
                  "1000", null, null, InteractionLimits.DEFAULT_PAGE_LIMIT, null),
              null,
              null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "balance",
              LedgerStepKind.ACCOUNT_BALANCE,
              null,
              null,
              null,
              new ContractTemplates.LedgerPlanQueryTemplateDescriptor(
                  "1000", null, null, null, null),
              null,
              null);
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "get-posting", LedgerStepKind.GET_POSTING, null, null, null, null, null, "posting-1");
          new ContractTemplates.LedgerPlanStepTemplateDescriptor(
              "assert",
              LedgerStepKind.ASSERT,
              null,
              null,
              null,
              null,
              new ContractTemplates.LedgerAssertionTemplateDescriptor(
                  LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
                  "1000",
                  null,
                  null,
                  new MonetaryAmount("EUR", "1000"),
                  BalanceSide.DEBIT,
                  null),
              null);
        });
  }

  @Test
  void ledgerPlanStepTemplates_rejectMissingRequiredAndImpossibleShapes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "broken-balance",
                LedgerStepKind.ACCOUNT_BALANCE,
                null,
                null,
                null,
                new ContractTemplates.LedgerPlanQueryTemplateDescriptor(
                    null, null, null, InteractionLimits.DEFAULT_PAGE_LIMIT, null),
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerPlanStepTemplateDescriptor(
                "missing-posting-id",
                LedgerStepKind.GET_POSTING,
                null,
                null,
                null,
                null,
                null,
                null));
    IllegalArgumentException zeroAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.JournalLineTemplateDescriptor(
                    "1000", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "0")));
    assertEquals("amount must carry one positive minor-unit value.", zeroAmount.getMessage());
  }

  @Test
  void ledgerAssertionTemplates_coverEveryCanonicalShape() {
    ContractTemplates.LedgerAssertionTemplateDescriptor accountDeclared =
        new ContractTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.ACCOUNT_DECLARED, "1000", null, null, null, null, null);
    ContractTemplates.LedgerAssertionTemplateDescriptor accountActive =
        new ContractTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.ACCOUNT_ACTIVE, "2000", null, null, null, null, null);
    ContractTemplates.LedgerAssertionTemplateDescriptor postingExists =
        new ContractTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.POSTING_EXISTS, null, null, null, null, null, "posting-1");
    ContractTemplates.LedgerAssertionTemplateDescriptor balanceEquals =
        new ContractTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
            "3000",
            null,
            null,
            new MonetaryAmount("EUR", "1000"),
            BalanceSide.CREDIT,
            null);
    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, accountDeclared.kind());
    assertEquals("2000", accountActive.accountCode());
    assertEquals("posting-1", postingExists.postingId());
    assertEquals(BalanceSide.CREDIT, balanceEquals.balanceSide());
  }

  @Test
  void ledgerAssertionTemplates_rejectMissingRequiredFields() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.LedgerAssertionTemplateDescriptor(
                LedgerAssertionKind.ACCOUNT_DECLARED, null, null, null, null, null, null));
  }

  @Test
  void shapeRequirementHelpers_reportMissingRuleRegistration() {
    IllegalStateException missingStepRule =
        assertThrows(
            IllegalStateException.class,
            () ->
                ContractTemplateShapeValidator.stepRequirements(
                    java.util.Map.of(
                        LedgerStepKind.OPEN_BOOK,
                        new ContractTemplateStepShapeRequirements(
                            ContractTemplateFieldPresence.REQUIRED,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            false)),
                    LedgerStepKind.POST_ENTRY));
    assertEquals(
        "No step-shape requirements are registered for ledger step kind POST_ENTRY.",
        missingStepRule.getMessage());

    IllegalStateException missingAssertionRule =
        assertThrows(
            IllegalStateException.class,
            () ->
                ContractTemplateShapeValidator.assertionRequirements(
                    java.util.Map.of(
                        LedgerAssertionKind.ACCOUNT_DECLARED,
                        new ContractTemplateAssertionShapeRequirements(
                            ContractTemplateFieldPresence.REQUIRED,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN,
                            ContractTemplateFieldPresence.FORBIDDEN)),
                    LedgerAssertionKind.POSTING_EXISTS));
    assertEquals(
        "No assertion-shape requirements are registered for ledger assertion kind POSTING_EXISTS.",
        missingAssertionRule.getMessage());
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor postingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        dev.erst.fingrind.core.PostingKind.STANDARD,
        "2026-04-25",
        java.util.List.of(
            new ContractTemplates.JournalLineTemplateDescriptor(
                "1000", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
            new ContractTemplates.JournalLineTemplateDescriptor(
                "2000", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000"))),
        new ContractTemplates.ProvenanceTemplateDescriptor(
            "actor-1", ActorType.HUMAN, "command-1", "idem-1", "cause-1", null),
        null);
  }
}

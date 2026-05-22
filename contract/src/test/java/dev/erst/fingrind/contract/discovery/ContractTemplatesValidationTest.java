package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingKind;
import org.junit.jupiter.api.Test;

/** Coverage and invariant tests for contract-owned template descriptors. */
class ContractTemplatesValidationTest {
  private static final String DOCUMENT_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

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
            AccountNodeKind.POSTABLE,
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
                AccountNodeKind.POSTABLE,
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
                  java.util.List.of("translation-services"),
                  "EUR",
                  "01-01",
                  AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1),
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
                  AccountNodeKind.POSTABLE,
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
  void evidenceAndApprovalTemplates_validateCanonicalShapes() {
    ContractTemplates.ApprovalTemplateDescriptor approval = approvalTemplate();
    ContractTemplates.AccountingEvidenceTemplateDescriptor evidence =
        evidenceTemplate(java.util.List.of(approval));

    assertEquals("approval-1", approval.approvalId());
    assertEquals("manager-signoff", approval.approvalType());
    assertEquals("manager-1", approval.approverId());
    assertEquals(ApprovalDecision.APPROVED, approval.decision());
    assertEquals(1, evidence.sourceDocuments().size());
    assertEquals(1, evidence.approvals().size());
  }

  @Test
  void evidenceAndApprovalTemplates_rejectInvalidEvidencePayloads() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.ApprovalTemplateDescriptor(
                "approval 1",
                "manager-signoff",
                "manager-1",
                ActorType.HUMAN,
                ApprovalDecision.APPROVED,
                "2026-04-25T10:15:30Z"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.ApprovalTemplateDescriptor(
                "approval-1",
                "manager signoff",
                "manager-1",
                ActorType.HUMAN,
                ApprovalDecision.APPROVED,
                "2026-04-25T10:15:30Z"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.AccountingEvidenceTemplateDescriptor(
                java.util.List.of(), java.util.List.of()));
  }

  @Test
  void postingRequestTemplates_coverEveryCanonicalEntryShape() {
    ContractTemplates.PostingRequestTemplateDescriptor cashRevenue = postingTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor cashExpense =
        new ContractTemplates.PostingRequestTemplateDescriptor(
            BookkeepingEntryKind.CASH_EXPENSE,
            "2026-04-25",
            "1000",
            null,
            "5000",
            null,
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            evidenceTemplate(java.util.List.of()),
            provenanceTemplate(),
            null);
    ContractTemplates.PostingRequestTemplateDescriptor ownerContribution =
        new ContractTemplates.PostingRequestTemplateDescriptor(
            BookkeepingEntryKind.OWNER_CONTRIBUTION,
            "2026-04-25",
            "1000",
            null,
            null,
            "3000",
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            evidenceTemplate(java.util.List.of()),
            provenanceTemplate(),
            null);
    ContractTemplates.PostingRequestTemplateDescriptor ownerDraw =
        new ContractTemplates.PostingRequestTemplateDescriptor(
            BookkeepingEntryKind.OWNER_DRAW,
            "2026-04-25",
            "1000",
            null,
            null,
            "3010",
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            evidenceTemplate(java.util.List.of()),
            provenanceTemplate(),
            null);
    ContractTemplates.PostingRequestTemplateDescriptor manualAdjustment =
        new ContractTemplates.PostingRequestTemplateDescriptor(
            BookkeepingEntryKind.MANUAL_ADJUSTMENT,
            "2026-04-25",
            null,
            null,
            null,
            null,
            null,
            PostingKind.STANDARD,
            java.util.List.of(
                journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
            evidenceTemplate(java.util.List.of()),
            provenanceTemplate(),
            null);

    assertEquals(BookkeepingEntryKind.CASH_REVENUE, cashRevenue.entryKind());
    assertEquals(BookkeepingEntryKind.CASH_EXPENSE, cashExpense.entryKind());
    assertEquals("5000", cashExpense.expenseAccountCode());
    assertEquals(BookkeepingEntryKind.OWNER_CONTRIBUTION, ownerContribution.entryKind());
    assertEquals("3000", ownerContribution.equityAccountCode());
    assertEquals(BookkeepingEntryKind.OWNER_DRAW, ownerDraw.entryKind());
    assertEquals("3010", ownerDraw.equityAccountCode());
    assertEquals(BookkeepingEntryKind.MANUAL_ADJUSTMENT, manualAdjustment.entryKind());
    assertEquals(PostingKind.STANDARD, manualAdjustment.postingKind());
    assertEquals(2, java.util.Objects.requireNonNull(manualAdjustment.lines()).size());
  }

  @Test
  void postingRequestTemplates_rejectShapeViolationsForTypedEventsAndManualAdjustments() {
    IllegalArgumentException missingCashAccount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.CASH_REVENUE,
                    "2026-04-25",
                    null,
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("cashAccountCode must not be null.", missingCashAccount.getMessage());

    IllegalArgumentException forbiddenRevenueAccount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.CASH_EXPENSE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    "5000",
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "revenueAccountCode must be absent for this entryKind.",
        forbiddenRevenueAccount.getMessage());

    IllegalArgumentException missingPositiveAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OWNER_CONTRIBUTION,
                    "2026-04-25",
                    "1000",
                    null,
                    null,
                    "3000",
                    null,
                    null,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("amount must not be null.", missingPositiveAmount.getMessage());

    IllegalArgumentException zeroAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OWNER_DRAW,
                    "2026-04-25",
                    "1000",
                    null,
                    null,
                    "3010",
                    new MonetaryAmount("EUR", "0"),
                    null,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("amount must carry one positive minor-unit value.", zeroAmount.getMessage());

    IllegalArgumentException forbiddenPostingKind =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.CASH_REVENUE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    PostingKind.STANDARD,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "postingKind must be absent for typed business events.", forbiddenPostingKind.getMessage());

    IllegalArgumentException forbiddenLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.CASH_REVENUE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    java.util.List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000")),
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("lines must be absent for typed business events.", forbiddenLines.getMessage());

    IllegalArgumentException forbiddenReversal =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.CASH_REVENUE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    new ContractTemplates.ReversalTemplateDescriptor(
                        "posting-1", "manual-correction")));
    assertEquals(
        "reversal must be absent for typed business events.", forbiddenReversal.getMessage());

    IllegalArgumentException generatedPostingKind =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.MANUAL_ADJUSTMENT,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    PostingKind.PERIOD_CLOSE,
                    java.util.List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "postingKind must belong to the caller-authored manual-adjustment surface.",
        generatedPostingKind.getMessage());

    IllegalArgumentException missingManualPostingKind =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.MANUAL_ADJUSTMENT,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    java.util.List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "postingKind must belong to the caller-authored manual-adjustment surface.",
        missingManualPostingKind.getMessage());

    IllegalArgumentException tooFewLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.MANUAL_ADJUSTMENT,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    PostingKind.OPENING_BALANCE,
                    java.util.List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000")),
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "lines must contain at least two journal lines for manualAdjustment.",
        tooFewLines.getMessage());

    IllegalArgumentException missingManualLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.MANUAL_ADJUSTMENT,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    PostingKind.STANDARD,
                    null,
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "lines must contain at least two journal lines for manualAdjustment.",
        missingManualLines.getMessage());

    IllegalArgumentException forbiddenManualAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.MANUAL_ADJUSTMENT,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    PostingKind.STANDARD,
                    java.util.List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    evidenceTemplate(java.util.List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("amount must be absent for manualAdjustment.", forbiddenManualAmount.getMessage());
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
        BookkeepingEntryKind.CASH_REVENUE,
        "2026-04-25",
        "1000",
        "4000",
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        evidenceTemplate(java.util.List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.ProvenanceTemplateDescriptor provenanceTemplate() {
    return new ContractTemplates.ProvenanceTemplateDescriptor(
        "actor-1", ActorType.HUMAN, "command-1", "idem-1", "cause-1", null);
  }

  private static ContractTemplates.JournalLineTemplateDescriptor journalLineTemplate(
      String accountCode, JournalLine.EntrySide side, String minorUnits) {
    return new ContractTemplates.JournalLineTemplateDescriptor(
        accountCode, side, new MonetaryAmount("EUR", minorUnits));
  }

  private static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate(
      java.util.List<ContractTemplates.ApprovalTemplateDescriptor> approvals) {
    return new ContractTemplates.AccountingEvidenceTemplateDescriptor(
        java.util.List.of(sourceDocumentTemplate()), approvals);
  }

  private static ContractTemplates.SourceDocumentTemplateDescriptor sourceDocumentTemplate() {
    return new ContractTemplates.SourceDocumentTemplateDescriptor(
        "document-idem-1",
        "invoice",
        "2026-04-25",
        "2026-04-25T10:15:30Z",
        "evidence://documents/document-idem-1.pdf",
        DOCUMENT_SHA256);
  }

  private static ContractTemplates.ApprovalTemplateDescriptor approvalTemplate() {
    return new ContractTemplates.ApprovalTemplateDescriptor(
        "approval-1",
        "manager-signoff",
        "manager-1",
        ActorType.HUMAN,
        ApprovalDecision.APPROVED,
        "2026-04-25T10:15:30Z");
  }
}

package dev.erst.fingrind.contract.discovery;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage and invariant tests for contract-owned template descriptors. */
class ContractTemplatesValidationTest {
  @Test
  void ledgerPlanAndQueryTemplates_validateEmptyAndInRangeCases() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPlanTemplates.LedgerPlanTemplateDescriptor("plan-1", java.util.List.of()));
    ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor boundedQuery =
        new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
            "1000",
            "2026-04-25",
            "2026-04-26",
            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
            "cursor-1");
    ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor openQuery =
        new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
            null, "2026-04-25", null, null, null);
    assertEquals("1000", boundedQuery.accountCode());
    assertEquals(ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT, boundedQuery.limit());
    assertEquals("cursor-1", boundedQuery.cursor());
    assertEquals("2026-04-25", openQuery.effectiveDateFrom());
    assertEquals(null, openQuery.limit());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
                "1000", null, null, ProtocolInteractionLimits.PAGE_LIMIT_MIN - 1, null));
  }

  @Test
  void declareAccountTemplateDescriptor_validatesOptionalParentAccountCodeWhenPresent() {
    ContractTemplates.DeclareAccountTemplateDescriptor template =
        new ContractTemplates.DeclareAccountTemplateDescriptor(
            "1000",
            "Cash",
            AccountType.ASSET,
            AccountNodeKind.POSTABLE,
            "3000",
            FinancialPositionLineClassification.CURRENT_ASSET,
            null,
            null);

    assertEquals("3000", template.parentAccountCode());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.DeclareAccountTemplateDescriptor(
                "1000",
                "Cash",
                AccountType.ASSET,
                AccountNodeKind.POSTABLE,
                "cash account",
                FinancialPositionLineClassification.CURRENT_ASSET,
                null,
                null));
  }

  @Test
  void ledgerPlanStepTemplates_coverEveryCanonicalShape() {
    assertDoesNotThrow(
        () -> {
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "open",
              LedgerStepKind.ENSURE_BOOK,
              new ContractPlanTemplates.EnsureBookTemplateDescriptor("Acme Studio", "EUR", "01-01"),
              null,
              null,
              null,
              null,
              null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "inspect", LedgerStepKind.INSPECT_BOOK, null, null, null, null, null, null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "preflight",
              LedgerStepKind.PREFLIGHT_ENTRY,
              null,
              postingTemplate(),
              null,
              null,
              null,
              null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "post", LedgerStepKind.POST_ENTRY, null, postingTemplate(), null, null, null, null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "declare",
              LedgerStepKind.DECLARE_ACCOUNT,
              null,
              null,
              new ContractTemplates.DeclareAccountTemplateDescriptor(
                  "1000",
                  "Cash",
                  AccountType.ASSET,
                  AccountNodeKind.POSTABLE,
                  null,
                  FinancialPositionLineClassification.CURRENT_ASSET,
                  null,
                  null),
              null,
              null,
              null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "list-accounts", LedgerStepKind.LIST_ACCOUNTS, null, null, null, null, null, null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "list-postings",
              LedgerStepKind.LIST_POSTINGS,
              null,
              null,
              null,
              new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
                  "1000", null, null, ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT, null),
              null,
              null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "balance",
              LedgerStepKind.ACCOUNT_BALANCE,
              null,
              null,
              null,
              new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
                  "1000", null, null, null, null),
              null,
              null);
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "get-posting", LedgerStepKind.GET_POSTING, null, null, null, null, null, "posting-1");
          new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
              "assert",
              LedgerStepKind.ASSERT,
              null,
              null,
              null,
              null,
              new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
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
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
                "broken-balance",
                LedgerStepKind.ACCOUNT_BALANCE,
                null,
                null,
                null,
                new ContractPlanTemplates.LedgerPlanQueryTemplateDescriptor(
                    null, null, null, ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT, null),
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractPlanTemplates.LedgerPlanStepTemplateDescriptor(
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
    ContractPlanTemplates.LedgerAssertionTemplateDescriptor accountDeclared =
        new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.ACCOUNT_DECLARED, "1000", null, null, null, null, null);
    ContractPlanTemplates.LedgerAssertionTemplateDescriptor accountActive =
        new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.ACCOUNT_ACTIVE, "2000", null, null, null, null, null);
    ContractPlanTemplates.LedgerAssertionTemplateDescriptor postingExists =
        new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
            LedgerAssertionKind.POSTING_EXISTS, null, null, null, null, null, "posting-1");
    ContractPlanTemplates.LedgerAssertionTemplateDescriptor balanceEquals =
        new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
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
            new ContractPlanTemplates.LedgerAssertionTemplateDescriptor(
                LedgerAssertionKind.ACCOUNT_DECLARED, null, null, null, null, null, null));
  }

  @Test
  void evidenceAndApprovalTemplates_validateCanonicalShapes() {
    ContractTemplates.ApprovalTemplateDescriptor approval = approvalTemplate();
    ContractTemplates.AccountingEvidenceTemplateDescriptor evidence =
        evidenceTemplate(List.of(approval));

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
                ActorType.PERSON,
                ApprovalDecision.APPROVED,
                "2026-04-25T10:15:30Z"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractTemplates.ApprovalTemplateDescriptor(
                "approval-1",
                "manager signoff",
                "manager-1",
                ActorType.PERSON,
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
    ContractTemplates.PostingRequestTemplateDescriptor sale = postingTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor expense = expensePostingTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor ownerContribution =
        ownerContributionPostingTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor ownerWithdrawal =
        ownerWithdrawalPostingTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor openingPosition =
        openingPositionPostingTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor reversal = reversalPostingTemplate();

    assertEquals(BookkeepingEntryKind.SALE, sale.entryKind());
    assertEquals("4000", sale.revenueAccountCode());
    assertEquals(BookkeepingEntryKind.EXPENSE, expense.entryKind());
    assertEquals("5000", expense.expenseAccountCode());
    assertEquals(BookkeepingEntryKind.OWNER_CONTRIBUTION, ownerContribution.entryKind());
    assertEquals("3000", ownerContribution.equityAccountCode());
    assertEquals(BookkeepingEntryKind.OWNER_WITHDRAWAL, ownerWithdrawal.entryKind());
    assertEquals("3010", ownerWithdrawal.equityAccountCode());
    assertEquals(BookkeepingEntryKind.OPENING_POSITION, openingPosition.entryKind());
    assertEquals(2, Objects.requireNonNull(openingPosition.openingBalances()).size());
    assertEquals(BookkeepingEntryKind.REVERSAL, reversal.entryKind());
    assertEquals("posting-1", Objects.requireNonNull(reversal.reversal()).priorPostingId());
  }

  @Test
  void postingRequestTemplates_acceptDirectJournalShapesWithoutTypedEventFields() {
    ContractTemplates.PostingRequestTemplateDescriptor directJournal =
        directJournalPostingTemplate();

    assertEquals(BookkeepingEntryKind.DIRECT_JOURNAL, directJournal.entryKind());
    assertEquals(2, Objects.requireNonNull(directJournal.lines()).size());
  }

  @Test
  void postingRequestTemplates_rejectDirectJournalWithTooFewLines() {
    IllegalArgumentException tooFewLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.DIRECT_JOURNAL,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000")),
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));

    assertEquals(
        "lines must contain at least two journal lines for journal.", tooFewLines.getMessage());
  }

  @Test
  void postingRequestTemplates_rejectTypedEventShapeViolations() {
    IllegalArgumentException missingCashAccount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.SALE,
                    "2026-04-25",
                    null,
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    null,
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("cashAccountCode must not be null.", missingCashAccount.getMessage());

    IllegalArgumentException forbiddenRevenueAccount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.EXPENSE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    "5000",
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    null,
                    null,
                    evidenceTemplate(List.of()),
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
                    null,
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("amount must not be null.", missingPositiveAmount.getMessage());

    IllegalArgumentException zeroAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OWNER_WITHDRAWAL,
                    "2026-04-25",
                    "1000",
                    null,
                    null,
                    "3010",
                    new MonetaryAmount("EUR", "0"),
                    null,
                    null,
                    null,
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("amount must carry one positive minor-unit value.", zeroAmount.getMessage());

    IllegalArgumentException forbiddenLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.SALE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    List.of(journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000")),
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("lines must be absent for this entryKind.", forbiddenLines.getMessage());

    IllegalArgumentException forbiddenReversal =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.SALE,
                    "2026-04-25",
                    "1000",
                    "4000",
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    null,
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    new ContractTemplates.ReversalTemplateDescriptor(
                        "posting-1", "manual-correction")));
    assertEquals("reversal must be absent for this entryKind.", forbiddenReversal.getMessage());
  }

  @Test
  void postingRequestTemplates_rejectOpeningPositionShapeViolations() {
    IllegalArgumentException tooFewOpeningBalanceLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OPENING_POSITION,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(openingBalanceTemplate("1000", JournalLine.EntrySide.DEBIT, "1000")),
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "openingBalances must contain at least two opening balances for openingPosition.",
        tooFewOpeningBalanceLines.getMessage());

    IllegalArgumentException missingOpeningBalances =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OPENING_POSITION,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "openingBalances must contain at least two opening balances for openingPosition.",
        missingOpeningBalances.getMessage());

    IllegalArgumentException openingPositionForbidsAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OPENING_POSITION,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    null,
                    List.of(
                        openingBalanceTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        openingBalanceTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "amount must be absent for openingPosition.", openingPositionForbidsAmount.getMessage());

    IllegalArgumentException openingPositionForbidsLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.OPENING_POSITION,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    List.of(
                        openingBalanceTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        openingBalanceTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals(
        "lines must be absent for this entryKind.", openingPositionForbidsLines.getMessage());
  }

  @Test
  void postingRequestTemplates_rejectReversalShapeViolations() {
    IllegalArgumentException missingReversalDescriptor =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.REVERSAL,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    null));
    assertEquals("reversal must be present for reversal.", missingReversalDescriptor.getMessage());

    IllegalArgumentException reversalRequiresLines =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.REVERSAL,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    new ContractTemplates.ReversalTemplateDescriptor(
                        "posting-1", "manual-correction")));
    assertEquals(
        "lines must contain at least two journal lines for reversal.",
        reversalRequiresLines.getMessage());

    IllegalArgumentException reversalForbidsOpeningBalances =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.REVERSAL,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    List.of(
                        openingBalanceTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        openingBalanceTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    new ContractTemplates.ReversalTemplateDescriptor(
                        "posting-1", "manual-correction")));
    assertEquals(
        "openingBalances must be absent for this entryKind.",
        reversalForbidsOpeningBalances.getMessage());

    IllegalArgumentException forbiddenManualAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.PostingRequestTemplateDescriptor(
                    BookkeepingEntryKind.REVERSAL,
                    "2026-04-25",
                    null,
                    null,
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    List.of(
                        journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                        journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                    null,
                    evidenceTemplate(List.of()),
                    provenanceTemplate(),
                    new ContractTemplates.ReversalTemplateDescriptor(
                        "posting-1", "manual-correction")));
    assertEquals("amount must be absent for reversal.", forbiddenManualAmount.getMessage());
  }

  @Test
  void postingRequestTemplateValidator_rejectsNullEntryKindWhenCalledDirectly() {
    ContractPostingRequestTemplateValidators.PostingTemplateFields fields =
        new ContractPostingRequestTemplateValidators.PostingTemplateFields(
            null, null, null, null, null, null, null, null, null);

    assertThrows(
        NullPointerException.class,
        () -> ContractPostingRequestTemplateValidators.validate(nullOf(), fields, null));
  }

  @Test
  void openingBalanceTemplates_rejectNonPositiveAmounts() {
    IllegalArgumentException zeroOpeningBalance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.OpeningBalanceTemplateDescriptor(
                    "1000", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "0")));

    assertEquals(
        "amount must carry one positive minor-unit value.", zeroOpeningBalance.getMessage());
  }

  @Test
  void shapeRequirementHelpers_reportMissingRuleRegistration() {
    IllegalStateException missingStepRule =
        assertThrows(
            IllegalStateException.class,
            () ->
                ContractTemplateShapeValidator.stepRequirements(
                    java.util.Map.of(
                        LedgerStepKind.ENSURE_BOOK,
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
        BookkeepingEntryKind.SALE,
        "2026-04-25",
        "1000",
        "4000",
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor expensePostingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.EXPENSE,
        "2026-04-25",
        "1000",
        null,
        "5000",
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor
      ownerContributionPostingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        "2026-04-25",
        "1000",
        null,
        null,
        "3000",
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor
      ownerWithdrawalPostingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        "2026-04-25",
        "1000",
        null,
        null,
        "3010",
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor
      openingPositionPostingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OPENING_POSITION,
        "2026-04-25",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            openingBalanceTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
            openingBalanceTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor reversalPostingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.REVERSAL,
        "2026-04-25",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
            journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
        null,
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        new ContractTemplates.ReversalTemplateDescriptor("posting-1", "operator reversal"));
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor directJournalPostingTemplate() {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        "2026-04-25",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            journalLineTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
            journalLineTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
        null,
        evidenceTemplate(List.of()),
        provenanceTemplate(),
        null);
  }

  private static ContractTemplates.ProvenanceTemplateDescriptor provenanceTemplate() {
    return new ContractTemplates.ProvenanceTemplateDescriptor(
        "actor-1", ActorType.PERSON, "command-1", "idem-1", "cause-1", null);
  }

  private static ContractTemplates.JournalLineTemplateDescriptor journalLineTemplate(
      String accountCode, JournalLine.EntrySide side, String minorUnits) {
    return new ContractTemplates.JournalLineTemplateDescriptor(
        accountCode, side, new MonetaryAmount("EUR", minorUnits));
  }

  private static ContractTemplates.OpeningBalanceTemplateDescriptor openingBalanceTemplate(
      String accountCode, JournalLine.EntrySide side, String minorUnits) {
    return new ContractTemplates.OpeningBalanceTemplateDescriptor(
        accountCode, side, new MonetaryAmount("EUR", minorUnits));
  }

  private static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate(
      List<ContractTemplates.ApprovalTemplateDescriptor> approvals) {
    return new ContractTemplates.AccountingEvidenceTemplateDescriptor(
        List.of(sourceDocumentTemplate()), approvals);
  }

  private static ContractTemplates.SourceDocumentTemplateDescriptor sourceDocumentTemplate() {
    return new ContractTemplates.SourceDocumentTemplateDescriptor(
        "document-idem-1", "cash-receipt", "2026-04-25");
  }

  private static ContractTemplates.ApprovalTemplateDescriptor approvalTemplate() {
    return new ContractTemplates.ApprovalTemplateDescriptor(
        "approval-1",
        "manager-signoff",
        "manager-1",
        ActorType.PERSON,
        ApprovalDecision.APPROVED,
        "2026-04-25T10:15:30Z");
  }
}

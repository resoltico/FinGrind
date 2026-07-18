package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractSettlementTemplates;
import dev.erst.fingrind.contract.discovery.ForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.QuotedExchangeRateTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for posting-model help helpers and their descriptor guardrails. */
class CliDiscoveryPostingModelGuidanceTest extends CliDiscoveryHelpTextTestSupport {
  private static final MethodHandle TEMPLATE_PUBLISHES_FIELD = templatePublishesFieldHandle();

  @Test
  void payrollPostingModelExplainsItsAdmittedProfileBeforeSubmission() {
    HelpDescriptor payrollHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(payrollHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor payrollTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL));

    String rendered =
        CliDiscoveryPostingModelGuidance.renderPostingModel(postingModel, payrollTemplate)
            .replaceAll("\\s+", " ");

    assertTrue(rendered.contains("taxBookHeldAtEmployer"), rendered);
    assertTrue(rendered.contains("current profile refuses false"), rendered);
    assertTrue(rendered.contains("dependantCount"), rendered);
    assertTrue(rendered.contains("current profile admits only 0"), rendered);
  }

  @Test
  void includesCanonicalTopLevelField_coversOwnedAndUnownedPublishedFamilies() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor directJournalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY));
    ContractRequestShapes.EntryKindSemanticsDescriptor directJournalSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, directJournalTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));
    ContractRequestShapes.EntryKindSemanticsDescriptor saleSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, saleTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor creditSaleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_ON_CREDIT));
    ContractRequestShapes.EntryKindSemanticsDescriptor creditSaleSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, creditSaleTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor expenseTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_EXPENSE_SETTLED));
    ContractRequestShapes.EntryKindSemanticsDescriptor expenseSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, expenseTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor contributionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OWNER_CONTRIBUTION));
    ContractRequestShapes.EntryKindSemanticsDescriptor contributionSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, contributionTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor openingPositionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION));
    ContractRequestShapes.EntryKindSemanticsDescriptor openingPositionSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(
            postingModel, openingPositionTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor reversalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_REVERSAL));
    ContractRequestShapes.EntryKindSemanticsDescriptor reversalSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, reversalTemplate);

    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.ENTRY_KIND,
            directJournalTemplate,
            directJournalSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
            directJournalTemplate,
            directJournalSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.LINES, directJournalTemplate, directJournalSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.AMOUNT, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
            creditSaleTemplate,
            creditSaleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.TAX, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.EVIDENCE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.PROVENANCE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
            expenseTemplate,
            expenseSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE,
            contributionTemplate,
            contributionSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.OPENING_BALANCES,
            openingPositionTemplate,
            openingPositionSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.REVERSAL, reversalTemplate, reversalSemantics));
    assertFalse(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Core.TAX, directJournalTemplate, directJournalSemantics));
    assertFalse(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
            expenseTemplate,
            expenseSemantics));
    assertFalse(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            "foreignField", saleTemplate, saleSemantics));
  }

  @Test
  void templatePublishesField_coversEverySwitchArm() {
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor directJournalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
        saleTemplateWithOwnedNestedFacts = saleTemplateWithOwnedNestedFacts(saleTemplate);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor expenseTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_EXPENSE_SETTLED));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor purchaseSettledTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_PURCHASE_SETTLED));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor contributionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OWNER_CONTRIBUTION));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor openingPositionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor reversalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_REVERSAL));

    assertTrue(
        templatePublishesField(ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE, saleTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE, directJournalTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE, saleTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE, expenseTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE, expenseTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE, contributionTemplate));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE, saleTemplate));
    assertTrue(templatePublishesField(ProtocolBusinessEventFields.Core.AMOUNT, saleTemplate));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Core.AMOUNT, directJournalTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Inventory.QUANTITY, purchaseSettledTemplate));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Inventory.QUANTITY, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Inventory.UNIT_COST, purchaseSettledTemplate));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Inventory.UNIT_COST, saleTemplate));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE, saleTemplateWithOwnedNestedFacts));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE, openingPositionTemplate));
    assertTrue(templatePublishesField(ProtocolBusinessEventFields.Core.TAX, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.TAX, saleTemplateWithOwnedNestedFacts));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Core.TAX, directJournalTemplate));
    assertTrue(
        templatePublishesField(ProtocolBusinessEventFields.Core.LINES, directJournalTemplate));
    assertFalse(templatePublishesField(ProtocolBusinessEventFields.Core.LINES, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.Core.OPENING_BALANCES, openingPositionTemplate));
    assertFalse(
        templatePublishesField(ProtocolBusinessEventFields.Core.OPENING_BALANCES, saleTemplate));
    assertTrue(templatePublishesField(ProtocolBusinessEventFields.Core.EVIDENCE, saleTemplate));
    assertTrue(templatePublishesField(ProtocolBusinessEventFields.Core.PROVENANCE, saleTemplate));
    assertTrue(templatePublishesField(ProtocolBusinessEventFields.Core.REVERSAL, reversalTemplate));
    assertFalse(templatePublishesField(ProtocolBusinessEventFields.Core.REVERSAL, saleTemplate));
    assertFalse(templatePublishesField("foreignField", saleTemplate));
  }

  @Test
  void templatePublishesField_coversAccrualCutoffFields() {
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor prepaymentTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_PREPAYMENT));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor deferredRevenueTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_DEFERRED_REVENUE));
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor accruedExpenseTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_ACCRUED_EXPENSE));

    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID, prepaymentTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL, prepaymentTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL,
            accruedExpenseTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE,
            prepaymentTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE,
            deferredRevenueTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
            accruedExpenseTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
            saleTemplate));
  }

  @Test
  void postingModelRows_threeArgumentOverloadPublishesPrefixedAllRows() {
    HelpDescriptor postEntryHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.POST_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        Objects.requireNonNull(
            Objects.requireNonNull(postEntryHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate =
        Objects.requireNonNull(postEntryHelp.requestTemplate());

    List<List<String>> rows =
        CliDiscoveryPostingModelGuidance.postingModelRows(
            postEntryShape, postingTemplate, "steps[].posting.");

    assertTrue(
        rows.stream().anyMatch(row -> "steps[].posting.entryKind".equals(row.getFirst())),
        rows.toString());
    assertTrue(
        rows.stream().anyMatch(row -> "steps[].posting.lines[].accountCode".equals(row.getFirst())),
        rows.toString());
  }

  @Test
  void postingModelRows_canonicalOverloadPublishesLinesTaxOpeningBalancesAndReversalFamilies() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());

    List<String> directJournalLabels =
        CliDiscoveryPostingModelGuidance.postingModelRows(
                postingModel,
                Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY)),
                "",
                true)
            .stream()
            .map(List::getFirst)
            .toList();
    List<String> saleLabels =
        CliDiscoveryPostingModelGuidance.postingModelRows(
                postingModel,
                Objects.requireNonNull(
                    MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED)),
                "",
                true)
            .stream()
            .map(List::getFirst)
            .toList();
    List<String> openingPositionLabels =
        CliDiscoveryPostingModelGuidance.postingModelRows(
                postingModel,
                Objects.requireNonNull(
                    MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION)),
                "",
                true)
            .stream()
            .map(List::getFirst)
            .toList();
    List<String> reversalLabels =
        CliDiscoveryPostingModelGuidance.postingModelRows(
                postingModel,
                Objects.requireNonNull(
                    MachineContract.requestTemplate(OperationId.RECORD_REVERSAL)),
                "",
                true)
            .stream()
            .map(List::getFirst)
            .toList();

    assertTrue(directJournalLabels.contains("lines[].accountCode"), directJournalLabels.toString());
    assertTrue(saleLabels.contains("cashAccountCode"), saleLabels.toString());
    assertTrue(saleLabels.contains("revenueAccountCode"), saleLabels.toString());
    assertTrue(saleLabels.contains("foreignExchange.transactionAmount"), saleLabels.toString());
    assertTrue(saleLabels.contains("tax.taxRegistrationId"), saleLabels.toString());
    assertTrue(
        openingPositionLabels.contains("openingBalances[].accountCode"),
        openingPositionLabels.toString());
    assertTrue(reversalLabels.contains("reversal.priorPostingId"), reversalLabels.toString());
  }

  @Test
  void postingModelRows_scopedCanonicalOverloadOmitsNonselectedPublishedFamilies() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));

    List<String> labels =
        CliDiscoveryPostingModelGuidance.postingModelRows(postingModel, saleTemplate, "", true)
            .stream()
            .map(List::getFirst)
            .toList();

    assertTrue(labels.contains("cashAccountCode"), labels.toString());
    assertTrue(labels.contains("revenueAccountCode"), labels.toString());
    assertFalse(labels.contains("expenseAccountCode"), labels.toString());
    assertFalse(labels.contains("equityAccountCode"), labels.toString());
    assertFalse(labels.contains("lines[].accountCode"), labels.toString());
  }

  @Test
  void
      postingModelRows_scopedCanonicalOverloadSkipsEvidenceAndProvenanceWhenSemanticsDoNotOwnThem() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor strippedPostingModel =
        postingModelWithoutOwnedFieldGroups(
            postingModel,
            saleTemplate,
            ProtocolBusinessEventFields.Core.EVIDENCE,
            ProtocolBusinessEventFields.Core.PROVENANCE);

    List<String> labels =
        CliDiscoveryPostingModelGuidance.postingModelRows(
                strippedPostingModel, saleTemplate, "", true)
            .stream()
            .map(List::getFirst)
            .toList();

    assertFalse(labels.contains("evidence"), labels.toString());
    assertFalse(labels.contains("evidence.sourceDocuments[].sourceDocumentId"), labels.toString());
    assertFalse(labels.contains("provenance.actorId"), labels.toString());
  }

  @Test
  void supplementalPostingModelRows_publishOnlyNoncanonicalLedgerPostingFamilies() {
    HelpDescriptor executePlanHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.EXECUTE_PLAN);
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape =
        Objects.requireNonNull(
            Objects.requireNonNull(executePlanHelp.requestShapes()).ledgerPlan());
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        postingModelWithForbiddenFields(ledgerPlanShape.postingModel());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));

    List<String> labels =
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                postingModel, postingTemplate, "steps[].posting.")
            .stream()
            .map(List::getFirst)
            .toList();

    assertTrue(labels.contains("steps[].posting.expenseAccountCode"), labels.toString());
    assertTrue(labels.contains("steps[].posting.equityAccountCode"), labels.toString());
    assertTrue(labels.contains("steps[].posting.lines[].accountCode"), labels.toString());
    assertTrue(labels.contains("steps[].posting.openingBalances[].accountCode"), labels.toString());
    assertTrue(labels.contains("steps[].posting.reversal.priorPostingId"), labels.toString());
    assertFalse(labels.contains("steps[].posting.cashAccountCode"), labels.toString());
    assertFalse(labels.contains("steps[].posting.revenueAccountCode"), labels.toString());
    assertFalse(labels.contains("steps[].posting.amount"), labels.toString());
    assertFalse(labels.contains("steps[].posting.evidence.sourceDocuments[].sourceDocumentType"));
    assertFalse(labels.contains("steps[].posting.forbiddenTopLevel"), labels.toString());
    assertFalse(labels.contains("steps[].posting.lines[].forbiddenLine"), labels.toString());
    assertTrue(labels.contains("steps[].posting.recognitionInterval.startDate"), labels.toString());
  }

  @Test
  void supplementalPostingModelRows_omitGroupsAlreadyOwnedBySelectedPostingFamily() {
    HelpDescriptor executePlanHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.EXECUTE_PLAN);
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape =
        Objects.requireNonNull(
            Objects.requireNonNull(executePlanHelp.requestShapes()).ledgerPlan());

    List<String> directJournalLabels =
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                ledgerPlanShape.postingModel(),
                Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY)),
                "steps[].posting.")
            .stream()
            .map(List::getFirst)
            .toList();
    List<String> openingPositionLabels =
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                ledgerPlanShape.postingModel(),
                Objects.requireNonNull(
                    MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION)),
                "steps[].posting.")
            .stream()
            .map(List::getFirst)
            .toList();
    List<String> reversalLabels =
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                ledgerPlanShape.postingModel(),
                Objects.requireNonNull(
                    MachineContract.requestTemplate(OperationId.RECORD_REVERSAL)),
                "steps[].posting.")
            .stream()
            .map(List::getFirst)
            .toList();

    assertFalse(directJournalLabels.contains("steps[].posting.lines[].accountCode"));
    assertFalse(openingPositionLabels.contains("steps[].posting.openingBalances[].accountCode"));
    assertFalse(reversalLabels.contains("steps[].posting.reversal.priorPostingId"));
  }

  @Test
  void supplementalRows_fallBackToPublishedFacts_andCanonicalRowsSkipMissingOrForbiddenFields() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor withoutSaleSemantics =
        postingModelWithoutSelectedEntryKind(postingModel, saleTemplate);

    ContractRequestShapes.EntryKindSemanticsDescriptor fallback =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKindOrPublishedFallback(
            withoutSaleSemantics, saleTemplate);
    assertEquals(saleTemplate.entryKind(), fallback.entryKind());
    assertTrue(fallback.requiredTopLevelFields().contains("cashAccountCode"));
    assertFalse(
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                withoutSaleSemantics, saleTemplate, "")
            .stream()
            .map(List::getFirst)
            .anyMatch("cashAccountCode"::equals));

    List<List<String>> rows = new ArrayList<>();
    CliDiscoveryPostingModelRowSupport.appendTopLevelRows(
        rows,
        List.of(
            new ContractRequestShapes.RequestFieldDescriptor(
                "entryKind", RequestFieldPresence.REQUIRED, "Entry kind."),
            new ContractRequestShapes.RequestFieldDescriptor(
                "amount", RequestFieldPresence.FORBIDDEN, "Amount.")),
        "",
        postingModel,
        saleTemplate,
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, saleTemplate));

    assertEquals(List.of("entryKind"), rows.stream().map(List::getFirst).toList());
  }

  @Test
  void supplementalRows_doNotDuplicateCanonicalRecognitionInterval() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor prepaymentTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_PREPAYMENT));

    List<String> labels =
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                postingModel, prepaymentTemplate, "")
            .stream()
            .map(List::getFirst)
            .toList();

    assertFalse(labels.contains("recognitionInterval.startDate"), labels.toString());
  }

  @Test
  void renderRequestGuidance_rejectsMissingSelectedEntryKindSemantics() {
    HelpDescriptor postEntryHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.POST_ENTRY);
    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        Objects.requireNonNull(postEntryHelp.requestShapes());
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape =
        Objects.requireNonNull(requestShapes.bookkeepingEntry());
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate =
        Objects.requireNonNull(postEntryHelp.requestTemplate());
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor withoutDirectJournalSemantics =
        new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
            postEntryShape.topLevelFields(),
            postEntryShape.lineFields(),
            postEntryShape.openingBalanceFields(),
            postEntryShape.recognitionIntervalFields(),
            postEntryShape.foreignExchangeFields(),
            postEntryShape.quotedRateFields(),
            postEntryShape.taxFields(),
            postEntryShape.evidenceFields(),
            postEntryShape.sourceDocumentFields(),
            postEntryShape.approvalFields(),
            postEntryShape.provenanceFields(),
            postEntryShape.reversalFields(),
            postEntryShape.entryKindSemantics().stream()
                .filter(entryKind -> entryKind.entryKind() != postingTemplate.entryKind())
                .toList(),
            postEntryShape.reachabilityMatrix(),
            postEntryShape.evidenceRequirement(),
            postEntryShape.enumVocabularies(),
            postEntryShape.schema());

    IllegalStateException missingSemantics =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliDiscoveryCommandGuidance.renderRequestGuidance(
                    helpDescriptorWithPostEntryShape(
                        postEntryHelp, requestShapes, withoutDirectJournalSemantics),
                    OperationId.POST_ENTRY));

    assertTrue(
        Objects.requireNonNull(missingSemantics.getMessage())
            .contains("entry-kind semantics for 'DIRECT_JOURNAL'"));
  }

  @Test
  void renderRequestGuidance_returnsEmptyWhenDeclareTaxRegistrationArtifactsAreMissing() {
    HelpDescriptor declareTaxRegistrationHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.DECLARE_TAX_REGISTRATION);

    String missingShapeGuidance =
        CliDiscoveryCommandGuidance.renderRequestGuidance(
            new HelpDescriptor(
                declareTaxRegistrationHelp.application(),
                declareTaxRegistrationHelp.version(),
                declareTaxRegistrationHelp.protocolVersion(),
                declareTaxRegistrationHelp.description(),
                declareTaxRegistrationHelp.usage(),
                declareTaxRegistrationHelp.bookModel(),
                declareTaxRegistrationHelp.bookkeepingKernel(),
                new ContractRequestShapes.RequestShapesDescriptor(
                    Objects.requireNonNull(declareTaxRegistrationHelp.requestShapes())
                        .schemaDialect(),
                    Objects.requireNonNull(declareTaxRegistrationHelp.requestShapes())
                        .bookkeepingEntry(),
                    Objects.requireNonNull(declareTaxRegistrationHelp.requestShapes())
                        .declareAccount(),
                    Objects.requireNonNull(declareTaxRegistrationHelp.requestShapes())
                        .retireAccount(),
                    null,
                    Objects.requireNonNull(declareTaxRegistrationHelp.requestShapes())
                        .ledgerPlan()),
                declareTaxRegistrationHelp.requestTemplate(),
                declareTaxRegistrationHelp.declareAccountTemplate(),
                declareTaxRegistrationHelp.declareTaxRegistrationTemplate(),
                declareTaxRegistrationHelp.planTemplate(),
                declareTaxRegistrationHelp.commands(),
                declareTaxRegistrationHelp.quickStart(),
                declareTaxRegistrationHelp.exitCodes(),
                declareTaxRegistrationHelp.preflight(),
                declareTaxRegistrationHelp.currencyModel()),
            OperationId.DECLARE_TAX_REGISTRATION);
    String missingRequestShapesGuidance =
        CliDiscoveryCommandGuidance.renderRequestGuidance(
            new HelpDescriptor(
                declareTaxRegistrationHelp.application(),
                declareTaxRegistrationHelp.version(),
                declareTaxRegistrationHelp.protocolVersion(),
                declareTaxRegistrationHelp.description(),
                declareTaxRegistrationHelp.usage(),
                declareTaxRegistrationHelp.bookModel(),
                declareTaxRegistrationHelp.bookkeepingKernel(),
                null,
                declareTaxRegistrationHelp.requestTemplate(),
                declareTaxRegistrationHelp.declareAccountTemplate(),
                declareTaxRegistrationHelp.declareTaxRegistrationTemplate(),
                declareTaxRegistrationHelp.planTemplate(),
                declareTaxRegistrationHelp.commands(),
                declareTaxRegistrationHelp.quickStart(),
                declareTaxRegistrationHelp.exitCodes(),
                declareTaxRegistrationHelp.preflight(),
                declareTaxRegistrationHelp.currencyModel()),
            OperationId.DECLARE_TAX_REGISTRATION);
    String missingTemplateGuidance =
        CliDiscoveryCommandGuidance.renderRequestGuidance(
            new HelpDescriptor(
                declareTaxRegistrationHelp.application(),
                declareTaxRegistrationHelp.version(),
                declareTaxRegistrationHelp.protocolVersion(),
                declareTaxRegistrationHelp.description(),
                declareTaxRegistrationHelp.usage(),
                declareTaxRegistrationHelp.bookModel(),
                declareTaxRegistrationHelp.bookkeepingKernel(),
                declareTaxRegistrationHelp.requestShapes(),
                declareTaxRegistrationHelp.requestTemplate(),
                declareTaxRegistrationHelp.declareAccountTemplate(),
                null,
                declareTaxRegistrationHelp.planTemplate(),
                declareTaxRegistrationHelp.commands(),
                declareTaxRegistrationHelp.quickStart(),
                declareTaxRegistrationHelp.exitCodes(),
                declareTaxRegistrationHelp.preflight(),
                declareTaxRegistrationHelp.currencyModel()),
            OperationId.DECLARE_TAX_REGISTRATION);

    assertEquals("", missingShapeGuidance);
    assertEquals("", missingRequestShapesGuidance);
    assertEquals("", missingTemplateGuidance);
  }

  private static boolean templatePublishesField(
      String fieldName,
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    try {
      return (boolean) TEMPLATE_PUBLISHES_FIELD.invoke(fieldName, postingTemplate);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("templatePublishesField threw unexpectedly.", throwable);
    }
  }

  private static MethodHandle templatePublishesFieldHandle() {
    try {
      return MethodHandles.privateLookupIn(
              CliDiscoveryPostingModelRowSupport.class, MethodHandles.lookup())
          .findStatic(
              CliDiscoveryPostingModelRowSupport.class,
              "templatePublishesField",
              MethodType.methodType(
                  boolean.class,
                  String.class,
                  ContractPostingRequestTemplates.PostingRequestTemplateDescriptor.class));
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError("Unable to access templatePublishesField.", exception);
    }
  }

  private static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      saleTemplateWithOwnedNestedFacts(
          ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        saleTemplate.entryKind(),
        saleTemplate.effectiveDate(),
        saleTemplate.cashAccountCode(),
        saleTemplate.receivableAccountCode(),
        saleTemplate.payableAccountCode(),
        saleTemplate.revenueAccountCode(),
        saleTemplate.inventoryAccountCode(),
        saleTemplate.expenseAccountCode(),
        saleTemplate.writeDownLossAccountCode(),
        saleTemplate.shrinkageLossAccountCode(),
        saleTemplate.countGainAccountCode(),
        saleTemplate.equityAccountCode(),
        saleTemplate.amount(),
        null,
        null,
        saleTemplate.inventoryRelief(),
        saleTemplate.settlementAdjunct(),
        new ForeignExchangeTemplateDescriptor(
            new MonetaryAmount("USD", "1100"),
            new MonetaryAmount("EUR", "1000"),
            new QuotedExchangeRateTemplateDescriptor(
                new MonetaryAmount("USD", "110"),
                new MonetaryAmount("EUR", "100"),
                "2026-04-25",
                "ECB daily reference rate"),
            ForeignExchangeTreatmentKind.SPOT_TRANSACTION),
        new ContractSettlementTemplates.TaxSelectionTemplateDescriptor(
            "vat-lv", "vat-standard-sale"),
        saleTemplate.lines(),
        saleTemplate.openingBalances(),
        saleTemplate.evidence(),
        saleTemplate.provenance(),
        saleTemplate.reversal(),
        saleTemplate.accrualCutoffId(),
        saleTemplate.prepaymentAssetAccountCode(),
        saleTemplate.deferredRevenueAccountCode(),
        saleTemplate.accruedExpenseLiabilityAccountCode(),
        saleTemplate.recognitionInterval(),
        saleTemplate.latvianMonthlyPayroll(),
        saleTemplate.latvianPayrollSettlement(),
        saleTemplate.fixedAsset(),
        saleTemplate.financing(),
        saleTemplate.realizedForeignExchange());
  }

  private static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor
      postingModelWithForbiddenFields(
          ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape) {
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        appendField(
            postEntryShape.topLevelFields(),
            new ContractRequestShapes.RequestFieldDescriptor(
                "forbiddenTopLevel", RequestFieldPresence.FORBIDDEN, "ignored")),
        appendField(
            postEntryShape.lineFields(),
            new ContractRequestShapes.RequestFieldDescriptor(
                "forbiddenLine", RequestFieldPresence.FORBIDDEN, "ignored")),
        postEntryShape.openingBalanceFields(),
        postEntryShape.recognitionIntervalFields(),
        postEntryShape.foreignExchangeFields(),
        postEntryShape.quotedRateFields(),
        postEntryShape.taxFields(),
        postEntryShape.evidenceFields(),
        postEntryShape.sourceDocumentFields(),
        postEntryShape.approvalFields(),
        postEntryShape.provenanceFields(),
        postEntryShape.reversalFields(),
        postEntryShape.entryKindSemantics(),
        postEntryShape.reachabilityMatrix(),
        postEntryShape.evidenceRequirement(),
        postEntryShape.enumVocabularies(),
        postEntryShape.schema());
  }

  private static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor
      postingModelWithoutSelectedEntryKind(
          ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
          ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        postEntryShape.topLevelFields(),
        postEntryShape.lineFields(),
        postEntryShape.openingBalanceFields(),
        postEntryShape.recognitionIntervalFields(),
        postEntryShape.foreignExchangeFields(),
        postEntryShape.quotedRateFields(),
        postEntryShape.taxFields(),
        postEntryShape.evidenceFields(),
        postEntryShape.sourceDocumentFields(),
        postEntryShape.approvalFields(),
        postEntryShape.provenanceFields(),
        postEntryShape.reversalFields(),
        postEntryShape.entryKindSemantics().stream()
            .filter(entryKind -> entryKind.entryKind() != postingTemplate.entryKind())
            .toList(),
        postEntryShape.reachabilityMatrix(),
        postEntryShape.evidenceRequirement(),
        postEntryShape.enumVocabularies(),
        postEntryShape.schema());
  }

  private static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor
      postingModelWithoutOwnedFieldGroups(
          ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
          ContractPostingRequestTemplates.PostingRequestTemplateDescriptor postingTemplate,
          String... removedTopLevelFields) {
    ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postEntryShape, postingTemplate);
    List<String> removedFields = List.of(removedTopLevelFields);
    ContractRequestShapes.EntryKindSemanticsDescriptor adjustedEntryKind =
        new ContractRequestShapes.EntryKindSemanticsDescriptor(
            selectedEntryKind.entryKind(),
            selectedEntryKind.requiredTopLevelFields().stream()
                .filter(fieldName -> !removedFields.contains(fieldName))
                .toList(),
            selectedEntryKind.optionalTopLevelFields().stream()
                .filter(fieldName -> !removedFields.contains(fieldName))
                .toList(),
            selectedEntryKind.forbiddenTopLevelFields(),
            selectedEntryKind.variantFields(),
            selectedEntryKind.requiredSourceDocumentFields(),
            selectedEntryKind.sourceDocumentTypeMode(),
            selectedEntryKind.acceptedSourceDocumentTypes(),
            selectedEntryKind.sourceDocumentTypeSemantics(),
            selectedEntryKind.semantics());
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        postEntryShape.topLevelFields(),
        postEntryShape.lineFields(),
        postEntryShape.openingBalanceFields(),
        postEntryShape.recognitionIntervalFields(),
        postEntryShape.foreignExchangeFields(),
        postEntryShape.quotedRateFields(),
        postEntryShape.taxFields(),
        postEntryShape.evidenceFields(),
        postEntryShape.sourceDocumentFields(),
        postEntryShape.approvalFields(),
        postEntryShape.provenanceFields(),
        postEntryShape.reversalFields(),
        postEntryShape.entryKindSemantics().stream()
            .map(
                entryKind ->
                    entryKind.entryKind() == adjustedEntryKind.entryKind()
                        ? adjustedEntryKind
                        : entryKind)
            .toList(),
        postEntryShape.reachabilityMatrix(),
        postEntryShape.evidenceRequirement(),
        postEntryShape.enumVocabularies(),
        postEntryShape.schema());
  }

  private static HelpDescriptor helpDescriptorWithPostEntryShape(
      HelpDescriptor baseHelp,
      ContractRequestShapes.RequestShapesDescriptor requestShapes,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape) {
    return new HelpDescriptor(
        baseHelp.application(),
        baseHelp.version(),
        baseHelp.protocolVersion(),
        baseHelp.description(),
        baseHelp.usage(),
        baseHelp.bookModel(),
        baseHelp.bookkeepingKernel(),
        new ContractRequestShapes.RequestShapesDescriptor(
            requestShapes.schemaDialect(),
            postEntryShape,
            requestShapes.declareAccount(),
            requestShapes.retireAccount(),
            requestShapes.declareTaxRegistration(),
            requestShapes.ledgerPlan()),
        baseHelp.requestTemplate(),
        baseHelp.declareAccountTemplate(),
        baseHelp.declareTaxRegistrationTemplate(),
        baseHelp.planTemplate(),
        baseHelp.commands(),
        baseHelp.quickStart(),
        baseHelp.exitCodes(),
        baseHelp.preflight(),
        baseHelp.currencyModel());
  }

  private static List<ContractRequestShapes.RequestFieldDescriptor> appendField(
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      ContractRequestShapes.RequestFieldDescriptor extraField) {
    List<ContractRequestShapes.RequestFieldDescriptor> expanded = new ArrayList<>(fields);
    expanded.add(extraField);
    return List.copyOf(expanded);
  }
}

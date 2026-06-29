package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.ForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.QuotedExchangeRateTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
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
  void includesCanonicalTopLevelField_coversOwnedAndUnownedPublishedFamilies() {
    HelpDescriptor preflightHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.PREFLIGHT_ENTRY);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel =
        Objects.requireNonNull(
            Objects.requireNonNull(preflightHelp.requestShapes()).bookkeepingEntry());
    ContractTemplates.PostingRequestTemplateDescriptor directJournalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY));
    ContractRequestShapes.EntryKindSemanticsDescriptor directJournalSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, directJournalTemplate);
    ContractTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE));
    ContractRequestShapes.EntryKindSemanticsDescriptor saleSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, saleTemplate);
    ContractTemplates.PostingRequestTemplateDescriptor expenseTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_EXPENSE));
    ContractRequestShapes.EntryKindSemanticsDescriptor expenseSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, expenseTemplate);
    ContractTemplates.PostingRequestTemplateDescriptor contributionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OWNER_CONTRIBUTION));
    ContractRequestShapes.EntryKindSemanticsDescriptor contributionSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, contributionTemplate);
    ContractTemplates.PostingRequestTemplateDescriptor openingPositionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION));
    ContractRequestShapes.EntryKindSemanticsDescriptor openingPositionSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(
            postingModel, openingPositionTemplate);
    ContractTemplates.PostingRequestTemplateDescriptor reversalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_REVERSAL));
    ContractRequestShapes.EntryKindSemanticsDescriptor reversalSemantics =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postingModel, reversalTemplate);

    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            directJournalTemplate,
            directJournalSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            directJournalTemplate,
            directJournalSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.LINES, directJournalTemplate, directJournalSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.AMOUNT, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.TAX, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.EVIDENCE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.PROVENANCE, saleTemplate, saleSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            expenseTemplate,
            expenseSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            contributionTemplate,
            contributionSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
            openingPositionTemplate,
            openingPositionSemantics));
    assertTrue(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.REVERSAL, reversalTemplate, reversalSemantics));
    assertFalse(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            ProtocolPostEntryFields.TopLevel.TAX, directJournalTemplate, directJournalSemantics));
    assertFalse(
        CliDiscoveryPostingModelRowSupport.includesCanonicalTopLevelField(
            "foreignField", saleTemplate, saleSemantics));
  }

  @Test
  void templatePublishesField_coversEverySwitchArm() {
    ContractTemplates.PostingRequestTemplateDescriptor directJournalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.POST_ENTRY));
    ContractTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE));
    ContractTemplates.PostingRequestTemplateDescriptor saleTemplateWithOwnedNestedFacts =
        saleTemplateWithOwnedNestedFacts(saleTemplate);
    ContractTemplates.PostingRequestTemplateDescriptor expenseTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_EXPENSE));
    ContractTemplates.PostingRequestTemplateDescriptor contributionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OWNER_CONTRIBUTION));
    ContractTemplates.PostingRequestTemplateDescriptor openingPositionTemplate =
        Objects.requireNonNull(
            MachineContract.requestTemplate(OperationId.RECORD_OPENING_POSITION));
    ContractTemplates.PostingRequestTemplateDescriptor reversalTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_REVERSAL));

    assertTrue(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE, saleTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE, directJournalTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE, saleTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE, expenseTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE, expenseTemplate));
    assertFalse(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE, contributionTemplate));
    assertFalse(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE, saleTemplate));
    assertTrue(templatePublishesField(ProtocolPostEntryFields.TopLevel.AMOUNT, saleTemplate));
    assertFalse(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.AMOUNT, directJournalTemplate));
    assertFalse(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, saleTemplateWithOwnedNestedFacts));
    assertFalse(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE, openingPositionTemplate));
    assertFalse(templatePublishesField(ProtocolPostEntryFields.TopLevel.TAX, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.TAX, saleTemplateWithOwnedNestedFacts));
    assertFalse(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.TAX, directJournalTemplate));
    assertTrue(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.LINES, directJournalTemplate));
    assertFalse(templatePublishesField(ProtocolPostEntryFields.TopLevel.LINES, saleTemplate));
    assertTrue(
        templatePublishesField(
            ProtocolPostEntryFields.TopLevel.OPENING_BALANCES, openingPositionTemplate));
    assertFalse(
        templatePublishesField(ProtocolPostEntryFields.TopLevel.OPENING_BALANCES, saleTemplate));
    assertTrue(templatePublishesField(ProtocolPostEntryFields.TopLevel.EVIDENCE, saleTemplate));
    assertTrue(templatePublishesField(ProtocolPostEntryFields.TopLevel.PROVENANCE, saleTemplate));
    assertTrue(templatePublishesField(ProtocolPostEntryFields.TopLevel.REVERSAL, reversalTemplate));
    assertFalse(templatePublishesField(ProtocolPostEntryFields.TopLevel.REVERSAL, saleTemplate));
    assertFalse(templatePublishesField("foreignField", saleTemplate));
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
    ContractTemplates.PostingRequestTemplateDescriptor postingTemplate =
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
                Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE)),
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
    ContractTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE));

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
    ContractTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE));
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor strippedPostingModel =
        postingModelWithoutOwnedFieldGroups(
            postingModel,
            saleTemplate,
            ProtocolPostEntryFields.TopLevel.EVIDENCE,
            ProtocolPostEntryFields.TopLevel.PROVENANCE);

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
    ContractTemplates.PostingRequestTemplateDescriptor postingTemplate =
        Objects.requireNonNull(executePlanHelp.planTemplate()).canonicalPostingTemplate();

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
  void
      renderRequestGuidance_executePlanUsesOnlyPrimaryLedgerStructureWhenNoSupplementalRowsExist() {
    HelpDescriptor executePlanHelp =
        MachineContract.help(
            CliDiscoveryTestSupport.identity(),
            CliDiscoveryTestSupport.environment(),
            OperationId.EXECUTE_PLAN);
    ContractRequestShapes.RequestShapesDescriptor requestShapes =
        Objects.requireNonNull(executePlanHelp.requestShapes());
    ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape =
        Objects.requireNonNull(requestShapes.ledgerPlan());
    ContractTemplates.PostingRequestTemplateDescriptor postingTemplate =
        Objects.requireNonNull(executePlanHelp.planTemplate()).canonicalPostingTemplate();
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor trimmedPostingModel =
        canonicalOnlyPostingModel(ledgerPlanShape.postingModel(), postingTemplate);

    assertTrue(
        CliDiscoveryPostingModelGuidance.supplementalPostingModelRows(
                trimmedPostingModel, postingTemplate, "steps[].posting.")
            .isEmpty());

    String rendered =
        CliDiscoveryCommandGuidance.renderRequestGuidance(
            helpDescriptorWithLedgerPlanPostingModel(
                executePlanHelp, requestShapes, ledgerPlanShape, trimmedPostingModel),
            OperationId.EXECUTE_PLAN);

    assertTrue(rendered.contains("Plan structure"), rendered);
    assertTrue(rendered.contains("steps[].posting.cashAccountCode"), rendered);
    assertFalse(rendered.contains("steps[].posting.expenseAccountCode"), rendered);
    assertFalse(rendered.contains("steps[].posting.lines[].accountCode"), rendered);
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
    ContractTemplates.PostingRequestTemplateDescriptor postingTemplate =
        Objects.requireNonNull(postEntryHelp.requestTemplate());
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor withoutDirectJournalSemantics =
        new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
            postEntryShape.topLevelFields(),
            postEntryShape.lineFields(),
            postEntryShape.openingBalanceFields(),
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
      String fieldName, ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
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
                  ContractTemplates.PostingRequestTemplateDescriptor.class));
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError("Unable to access templatePublishesField.", exception);
    }
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor
      saleTemplateWithOwnedNestedFacts(
          ContractTemplates.PostingRequestTemplateDescriptor saleTemplate) {
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        saleTemplate.entryKind(),
        saleTemplate.effectiveDate(),
        saleTemplate.cashAccountCode(),
        saleTemplate.revenueAccountCode(),
        saleTemplate.expenseAccountCode(),
        saleTemplate.equityAccountCode(),
        saleTemplate.amount(),
        new ForeignExchangeTemplateDescriptor(
            new MonetaryAmount("USD", "1100"),
            new MonetaryAmount("EUR", "1000"),
            new QuotedExchangeRateTemplateDescriptor(
                new MonetaryAmount("USD", "110"),
                new MonetaryAmount("EUR", "100"),
                "2026-04-25",
                "ECB daily reference rate"),
            ForeignExchangeTreatmentKind.SPOT_SETTLEMENT),
        new ContractTemplates.TaxSelectionTemplateDescriptor("vat-lv", "vat-standard-sale"),
        saleTemplate.lines(),
        saleTemplate.openingBalances(),
        saleTemplate.evidence(),
        saleTemplate.provenance(),
        saleTemplate.reversal());
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
      canonicalOnlyPostingModel(
          ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
          ContractTemplates.PostingRequestTemplateDescriptor postingTemplate) {
    ContractRequestShapes.EntryKindSemanticsDescriptor selectedEntryKind =
        CliDiscoveryPostingFieldDescriptions.selectedEntryKind(postEntryShape, postingTemplate);
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        postEntryShape.topLevelFields().stream()
            .filter(field -> selectedEntryKind.requiredTopLevelFields().contains(field.name()))
            .toList(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        postEntryShape.evidenceFields(),
        postEntryShape.sourceDocumentFields(),
        postEntryShape.approvalFields(),
        postEntryShape.provenanceFields(),
        List.of(),
        List.of(selectedEntryKind),
        postEntryShape.reachabilityMatrix(),
        postEntryShape.evidenceRequirement(),
        postEntryShape.enumVocabularies(),
        postEntryShape.schema());
  }

  private static ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor
      postingModelWithoutOwnedFieldGroups(
          ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postEntryShape,
          ContractTemplates.PostingRequestTemplateDescriptor postingTemplate,
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
            selectedEntryKind.requiredSourceDocumentFields(),
            selectedEntryKind.sourceDocumentTypeMode(),
            selectedEntryKind.acceptedSourceDocumentTypes(),
            selectedEntryKind.sourceDocumentTypeSemantics(),
            selectedEntryKind.semantics());
    return new ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor(
        postEntryShape.topLevelFields(),
        postEntryShape.lineFields(),
        postEntryShape.openingBalanceFields(),
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

  private static HelpDescriptor helpDescriptorWithLedgerPlanPostingModel(
      HelpDescriptor baseHelp,
      ContractRequestShapes.RequestShapesDescriptor requestShapes,
      ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel) {
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
            requestShapes.bookkeepingEntry(),
            requestShapes.declareAccount(),
            requestShapes.declareTaxRegistration(),
            new ContractRequestShapes.LedgerPlanRequestShapeDescriptor(
                ledgerPlanShape.topLevelFields(),
                ledgerPlanShape.stepFields(),
                ledgerPlanShape.queryFields(),
                ledgerPlanShape.assertionFields(),
                postingModel,
                ledgerPlanShape.administrationStepKinds(),
                ledgerPlanShape.queryStepKinds(),
                ledgerPlanShape.writeStepKinds(),
                ledgerPlanShape.assertStepKind(),
                ledgerPlanShape.assertionKinds(),
                ledgerPlanShape.execution(),
                ledgerPlanShape.schema())),
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

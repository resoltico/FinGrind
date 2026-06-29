package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage tests for the tax-registration discovery scaffold and validation owners. */
class TaxDiscoveryTemplateCoverageTest {
  @Test
  void taxTemplateCatalogAndRequestShapes_publishCanonicalTaxSurface() {
    ContractTemplates.DeclareTaxRegistrationTemplateDescriptor template =
        MachineContractTemplatesCatalog.declareTaxRegistrationTemplate();
    ContractTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE));

    assertEquals("vat-lv", template.taxRegistrationId());
    assertEquals("Latvia VAT", template.taxRegistrationName());
    assertEquals("LV", template.jurisdiction().value());
    assertEquals("LV40000000000", template.registrationNumber());
    assertEquals("tax-payable-vat", template.payableAccountCode());
    assertEquals("tax-recoverable-vat", template.recoverableAccountCode());
    assertEquals(TaxObligationFrequency.MONTHLY, template.obligationFrequency());
    assertEquals(20, template.dueDaysAfterPeriodEnd());
    assertEquals(2, template.taxCodes().size());
    assertEquals("vat-standard-sale", template.taxCodes().getFirst().taxCode());
    assertEquals(TaxApplicationKind.OUTPUT_SALE, template.taxCodes().getFirst().applicationKind());
    assertEquals(
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        template.taxCodes().getLast().applicationKind());
    assertEquals(template, MachineContract.declareTaxRegistrationTemplate());
    assertNull(saleTemplate.tax());
    assertEquals(
        template,
        MachineContractTemplatesCatalog.declareTaxRegistrationTemplateFor(
            ProtocolCatalog.operation(OperationId.DECLARE_TAX_REGISTRATION)));
    assertNull(
        MachineContractTemplatesCatalog.declareTaxRegistrationTemplateFor(
            ProtocolCatalog.operation(OperationId.HELP)));
    assertNull(
        MachineContractDeclareTaxRegistrationSchemas.declareTaxRegistrationSchemaWithoutDialect()
            .get("$schema"));

    ContractRequestShapes.RequestShapesDescriptor registrationShapes =
        MachineContractTemplatesCatalog.requestShapesFor(
            ProtocolCatalog.operation(OperationId.DECLARE_TAX_REGISTRATION));
    assertNotNull(registrationShapes);
    assertNotNull(registrationShapes.declareTaxRegistration());
    assertNull(registrationShapes.bookkeepingEntry());

    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor saleDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.SALE);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor ownerContributionDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.OWNER_CONTRIBUTION);
    assertEquals(
        RequestFieldPresence.REQUIRED,
        fieldNamed(saleDescriptor.taxFields(), ProtocolPostEntryFields.Tax.TAX_REGISTRATION_ID)
            .presence());
    assertEquals(
        RequestFieldPresence.FORBIDDEN,
        fieldNamed(
                ownerContributionDescriptor.taxFields(),
                ProtocolPostEntryFields.Tax.TAX_REGISTRATION_ID)
            .presence());
  }

  @Test
  void taxTemplateValidationSupport_coversOptionalRegistrationNumberAndForbiddenTaxBranch() {
    ContractTemplates.DeclareTaxCodeTemplateDescriptor expenseCode =
        new ContractTemplates.DeclareTaxCodeTemplateDescriptor(
            "vat-standard-expense",
            "VAT Standard Expense",
            210_000,
            TaxInclusionMode.INCLUSIVE,
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE);
    ContractDeclarationTemplateValidationSupport.DeclareTaxRegistrationTemplateValues
        withRegistrationNumber =
            ContractDeclarationTemplateValidationSupport.validateDeclareTaxRegistrationTemplate(
                "vat-lv",
                "Latvia VAT",
                new TaxJurisdiction("LV"),
                "LV40001234567",
                "2100",
                "1300",
                TaxObligationFrequency.MONTHLY,
                20,
                List.of(expenseCode));
    ContractDeclarationTemplateValidationSupport.DeclareTaxRegistrationTemplateValues
        withoutRegistrationNumber =
            ContractDeclarationTemplateValidationSupport.validateDeclareTaxRegistrationTemplate(
                "vat-ee",
                "Estonia VAT",
                new TaxJurisdiction("EE"),
                null,
                "2200",
                "1400",
                TaxObligationFrequency.QUARTERLY,
                30,
                List.of(expenseCode));
    ContractDeclarationTemplateValidationSupport.DeclareTaxCodeTemplateValues codeValues =
        ContractDeclarationTemplateValidationSupport.validateDeclareTaxCodeTemplate(
            "vat-standard-sale",
            "VAT Standard Sale",
            210_000,
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.OUTPUT_SALE);
    ContractTemplates.DeclareTaxRegistrationTemplateDescriptor descriptor =
        new ContractTemplates.DeclareTaxRegistrationTemplateDescriptor(
            "vat-ee",
            "Estonia VAT",
            new TaxJurisdiction("EE"),
            null,
            "tax-payable-vat",
            "tax-recoverable-vat",
            TaxObligationFrequency.QUARTERLY,
            30,
            List.of(expenseCode));

    assertEquals("LV40001234567", withRegistrationNumber.registrationNumber());
    assertNull(withoutRegistrationNumber.registrationNumber());
    assertEquals("vat-standard-sale", codeValues.taxCode());
    assertEquals(TaxApplicationKind.OUTPUT_SALE, codeValues.applicationKind());
    assertNull(descriptor.registrationNumber());
    assertEquals(TaxInclusionMode.INCLUSIVE, descriptor.taxCodes().getFirst().inclusionMode());

    IllegalArgumentException forbiddenTax =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingTemplateFieldRules.forbidTax(
                    new ContractTemplates.TaxSelectionTemplateDescriptor(
                        "vat-lv", "vat-standard-sale"),
                    "ownerContribution"));
    assertEquals("tax must be absent for ownerContribution.", forbiddenTax.getMessage());
  }

  @Test
  void provenanceTemplateValidation_coversLiveAndPlaceholderBranches() {
    ContractTemplates.ProvenanceTemplateDescriptor live =
        new ContractTemplates.ProvenanceTemplateDescriptor(
            "actor-1", ActorType.PERSON, "command-1", "idem-1", "cause-1", "corr-1");

    assertEquals("actor-1", live.actorId());
    assertEquals("corr-1", live.correlationId());
    assertEquals(
        ScaffoldPlaceholders.ACTOR_ID,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                ScaffoldPlaceholders.ACTOR_ID,
                ActorType.PERSON,
                "command-1",
                "idem-1",
                "cause-1",
                null)
            .actorId());
    assertEquals(
        ScaffoldPlaceholders.COMMAND_ID,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                "actor-1",
                ActorType.PERSON,
                ScaffoldPlaceholders.COMMAND_ID,
                "idem-1",
                "cause-1",
                null)
            .commandId());
    assertEquals(
        ScaffoldPlaceholders.IDEMPOTENCY_KEY,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                "actor-1",
                ActorType.PERSON,
                "command-1",
                ScaffoldPlaceholders.IDEMPOTENCY_KEY,
                "cause-1",
                null)
            .idempotencyKey());
    assertEquals(
        ScaffoldPlaceholders.CAUSATION_ID,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                "actor-1",
                ActorType.PERSON,
                "command-1",
                "idem-1",
                ScaffoldPlaceholders.CAUSATION_ID,
                null)
            .causationId());
    assertEquals(
        ScaffoldPlaceholders.COMMAND_ID,
        ContractTemplateValidationSupport.validateProvenanceTemplate(
                "actor-1",
                ActorType.PERSON,
                ScaffoldPlaceholders.COMMAND_ID,
                "idem-1",
                "cause-1",
                ScaffoldPlaceholders.COMMAND_ID)
            .commandId());
    assertEquals(
        ScaffoldPlaceholders.COMMAND_ID,
        ContractTemplateValidationSupport.validateProvenanceTemplate(
                "actor-1",
                ActorType.PERSON,
                "command-1",
                "idem-1",
                "cause-1",
                ScaffoldPlaceholders.COMMAND_ID)
            .correlationId());
  }

  private static ContractRequestShapes.RequestFieldDescriptor fieldNamed(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String name) {
    return fields.stream().filter(field -> name.equals(field.name())).findFirst().orElseThrow();
  }
}

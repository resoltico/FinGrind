package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage tests for the tax-registration discovery scaffold and validation owners. */
class TaxDiscoveryTemplateCoverageTest {
  private static final MethodHandle TAX_CODE_SCAFFOLD_VALUE = taxCodeScaffoldValueHandle();

  @Test
  void taxTemplateCatalogAndRequestShapes_publishCanonicalTaxSurface() {
    ContractTemplates.DeclareTaxRegistrationTemplateDescriptor template =
        MachineContractTemplatesCatalog.declareTaxRegistrationTemplate();
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor saleTemplate =
        Objects.requireNonNull(MachineContract.requestTemplate(OperationId.RECORD_SALE_SETTLED));

    assertEquals("replace-before-commit-tax-registration-id", template.taxRegistrationId());
    assertEquals("Replace Before Commit Tax Registration", template.taxRegistrationName());
    assertEquals("<ISO-3166-alpha-2>", template.jurisdiction().value());
    assertEquals("replace-before-commit-registration-number", template.registrationNumber());
    assertEquals("tax-payable-vat", template.payableAccountCode());
    assertEquals("tax-recoverable-vat", template.recoverableAccountCode());
    assertEquals(TaxObligationFrequency.MONTHLY, template.obligationFrequency());
    assertEquals(20, template.dueDaysAfterPeriodEnd());
    assertEquals(2, template.taxCodes().size());
    assertEquals("replace-before-commit-output-tax-code", template.taxCodes().getFirst().taxCode());
    assertEquals(0, template.taxCodes().getFirst().ratePartsPerMillion());
    assertEquals(TaxApplicationKind.OUTPUT_SALE, template.taxCodes().getFirst().applicationKind());
    assertEquals("replace-before-commit-input-tax-code", template.taxCodes().getLast().taxCode());
    assertEquals(0, template.taxCodes().getLast().ratePartsPerMillion());
    assertEquals(
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        template.taxCodes().getLast().applicationKind());
    assertEquals(template, MachineContract.declareTaxRegistrationTemplate());
    assertEquals(
        ScaffoldPlaceholders.TAX_REGISTRATION_ID,
        Objects.requireNonNull(saleTemplate.tax()).taxRegistrationId());
    assertEquals(ScaffoldPlaceholders.OUTPUT_TAX_CODE, saleTemplate.tax().taxCode());
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
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.SALE_SETTLED);
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
  void postingTemplates_publishTaxSelectorsExactlyWhenCanonicalFactsAllowTax() {
    for (BookkeepingEntryKind entryKind : BookkeepingEntryKind.values()) {
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template =
          MachineContractPostEntryVariantSchemas.template(entryKind);
      boolean taxAllowed =
          ProtocolCatalog.domain()
              .requestSurface()
              .bookkeepingEntryKind(entryKind)
              .optionalTopLevelFields()
              .contains(ProtocolBusinessEventFields.Core.TAX);

      assertEquals(taxAllowed, template.tax() != null, entryKind.wireValue());
      if (taxAllowed) {
        assertEquals(
            ScaffoldPlaceholders.TAX_REGISTRATION_ID,
            Objects.requireNonNull(template.tax()).taxRegistrationId(),
            entryKind.wireValue());
        assertEquals(expectedTaxCodePlaceholder(entryKind), template.tax().taxCode());
      }
    }
  }

  @Test
  void taxCodeScaffoldPolicy_rejectsAnUnmappedEntryKind() {
    IllegalStateException rejection =
        assertThrows(
            IllegalStateException.class, () -> taxCodeScaffoldValue(BookkeepingEntryKind.RECEIPT));

    assertEquals("No tax-selector scaffold policy is defined for RECEIPT.", rejection.getMessage());
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
                    new ContractSettlementTemplates.TaxSelectionTemplateDescriptor(
                        "vat-lv", "vat-standard-sale"),
                    "ownerContribution"));
    assertEquals("tax must be absent for ownerContribution.", forbiddenTax.getMessage());
  }

  @Test
  void provenanceTemplateValidation_coversLiveAndPlaceholderBranches() {
    ContractTemplates.ProvenanceTemplateDescriptor live =
        new ContractTemplates.ProvenanceTemplateDescriptor(
            "actor-1", "person", "command-1", "idem-1", "cause-1", "corr-1");

    assertEquals("actor-1", live.actorId());
    assertEquals("corr-1", live.correlationId());
    assertEquals(
        ScaffoldPlaceholders.ACTOR_ID,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                ScaffoldPlaceholders.ACTOR_ID,
                "person",
                "command-1",
                "idem-1",
                "cause-1",
                null)
            .actorId());
    assertEquals(
        ScaffoldPlaceholders.COMMAND_ID,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                "actor-1",
                "person",
                ScaffoldPlaceholders.COMMAND_ID,
                "idem-1",
                "cause-1",
                null)
            .commandId());
    assertEquals(
        ScaffoldPlaceholders.IDEMPOTENCY_KEY,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                "actor-1",
                "person",
                "command-1",
                ScaffoldPlaceholders.IDEMPOTENCY_KEY,
                "cause-1",
                null)
            .idempotencyKey());
    assertEquals(
        ScaffoldPlaceholders.CAUSATION_ID,
        new ContractTemplates.ProvenanceTemplateDescriptor(
                "actor-1",
                "person",
                "command-1",
                "idem-1",
                ScaffoldPlaceholders.CAUSATION_ID,
                null)
            .causationId());
    assertEquals(
        ScaffoldPlaceholders.COMMAND_ID,
        ContractTemplateValidationSupport.validateProvenanceTemplate(
                "actor-1",
                "person",
                ScaffoldPlaceholders.COMMAND_ID,
                "idem-1",
                "cause-1",
                ScaffoldPlaceholders.COMMAND_ID)
            .commandId());
    assertEquals(
        ScaffoldPlaceholders.COMMAND_ID,
        ContractTemplateValidationSupport.validateProvenanceTemplate(
                "actor-1",
                "person",
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

  private static String expectedTaxCodePlaceholder(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case SALE_SETTLED, SALE_ON_CREDIT -> ScaffoldPlaceholders.OUTPUT_TAX_CODE;
      case PURCHASE_SETTLED,
          PURCHASE_ON_CREDIT,
          INVENTORY_CAPITALIZATION_SETTLED,
          INVENTORY_CAPITALIZATION_ON_CREDIT,
          EXPENSE_SETTLED,
          EXPENSE_ON_CREDIT ->
          ScaffoldPlaceholders.INPUT_TAX_CODE;
      default -> throw new AssertionError("Unexpected tax-capable entry kind: " + entryKind);
    };
  }

  private static String taxCodeScaffoldValue(BookkeepingEntryKind entryKind) {
    try {
      return (String) TAX_CODE_SCAFFOLD_VALUE.invoke(entryKind);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("taxCodeScaffoldValue threw unexpectedly.", throwable);
    }
  }

  private static MethodHandle taxCodeScaffoldValueHandle() {
    try {
      return MethodHandles.lookup()
          .findStatic(
              MachineContractPostEntryTaxTemplateSupport.class,
              "taxCodeScaffoldValue",
              MethodType.methodType(String.class, BookkeepingEntryKind.class));
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError("Unable to access taxCodeScaffoldValue.", exception);
    }
  }
}

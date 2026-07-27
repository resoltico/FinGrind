package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.cli.json.CliTaxRejectionJsonModels;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxRegistrationPageCursor;
import dev.erst.fingrind.core.AccountCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Focused tax-surface coverage for deterministic payload mapping and text rendering branches. */
class CliTaxContractCoverageTest extends CliResponseWriterTestSupport {
  @Test
  void taxRejectionPayloadMapper_coversDeclarationAndQueryVariants() {
    CliEnvelopeJsonModels.Envelope<?> declarationBookNotInitialized =
        CliRejectionPayloadMapper.taxDeclarationRejectedEnvelope(
            new TaxDeclarationRejection.BookNotInitialized());
    CliEnvelopeJsonModels.Envelope<?> declarationDefinitionViolations =
        CliTaxRejectionPayloadMapper.declarationRejectedEnvelope(
            new TaxDeclarationRejection.DefinitionViolations(
                List.of(
                    new TaxDefinitionViolation(
                        "missing-tax-code", "taxCodes[0].taxCode", "Tax code is required."))));
    CliEnvelopeJsonModels.Envelope<?> queryBookNotInitialized =
        CliRejectionPayloadMapper.taxQueryRejectedEnvelope(
            dev.erst.fingrind.contract.protocol.OperationId.LIST_TAX_REGISTRATIONS,
            new TaxQueryRejection.BookNotInitialized());
    CliEnvelopeJsonModels.Envelope<?> unknownTaxRegistration =
        CliTaxRejectionPayloadMapper.queryRejectedEnvelope(
            dev.erst.fingrind.contract.protocol.OperationId.TAX_OBLIGATION,
            new TaxQueryRejection.UnknownTaxRegistration(new TaxRegistrationId("vat-missing")));
    CliEnvelopeJsonModels.Envelope<?> obligationPeriodMismatch =
        CliTaxRejectionPayloadMapper.queryRejectedEnvelope(
            dev.erst.fingrind.contract.protocol.OperationId.TAX_OBLIGATION,
            new TaxQueryRejection.ObligationPeriodMismatch(
                TaxObligationFrequency.MONTHLY,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-15")));

    assertEquals("tax-book-not-initialized", declarationBookNotInitialized.code());
    assertTrue(
        Objects.requireNonNull(declarationBookNotInitialized.message())
            .contains("missing or not initialized"),
        declarationBookNotInitialized.message());
    assertNull(declarationBookNotInitialized.details());

    assertEquals("tax-definition-violations", declarationDefinitionViolations.code());
    CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails definitionDetails =
        assertInstanceOf(
            CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails.class,
            declarationDefinitionViolations.details());
    assertEquals(1, definitionDetails.violations().size());
    assertEquals("missing-tax-code", definitionDetails.violations().getFirst().code());
    assertEquals("taxCodes[0].taxCode", definitionDetails.violations().getFirst().field());

    assertEquals("tax-query-book-not-initialized", queryBookNotInitialized.code());
    assertTrue(
        Objects.requireNonNull(queryBookNotInitialized.hint()).contains("open-book"),
        queryBookNotInitialized.hint());
    assertNull(queryBookNotInitialized.details());

    assertEquals("unknown-tax-registration", unknownTaxRegistration.code());
    CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails unknownDetails =
        assertInstanceOf(
            CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails.class,
            unknownTaxRegistration.details());
    assertEquals("vat-missing", unknownDetails.taxRegistrationId());
    assertTrue(
        Objects.requireNonNull(unknownTaxRegistration.hint()).contains("list-tax-registrations"),
        unknownTaxRegistration.hint());
    assertTrue(
        Objects.requireNonNull(unknownTaxRegistration.hint()).contains("tax-obligation"),
        unknownTaxRegistration.hint());

    assertEquals("tax-obligation-period-mismatch", obligationPeriodMismatch.code());
    CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails mismatchDetails =
        assertInstanceOf(
            CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails.class,
            obligationPeriodMismatch.details());
    assertEquals("MONTHLY", mismatchDetails.obligationFrequency());
    assertEquals("2026-04-01", mismatchDetails.effectiveDateFrom());
    assertEquals("2026-04-15", mismatchDetails.effectiveDateTo());
    assertTrue(
        Objects.requireNonNull(obligationPeriodMismatch.hint()).contains("one full monthly period"),
        obligationPeriodMismatch.hint());
  }

  @Test
  void taxRejectionJsonModels_validateAndDefensivelyCopyInputs() {
    List<CliTaxRejectionJsonModels.TaxDefinitionViolationDetails> violations =
        new ArrayList<>(
            List.of(
                new CliTaxRejectionJsonModels.TaxDefinitionViolationDetails(
                    "missing-tax-code", "taxCodes[0].taxCode", "Tax code is required."),
                new CliTaxRejectionJsonModels.TaxDefinitionViolationDetails(
                    "invalid-jurisdiction", null, "Jurisdiction must be ISO 3166-1 alpha-2.")));

    CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails details =
        new CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails(violations);
    violations.clear();

    assertEquals(2, details.violations().size());
    assertEquals("missing-tax-code", details.violations().getFirst().code());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliTaxRejectionJsonModels.TaxDefinitionViolationsDetails(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliTaxRejectionJsonModels.UnknownTaxRegistrationDetails(" "));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliTaxRejectionJsonModels.ObligationPeriodMismatchDetails(
                "MONTHLY", "2026-04-01", " "));
  }

  @Test
  void taxPayloadAndTextRenderers_coverOptionalRegistrationNumberAndTypedOutcome() {
    DeclaredTaxRegistration registrationWithoutNumber = declaredTaxRegistration(null);
    DeclaredTaxRegistration registrationWithNumber = declaredTaxRegistration("LV40001234567");
    CliTaxJsonModels.DeclaredTaxRegistrationPayload withoutNumberPayload =
        CliTaxPayloadMapper.taxRegistrationPayload(registrationWithoutNumber);
    CliTaxJsonModels.DeclaredTaxRegistrationPayload withNumberPayload =
        CliTaxPayloadMapper.taxRegistrationPayload(registrationWithNumber);
    TaxRegistrationPageCursor emptyCursor =
        new TaxRegistrationPageCursor(new TaxRegistrationId("vat-empty-next"));
    TaxRegistrationPageCursor listedCursor =
        new TaxRegistrationPageCursor(new TaxRegistrationId("vat-listed-next"));
    TaxRegistrationPage emptyPage =
        new TaxRegistrationPage(bookIdentity(), List.of(), 1, Optional.of(emptyCursor));
    TaxRegistrationPage listedPage =
        new TaxRegistrationPage(
            bookIdentity(), List.of(registrationWithNumber), 1, Optional.of(listedCursor));
    String unchangedMutationText =
        CliTaxOutputRenderer.renderTaxRegistrationMutationText(
            CliTaxJsonModels.TaxRegistrationMutationOutcome.UNCHANGED,
            registrationWithNumber,
            null);
    String emptyListText = CliTaxOutputRenderer.renderTaxRegistrationListText(emptyPage, false);
    String listedText = CliTaxOutputRenderer.renderTaxRegistrationListText(listedPage, false);

    assertNull(withoutNumberPayload.registrationNumber());
    assertEquals("LV40001234567", withNumberPayload.registrationNumber());
    assertTrue(unchangedMutationText.contains("Tax Registration Unchanged"), unchangedMutationText);
    assertTrue(
        emptyListText.contains("No tax registrations matched the selected scope."), emptyListText);
    assertTrue(emptyListText.contains(emptyCursor.wireValue()), emptyListText);
    assertTrue(listedText.contains(listedCursor.wireValue()), listedText);
    assertTrue(listedText.contains("Latvia VAT"), listedText);
  }

  private static DeclaredTaxRegistration declaredTaxRegistration(
      @Nullable String registrationNumber) {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        registrationNumber == null ? null : new TaxRegistrationNumber(registrationNumber),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)),
        Instant.parse("2026-04-17T10:20:30Z"));
  }
}

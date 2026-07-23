package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
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
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tax-registration-specific writer coverage that stays separate from generic administration I/O.
 */
class CliAdministrativeTaxCommandResponseWriterTest extends CliResponseWriterTestSupport {

  @Test
  void writeDeclareTaxRegistrationResult_coversAllOutcomesAcrossOutputModes() throws Exception {
    DeclaredTaxRegistration declaredRegistration = declaredTaxRegistration(null, 20);
    DeclaredTaxRegistration updatedRegistration = declaredTaxRegistration("LV40001234567", 21);

    ByteArrayOutputStream declaredJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(declaredJsonOutput))
        .writeDeclareTaxRegistrationResult(
            new DeclareTaxRegistrationResult.Declared(declaredRegistration, attestationCommit()),
            OutputMode.JSON);
    assertJsonContains(declaredJsonOutput, "\"status\":\"ok\"");
    assertJsonContains(declaredJsonOutput, "\"outcome\":\"declared\"");
    assertJsonContains(declaredJsonOutput, "\"taxRegistrationId\":\"vat-lv\"");
    assertTrue(
        readJson(declaredJsonOutput).path("payload").path("registrationNumber").isMissingNode()
            || readJson(declaredJsonOutput).path("payload").path("registrationNumber").isNull());

    ByteArrayOutputStream declaredTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(declaredTextOutput))
        .writeDeclareTaxRegistrationResult(
            new DeclareTaxRegistrationResult.Declared(declaredRegistration, attestationCommit()),
            OutputMode.TEXT);
    String declaredText = declaredTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(declaredText.contains("Tax Registration Declared"));
    assertTrue(declaredText.contains("Registration number"));
    assertTrue(declaredText.contains("(none)"));

    ByteArrayOutputStream updatedTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(updatedTextOutput))
        .writeDeclareTaxRegistrationResult(
            new DeclareTaxRegistrationResult.Updated(updatedRegistration, attestationCommit()),
            OutputMode.TEXT);
    String updatedText = updatedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(updatedText.contains("Tax Registration Updated"));
    assertTrue(updatedText.contains("Registration number"));
    assertTrue(updatedText.contains("LV40001234567"));
    assertTrue(updatedText.contains("Due days after period end"));

    ByteArrayOutputStream updatedJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(updatedJsonOutput))
        .writeDeclareTaxRegistrationResult(
            new DeclareTaxRegistrationResult.Updated(updatedRegistration, attestationCommit()),
            OutputMode.JSON);
    assertJsonContains(updatedJsonOutput, "\"outcome\":\"updated\"");
    assertJsonContains(updatedJsonOutput, "\"registrationNumber\":\"LV40001234567\"");

    ByteArrayOutputStream unchangedJsonOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(unchangedJsonOutput))
        .writeDeclareTaxRegistrationResult(
            new DeclareTaxRegistrationResult.Unchanged(updatedRegistration, null), OutputMode.JSON);
    assertJsonContains(unchangedJsonOutput, "\"outcome\":\"unchanged\"");
    assertJsonContains(unchangedJsonOutput, "\"registrationNumber\":\"LV40001234567\"");

    ByteArrayOutputStream rejectedTextOutput = new ByteArrayOutputStream();
    new CliResponseWriter(utf8PrintStream(rejectedTextOutput))
        .writeDeclareTaxRegistrationResult(
            new DeclareTaxRegistrationResult.Rejected(
                new TaxDeclarationRejection.DefinitionViolations(
                    List.of(
                        new TaxDefinitionViolation(
                            "missing-tax-code", "taxCodes[0].taxCode", "Tax code is required.")))),
            OutputMode.TEXT);
    String rejectedText = rejectedTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rejectedText.contains("tax-definition-violations"), rejectedText);
    assertTrue(rejectedText.contains("Violation 1"), rejectedText);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeDeclareTaxRegistrationResult(
                    new DeclareTaxRegistrationResult.Declared(
                        declaredRegistration, attestationCommit()),
                    OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeDeclareTaxRegistrationResult(
                    new DeclareTaxRegistrationResult.Updated(
                        updatedRegistration, attestationCommit()),
                    OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliResponseWriter(utf8PrintStream(new ByteArrayOutputStream()))
                .writeDeclareTaxRegistrationResult(
                    new DeclareTaxRegistrationResult.Unchanged(updatedRegistration, null),
                    OutputMode.CSV));

    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new DeclareTaxRegistrationResult.Declared(declaredRegistration, attestationCommit())));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new DeclareTaxRegistrationResult.Updated(updatedRegistration, attestationCommit())));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new DeclareTaxRegistrationResult.Unchanged(updatedRegistration, null)));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new DeclareTaxRegistrationResult.Rejected(
                new TaxDeclarationRejection.BookNotInitialized())));
  }

  private static DeclaredTaxRegistration declaredTaxRegistration(
      @Nullable String registrationNumber, int dueDaysAfterPeriodEnd) {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        registrationNumber == null ? null : new TaxRegistrationNumber(registrationNumber),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        dueDaysAfterPeriodEnd,
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

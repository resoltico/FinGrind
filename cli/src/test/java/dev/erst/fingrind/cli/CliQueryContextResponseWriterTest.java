package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.core.AccountCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused text-mode coverage for opt-in context sections on book-read query surfaces. */
class CliQueryContextResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeBookReadTextResults_restoreContextSectionsOnlyWhenExplicitlyRequested() {
    PostingFact postingFact = postingFact();
    DeclaredTaxRegistration registration = declaredTaxRegistration("LV40001234567");
    TaxRegistrationPage registrations =
        new TaxRegistrationPage(bookIdentity(), List.of(registration), 10, Optional.empty());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriterBookReadSupport writer =
        new CliResponseWriterBookReadSupport(utf8PrintStream(outputStream));

    writer.writeGetPostingResult(foundPosting(postingFact), true, OutputMode.TEXT);
    String postingText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(postingText.contains("Context"), postingText);
    assertTrue(postingText.contains("Seed template"), postingText);

    outputStream.reset();
    writer.writeListPostingsResult(
        new ListPostingsResult.Listed(postingPage(List.of(postingFact), 10, Optional.empty())),
        true,
        OutputMode.TEXT);
    String postingRegisterText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(postingRegisterText.contains("Context"), postingRegisterText);
    assertTrue(postingRegisterText.contains("Account filter"), postingRegisterText);

    outputStream.reset();
    writer.writeListTaxRegistrationsResult(
        new ListTaxRegistrationsResult.Listed(registrations), true, OutputMode.TEXT);
    String taxRegistrationText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(taxRegistrationText.contains("Context"), taxRegistrationText);
    assertTrue(taxRegistrationText.contains("Seed template"), taxRegistrationText);
  }

  private static DeclaredTaxRegistration declaredTaxRegistration(String registrationNumber) {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        new TaxRegistrationNumber(registrationNumber),
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

package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Dedicated validation coverage for discovery-side foreign-exchange template descriptors. */
class ForeignExchangeTemplateDescriptorValidationTest {
  @Test
  void foreignExchangeTemplateDescriptors_validateOwnedAmountsAndOpeningPositionForbidsThem() {
    QuotedExchangeRateTemplateDescriptor quotedRate = quotedExchangeRateTemplate();
    ForeignExchangeTemplateDescriptor foreignExchange =
        new ForeignExchangeTemplateDescriptor(
            new MonetaryAmount("USD", "1100"),
            new MonetaryAmount("EUR", "1000"),
            quotedRate,
            ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);

    assertEquals("USD", foreignExchange.transactionAmount().currencyCode());
    assertEquals("ECB daily reference rate", quotedRate.quoteSource());
    assertEquals(
        "transactionAmount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeTemplateDescriptor(
                        new MonetaryAmount("USD", "0"),
                        new MonetaryAmount("EUR", "1000"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT))
            .getMessage());
    assertEquals(
        "functionalAmount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeTemplateDescriptor(
                        new MonetaryAmount("USD", "1100"),
                        new MonetaryAmount("EUR", "0"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT))
            .getMessage());
    assertEquals(
        "transactionCurrencyAmount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new QuotedExchangeRateTemplateDescriptor(
                        new MonetaryAmount("USD", "0"),
                        new MonetaryAmount("EUR", "100"),
                        "2026-04-25",
                        "ECB daily reference rate"))
            .getMessage());
    assertEquals(
        "functionalCurrencyAmount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new QuotedExchangeRateTemplateDescriptor(
                        new MonetaryAmount("USD", "110"),
                        new MonetaryAmount("EUR", "0"),
                        "2026-04-25",
                        "ECB daily reference rate"))
            .getMessage());
    assertEquals(
        "foreignExchange must be absent for openingPosition.",
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
                        foreignExchange,
                        null,
                        null,
                        List.of(
                            openingBalanceTemplate("1000", JournalLine.EntrySide.DEBIT, "1000"),
                            openingBalanceTemplate("2000", JournalLine.EntrySide.CREDIT, "1000")),
                        evidenceTemplate(),
                        provenanceTemplate(),
                        null))
            .getMessage());
  }

  private static QuotedExchangeRateTemplateDescriptor quotedExchangeRateTemplate() {
    return new QuotedExchangeRateTemplateDescriptor(
        new MonetaryAmount("USD", "110"),
        new MonetaryAmount("EUR", "100"),
        "2026-04-25",
        "ECB daily reference rate");
  }

  private static ContractTemplates.OpeningBalanceTemplateDescriptor openingBalanceTemplate(
      String accountCode, JournalLine.EntrySide side, String minorUnits) {
    return new ContractTemplates.OpeningBalanceTemplateDescriptor(
        accountCode, side, new MonetaryAmount("EUR", minorUnits));
  }

  private static ContractTemplates.AccountingEvidenceTemplateDescriptor evidenceTemplate() {
    return new ContractTemplates.AccountingEvidenceTemplateDescriptor(
        List.of(
            new ContractTemplates.SourceDocumentTemplateDescriptor(
                "document-idem-1", "cash-receipt", "2026-04-25")),
        List.of());
  }

  private static ContractTemplates.ProvenanceTemplateDescriptor provenanceTemplate() {
    return new ContractTemplates.ProvenanceTemplateDescriptor(
        "actor-1", ActorType.PERSON, "command-1", "idem-1", "cause-1", null);
  }
}

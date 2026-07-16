package dev.erst.fingrind.contract.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct contract coverage for owned foreign-exchange value types. */
class ForeignExchangeContractTypesTest {
  @Test
  void treatmentKind_publishesStableWireValuesAndParsing() {
    assertEquals(
        List.of("SPOT_TRANSACTION", "UNREALIZED_REMEASUREMENT"),
        ForeignExchangeTreatmentKind.wireValues());
    assertEquals(
        ForeignExchangeTreatmentKind.UNREALIZED_REMEASUREMENT,
        ForeignExchangeTreatmentKind.fromWireValue("UNREALIZED_REMEASUREMENT"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ForeignExchangeTreatmentKind.fromWireValue("REALIZED_SETTLEMENT"));
  }

  @Test
  void quotedExchangeRate_translatesHalfUpAndRejectsInvalidFacts() {
    QuotedExchangeRate halfUpQuote =
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "2"),
            new MonetaryAmount("EUR", "1"),
            LocalDate.parse("2026-04-25"),
            "ECB");
    QuotedExchangeRate truncateQuote =
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "4"),
            new MonetaryAmount("EUR", "1"),
            LocalDate.parse("2026-04-25"),
            "ECB");

    assertEquals(
        new MonetaryAmount("EUR", "1"), halfUpQuote.translate(new MonetaryAmount("USD", "1")));
    assertEquals(
        new MonetaryAmount("EUR", "0"), truncateQuote.translate(new MonetaryAmount("USD", "1")));
    assertEquals(
        "transactionAmount currencyCode must match quoted transaction currency.",
        assertThrows(
                IllegalArgumentException.class,
                () -> halfUpQuote.translate(new MonetaryAmount("EUR", "1")))
            .getMessage());
    assertEquals(
        "transactionAmount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () -> halfUpQuote.translate(new MonetaryAmount("USD", "0")))
            .getMessage());
    assertEquals(
        "Translated functional amount is outside the supported money range.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new QuotedExchangeRate(
                            new MonetaryAmount("USD", "1"),
                            new MonetaryAmount("EUR", Long.toString(Long.MAX_VALUE)),
                            LocalDate.parse("2026-04-25"),
                            "ECB")
                        .translate(new MonetaryAmount("USD", "2")))
            .getMessage());
    assertEquals(
        "Quoted exchange rate must relate distinct transaction and functional currencies.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new QuotedExchangeRate(
                        new MonetaryAmount("USD", "2"),
                        new MonetaryAmount("USD", "1"),
                        LocalDate.parse("2026-04-25"),
                        "ECB"))
            .getMessage());
    assertEquals(
        "quoteSource must not be blank.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new QuotedExchangeRate(
                        new MonetaryAmount("USD", "2"),
                        new MonetaryAmount("EUR", "1"),
                        LocalDate.parse("2026-04-25"),
                        " "))
            .getMessage());
  }

  @Test
  void foreignExchangeDetails_validateCurrenciesAndTranslatedFunctionalAmount() {
    QuotedExchangeRate quotedRate =
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "110"),
            new MonetaryAmount("EUR", "100"),
            LocalDate.parse("2026-04-25"),
            "ECB");

    assertEquals(
        new MonetaryAmount("EUR", "1000"),
        new ForeignExchangeDetails(
                new MonetaryAmount("USD", "1100"),
                new MonetaryAmount("EUR", "1000"),
                quotedRate,
                ForeignExchangeTreatmentKind.SPOT_TRANSACTION)
            .functionalAmount());
    assertEquals(
        "transactionAmount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeDetails(
                        new MonetaryAmount("USD", "0"),
                        new MonetaryAmount("EUR", "1000"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_TRANSACTION))
            .getMessage());
    assertEquals(
        "Foreign-exchange details require distinct transaction and functional currencies.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeDetails(
                        new MonetaryAmount("USD", "1100"),
                        new MonetaryAmount("USD", "1000"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_TRANSACTION))
            .getMessage());
    assertEquals(
        "transactionAmount currencyCode must match quotedExchangeRate transaction currency.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeDetails(
                        new MonetaryAmount("GBP", "1100"),
                        new MonetaryAmount("EUR", "1000"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_TRANSACTION))
            .getMessage());
    assertEquals(
        "functionalAmount currencyCode must match quotedExchangeRate functional currency.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeDetails(
                        new MonetaryAmount("USD", "1100"),
                        new MonetaryAmount("GBP", "1000"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_TRANSACTION))
            .getMessage());
    assertEquals(
        "functionalAmount must equal the half-up translation of transactionAmount through quotedExchangeRate.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new ForeignExchangeDetails(
                        new MonetaryAmount("USD", "1100"),
                        new MonetaryAmount("EUR", "999"),
                        quotedRate,
                        ForeignExchangeTreatmentKind.SPOT_TRANSACTION))
            .getMessage());
  }
}

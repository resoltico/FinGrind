package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractSettlementTemplates.TaxSelectionTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractTemplates.OpeningBalanceTemplateDescriptor;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage tests for package-private posting-template field-presence rules. */
class ContractPostingTemplateFieldRulesTest {
  @Test
  void requireLines_rejectsNullAndSingleLineInputs() {
    IllegalArgumentException missingLines =
        assertThrows(
            IllegalArgumentException.class,
            () -> ContractPostingTemplateFieldRules.requireLines(null, "journal"));
    IllegalArgumentException singleLine =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingTemplateFieldRules.requireLines(
                    List.of(journalLine("1000", JournalLine.EntrySide.DEBIT, "1000")), "journal"));

    assertEquals(
        "lines must contain at least two journal lines for journal.", missingLines.getMessage());
    assertEquals(
        "lines must contain at least two journal lines for journal.", singleLine.getMessage());
  }

  @Test
  void requireLines_acceptsTwoOrMoreJournalLines() {
    assertDoesNotThrow(
        () ->
            ContractPostingTemplateFieldRules.requireLines(
                List.of(
                    journalLine("1000", JournalLine.EntrySide.DEBIT, "1000"),
                    journalLine("2000", JournalLine.EntrySide.CREDIT, "1000")),
                "journal"));
  }

  @Test
  void scalarAndForbiddenFieldRules_coverPositiveAndAbsentBranches() {
    assertEquals(
        "cashAccountCode",
        ContractPostingTemplateFieldRules.requireText("cashAccountCode", "fieldName"));
    assertEquals(
        new MonetaryAmount("EUR", "1250"),
        ContractPostingTemplateScalarFieldRules.requirePositiveAmount(
            new MonetaryAmount("EUR", "1250")));
    assertEquals("3", ContractPostingTemplateScalarFieldRules.requirePositiveQuantity("3"));
    assertEquals(
        new MonetaryAmount("EUR", "995"),
        ContractPostingTemplateScalarFieldRules.requirePositiveUnitCost(
            new MonetaryAmount("EUR", "995")));

    assertEquals(
        "fieldName must not be null.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateFieldRules.requireText(null, "fieldName"))
            .getMessage());
    assertEquals(
        "amount must not be null.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateScalarFieldRules.requirePositiveAmount(null))
            .getMessage());
    assertEquals(
        "amount must carry one positive minor-unit value.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateScalarFieldRules.requirePositiveAmount(
                        new MonetaryAmount("EUR", "0")))
            .getMessage());
    assertEquals(
        "quantity must not be null.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateScalarFieldRules.requirePositiveQuantity(null))
            .getMessage());
    assertEquals(
        "quantity must carry one positive quantity.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateScalarFieldRules.requirePositiveQuantity("0"))
            .getMessage());
    assertEquals(
        "unitCost must not be null.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateScalarFieldRules.requirePositiveUnitCost(null))
            .getMessage());
    assertEquals(
        "unitCost must carry one positive minor-unit value.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateScalarFieldRules.requirePositiveUnitCost(
                        new MonetaryAmount("EUR", "0")))
            .getMessage());

    assertDoesNotThrow(() -> ContractPostingTemplateFieldRules.forbidText(null, "fieldName"));
    assertDoesNotThrow(() -> ContractPostingTemplateScalarFieldRules.forbidAmount(null, "sale"));
    assertDoesNotThrow(
        () -> ContractPostingTemplateScalarFieldRules.forbidQuantity(null, "purchase"));
    assertDoesNotThrow(
        () -> ContractPostingTemplateScalarFieldRules.forbidUnitCost(null, "purchase"));
    assertDoesNotThrow(() -> ContractPostingTemplateFieldRules.forbidLines(null));
    assertDoesNotThrow(() -> ContractPostingTemplateFieldRules.forbidOpeningBalances(null));
    assertDoesNotThrow(() -> ContractPostingTemplateFieldRules.forbidTax(null, "sale"));
    assertDoesNotThrow(() -> ContractPostingTemplateFieldRules.forbidForeignExchange(null, "sale"));
    assertDoesNotThrow(() -> ContractPostingTemplateFieldRules.forbidReversal(null));

    assertEquals(
        "fieldName must be absent for this entryKind.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateFieldRules.forbidText("cash", "fieldName"))
            .getMessage());
    assertEquals(
        "amount must be absent for sale.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateScalarFieldRules.forbidAmount(
                        new MonetaryAmount("EUR", "100"), "sale"))
            .getMessage());
    assertEquals(
        "quantity must be absent for purchase.",
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractPostingTemplateScalarFieldRules.forbidQuantity("2", "purchase"))
            .getMessage());
    assertEquals(
        "unitCost must be absent for purchase.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateScalarFieldRules.forbidUnitCost(
                        new MonetaryAmount("EUR", "100"), "purchase"))
            .getMessage());
    assertEquals(
        "lines must be absent for this entryKind.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateFieldRules.forbidLines(
                        List.of(journalLine("1000", JournalLine.EntrySide.DEBIT, "1000"))))
            .getMessage());
    assertEquals(
        "openingBalances must be absent for this entryKind.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateFieldRules.forbidOpeningBalances(
                        List.of(
                            new OpeningBalanceTemplateDescriptor(
                                "1000",
                                JournalLine.EntrySide.DEBIT,
                                new MonetaryAmount("EUR", "1000")))))
            .getMessage());
    assertEquals(
        "tax must be absent for sale.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateFieldRules.forbidTax(
                        new TaxSelectionTemplateDescriptor("tax-reg-1", "vat-20"), "sale"))
            .getMessage());
    assertEquals(
        "foreignExchange must be absent for sale.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateFieldRules.forbidForeignExchange(
                        new ForeignExchangeTemplateDescriptor(
                            new MonetaryAmount("USD", "1000"),
                            new MonetaryAmount("EUR", "900"),
                            new QuotedExchangeRateTemplateDescriptor(
                                new MonetaryAmount("USD", "100"),
                                new MonetaryAmount("EUR", "90"),
                                "2026-07-06",
                                "ECB"),
                            ForeignExchangeTreatmentKind.SPOT_TRANSACTION),
                        "sale"))
            .getMessage());
    assertEquals(
        "reversal must be absent for this entryKind.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingTemplateFieldRules.forbidReversal(
                        new ReversalTemplateDescriptor(
                            "0197f663-1a08-7000-8c22-0d328f0db4ab", "operator correction")))
            .getMessage());
  }

  private static JournalLineTemplateDescriptor journalLine(
      String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLineTemplateDescriptor(accountCode, side, new MonetaryAmount("EUR", amount));
  }
}

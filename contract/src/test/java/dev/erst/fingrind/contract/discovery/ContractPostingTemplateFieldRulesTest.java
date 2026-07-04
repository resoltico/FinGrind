package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates.JournalLineTemplateDescriptor;
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

  private static JournalLineTemplateDescriptor journalLine(
      String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLineTemplateDescriptor(accountCode, side, new MonetaryAmount("EUR", amount));
  }
}

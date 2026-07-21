package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the no-op template-admission path for ordinary non-inventory business events. */
class PostEntryAdmissionSupportTradingTemplateTest {
  @Test
  void validateTradingTemplateEntryAdmission_leavesNonInventorySaleUntouched() {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();

    PostEntryAdmissionSupport.validateTradingTemplateEntryAdmission(
        violations,
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null),
        BookTemplateId.OWNER_MANAGED_SERVICE,
        "entryKind",
        "SALE_SETTLED");

    assertTrue(violations.isEmpty());
  }

  @Test
  void validateTradingTemplateEntryAdmission_leavesEveryNonInventoryVariantUntouched() {
    List<BookkeepingEntry> nonInventoryEvents =
        List.of(
            new BookkeepingEntry.DirectJournal(
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
                        new JournalLine(
                            new AccountCode("4000"),
                            JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.parse("EUR", "10.00")))),
                null),
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null,
                null),
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null),
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null),
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "1000"),
                null),
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-07"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null),
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000"),
                null),
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null),
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("EUR", "1000"),
                        null),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("EUR", "1000"),
                        null))),
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new ReversalReason("operator correction")),
                null,
                null));

    assertTrue(nonInventoryEvents.stream().allMatch(this::hasNoTradingTemplateViolation));
  }

  @Test
  void validateTradingTemplateEntryAdmission_rejectsEveryInventoryEventOnServiceBooks() {
    List<BookkeepingEntry> inventoryEvents =
        List.of(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                nullOf(),
                nullOf(),
                null,
                null),
            new BookkeepingEntry.PurchaseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2100"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null),
            new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null),
            new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null),
            new InventoryBookkeepingEntryVariants.InventoryWriteDown(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("5000"),
                new MonetaryAmount("EUR", "1000")),
            new InventoryBookkeepingEntryVariants.InventoryShrinkage(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("5000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                null),
            new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("4000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null));

    assertTrue(inventoryEvents.stream().allMatch(this::hasTradingTemplateRejection));
  }

  @Test
  void validateTradingTemplateEntryAdmission_rejectsMissingEntry() {
    assertThrows(
        NullPointerException.class,
        () ->
            PostEntryAdmissionSupport.validateTradingTemplateEntryAdmission(
                new ArrayList<>(),
                nullOf(),
                BookTemplateId.OWNER_MANAGED_SERVICE,
                "entryKind",
                "SALE_SETTLED"));
  }

  private boolean hasNoTradingTemplateViolation(BookkeepingEntry entry) {
    return tradingTemplateViolations(entry).isEmpty();
  }

  private boolean hasTradingTemplateRejection(BookkeepingEntry entry) {
    return tradingTemplateViolations(entry).stream()
        .anyMatch(violation -> "verb-requires-trading-template".equals(violation.code()));
  }

  private static List<BookkeepingPostingRejection.EntrySemanticsViolation>
      tradingTemplateViolations(BookkeepingEntry entry) {
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    PostEntryAdmissionSupport.validateTradingTemplateEntryAdmission(
        violations,
        entry,
        BookTemplateId.OWNER_MANAGED_SERVICE,
        "entryKind",
        entry.entryKind().wireValue());
    return violations;
  }
}

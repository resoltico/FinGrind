package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.equityAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.tradingAccrualBookIdentity;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Focused inventory boundary coverage for the published post-entry semantics policy. */
class PostEntrySemanticsPolicyInventoryBoundaryTest {
  @Test
  void rejectionFor_rejectsInventoryOpeningBalancesThatOmitQuantity() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble inventoryOpeningBook =
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            tradingAccrualBookIdentity(),
            Map.of(
                new AccountCode("inventory"),
                inventoryAssetAccount("inventory"),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.OpeningPosition(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("inventory"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                            MonetaryAmount.of(Money.parse("EUR", "10.00")),
                            null),
                        new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                            new AccountCode("3200"),
                            dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                            MonetaryAmount.of(Money.parse("EUR", "10.00")),
                            null))),
                generatedEvidence("opening-inventory", "opening-balance"),
                requestProvenance("opening-inventory"),
                SourceChannel.CLI),
            inventoryOpeningBook),
        "opening-inventory-requires-quantity");
  }

  @Test
  void rejectionFor_rejectsRawJournalInventoryMovementsBeforeAmountOnlyInventoryCanReachCommit() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble inventoryJournalBook =
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            tradingAccrualBookIdentity(),
            Map.of(
                new AccountCode("inventory"),
                inventoryAssetAccount("inventory"),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));

    assertSingleViolation(
        policy.rejectionFor(
            new PostEntryCommand(
                new BookkeepingEntry.DirectJournal(
                    new dev.erst.fingrind.core.JournalEntry(
                        LocalDate.parse("2026-04-07"),
                        List.of(
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("inventory"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                Money.parse("EUR", "10.00")),
                            new dev.erst.fingrind.core.JournalLine(
                                new AccountCode("3200"),
                                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                Money.parse("EUR", "10.00")))),
                    null),
                generatedEvidence("direct-journal-inventory", "working-note"),
                requestProvenance("direct-journal-inventory"),
                SourceChannel.CLI),
            inventoryJournalBook),
        "raw-journal-touches-inventory");
  }

  @Test
  void rejectionFor_namesTheFirstInventoryAccountInCallerLineOrder() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble inventoryJournalBook =
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            tradingAccrualBookIdentity(),
            Map.of(
                new AccountCode("inventory-first"),
                inventoryAssetAccount("inventory-first"),
                new AccountCode("inventory-second"),
                inventoryAssetAccount("inventory-second"),
                new AccountCode("3200"),
                equityAccount("3200", FinancialPositionLineClassification.EQUITY_CONTRIBUTION)));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertSingleViolation(
            policy.rejectionFor(
                new PostEntryCommand(
                    new BookkeepingEntry.DirectJournal(
                        new dev.erst.fingrind.core.JournalEntry(
                            LocalDate.parse("2026-04-07"),
                            List.of(
                                new dev.erst.fingrind.core.JournalLine(
                                    new AccountCode("inventory-first"),
                                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                    Money.parse("EUR", "10.00")),
                                new dev.erst.fingrind.core.JournalLine(
                                    new AccountCode("inventory-second"),
                                    dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                                    Money.parse("EUR", "10.00")),
                                new dev.erst.fingrind.core.JournalLine(
                                    new AccountCode("3200"),
                                    dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                                    Money.parse("EUR", "20.00")))),
                        null),
                    generatedEvidence("ordered-inventory-journal", "working-note"),
                    requestProvenance("ordered-inventory-journal"),
                    SourceChannel.CLI),
                inventoryJournalBook),
            "raw-journal-touches-inventory");

    assertEquals(
        "entryKind 'DIRECT_JOURNAL' contains lines[].accountCode 'inventory-first', which resolves to the inventory role. Raw direct-journal requests cannot create or change exact inventory quantity.",
        rejection.violations().getFirst().message());
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolations assertSingleViolation(
      java.util.Optional<BookkeepingPostingRejection> rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations semanticViolations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            rejection.orElseThrow(() -> new AssertionError("Expected rejection.")));
    assertEquals(1, semanticViolations.violations().size());
    assertEquals(expectedCode, semanticViolations.violations().getFirst().code());
    return semanticViolations;
  }
}

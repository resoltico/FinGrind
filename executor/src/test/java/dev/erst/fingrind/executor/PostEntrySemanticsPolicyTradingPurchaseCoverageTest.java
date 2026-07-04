package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.account;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.accrualBookIdentity;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.payableAccount;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.tradingAccrualBookIdentity;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.requestProvenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage for trading-template purchase-verb admission and rejection paths. */
class PostEntrySemanticsPolicyTradingPurchaseCoverageTest {
  @Test
  void rejectionFor_serviceDoctrineRejectsInventoryPurchaseVerbsBeforeMissingAccountLookup() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble serviceBookWithoutInventoryAccount =
        new PostingValidationStoreDouble(
            accrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("2100"),
                payableAccount("2100")));

    assertSingleViolation(
        policy.rejectionFor(
            settledPurchase("purchase-settled-service", "purchase-receipt"),
            serviceBookWithoutInventoryAccount),
        "verb-requires-trading-template");
    assertSingleViolation(
        policy.rejectionFor(
            creditPurchase("purchase-credit-service", "supplier-invoice"),
            serviceBookWithoutInventoryAccount),
        "verb-requires-trading-template");
  }

  @Test
  void rejectionFor_tradingDoctrineAdmitsInventoryPurchaseVerbsWhenAccountsMatch() {
    PostEntrySemanticsPolicy policy = PostEntrySemanticsPolicy.currentKernel();
    PostingValidationStoreDouble tradingBook =
        new PostingValidationStoreDouble(
            tradingAccrualBookIdentity(),
            Map.of(
                new AccountCode("1000"),
                account("1000", AccountType.ASSET),
                new AccountCode("1400"),
                inventoryAssetAccount("1400"),
                new AccountCode("2100"),
                payableAccount("2100")));

    assertTrue(
        policy
            .rejectionFor(
                settledPurchase("purchase-settled-trading", "purchase-receipt"), tradingBook)
            .isEmpty());
    assertTrue(
        policy
            .rejectionFor(
                creditPurchase("purchase-credit-trading", "supplier-invoice"), tradingBook)
            .isEmpty());
  }

  private static void assertSingleViolation(
      Optional<BookkeepingPostingRejection> rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations violations =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection.orElseThrow());
    assertEquals(1, violations.violations().size());
    assertEquals(expectedCode, violations.violations().getFirst().code());
  }

  private static PostEntryCommand settledPurchase(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            null),
        ExecutorAccountingTestSupport.generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }

  private static PostEntryCommand creditPurchase(String token, String sourceDocumentType) {
    return new PostEntryCommand(
        new BookkeepingEntry.PurchaseOnCredit(
            LocalDate.parse("2026-04-07"),
            new AccountCode("1400"),
            new AccountCode("2100"),
            MonetaryAmount.of(Money.parse("EUR", "10.00"))),
        ExecutorAccountingTestSupport.generatedEvidence(token, sourceDocumentType),
        requestProvenance(token),
        SourceChannel.CLI);
  }
}

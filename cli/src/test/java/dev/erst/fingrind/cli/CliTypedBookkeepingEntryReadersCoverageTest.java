package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Direct coverage for the typed-entry reader catalog. */
class CliTypedBookkeepingEntryReadersCoverageTest extends CliRequestReaderTestSupport {
  @Test
  void read_rejectsDirectJournalKind() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliTypedBookkeepingEntryReaders.read(
                    CliJsonObjectMappers.configuredObjectMapper().createObjectNode(),
                    BookkeepingEntryKind.DIRECT_JOURNAL));

    assertEquals("Direct journal entries are handled separately.", exception.getMessage());
  }

  @Test
  void read_supportsEveryTypedEntryKind() throws IOException {
    assertEquals(
        new AccountCode("1400"),
        assertInstanceOf(
                BookkeepingEntry.PurchaseSettled.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "inventoryAccountCode": "1400",
                          "cashAccountCode": "1000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.PURCHASE_SETTLED))
            .inventoryAccountCode());
    assertEquals(
        new AccountCode("2100"),
        assertInstanceOf(
                BookkeepingEntry.PurchaseOnCredit.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "inventoryAccountCode": "1400",
                          "payableAccountCode": "2100",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.PURCHASE_ON_CREDIT))
            .payableAccountCode());
    assertEquals(
        new AccountCode("1000"),
        assertInstanceOf(
                BookkeepingEntry.SaleSettled.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "cashAccountCode": "1000",
                          "revenueAccountCode": "4000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.SALE_SETTLED))
            .cashAccountCode());
    assertEquals(
        new AccountCode("1100"),
        assertInstanceOf(
                BookkeepingEntry.SaleOnCredit.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "receivableAccountCode": "1100",
                          "revenueAccountCode": "4000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.SALE_ON_CREDIT))
            .receivableAccountCode());
    assertEquals(
        new AccountCode("5000"),
        assertInstanceOf(
                BookkeepingEntry.ExpenseSettled.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "expenseAccountCode": "5000",
                          "cashAccountCode": "1000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.EXPENSE_SETTLED))
            .expenseAccountCode());
    assertEquals(
        new AccountCode("2100"),
        assertInstanceOf(
                BookkeepingEntry.ExpenseOnCredit.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "expenseAccountCode": "5000",
                          "payableAccountCode": "2100",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.EXPENSE_ON_CREDIT))
            .payableAccountCode());
    assertEquals(
        new AccountCode("1100"),
        assertInstanceOf(
                BookkeepingEntry.Receipt.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "cashAccountCode": "1000",
                          "receivableAccountCode": "1100",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.RECEIPT))
            .receivableAccountCode());
    assertEquals(
        new AccountCode("2100"),
        assertInstanceOf(
                BookkeepingEntry.Payment.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "payableAccountCode": "2100",
                          "cashAccountCode": "1000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.PAYMENT))
            .payableAccountCode());
    assertEquals(
        new AccountCode("3000"),
        assertInstanceOf(
                BookkeepingEntry.OwnerContribution.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "cashAccountCode": "1000",
                          "equityAccountCode": "3000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.OWNER_CONTRIBUTION))
            .equityAccountCode());
    assertEquals(
        new AccountCode("3010"),
        assertInstanceOf(
                BookkeepingEntry.OwnerWithdrawal.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "equityAccountCode": "3010",
                          "cashAccountCode": "1000",
                          "amount": %s
                        }
                        """
                            .formatted(eurMoneyJson("1000"))),
                    BookkeepingEntryKind.OWNER_WITHDRAWAL))
            .equityAccountCode());
    assertEquals(
        2,
        assertInstanceOf(
                BookkeepingEntry.OpeningPosition.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "openingBalances": [
                            {
                              "accountCode": "1000",
                              "side": "DEBIT",
                              "amount": %s
                            },
                            {
                              "accountCode": "3000",
                              "side": "CREDIT",
                              "amount": %s
                            }
                          ]
                        }
                        """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000"))),
                    BookkeepingEntryKind.OPENING_POSITION))
            .balances()
            .size());
    assertEquals(
        "operator reversal",
        assertInstanceOf(
                BookkeepingEntry.Reversal.class,
                CliTypedBookkeepingEntryReaders.read(
                    rootNode(
                        """
                        {
                          "effectiveDate": "2026-04-07",
                          "reversal": {
                            "priorPostingId": "posting-1",
                            "reason": "operator reversal"
                          }
                        }
                        """),
                    BookkeepingEntryKind.REVERSAL))
            .reversal()
            .reason()
            .value());
  }

  private static ObjectNode rootNode(String json) throws IOException {
    return (ObjectNode) CliJsonObjectMappers.configuredObjectMapper().readTree(json);
  }
}

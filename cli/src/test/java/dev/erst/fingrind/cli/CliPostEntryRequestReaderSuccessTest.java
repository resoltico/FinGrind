package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderSuccessTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_readsExpenseEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "EXPENSE_SETTLED",
              "effectiveDate": "2026-04-07",
              "expenseAccountCode": "5000",
              "cashAccountCode": "1000",
              "amount": %s,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-expense",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.ExpenseSettled entry =
        assertInstanceOf(BookkeepingEntry.ExpenseSettled.class, command.entry());

    assertEquals(BookkeepingEntryKind.EXPENSE_SETTLED, entry.entryKind());
    assertEquals(new AccountCode("5000"), entry.expenseAccountCode());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsCreditSaleAndExpenseEntriesWithTaxSelection() {
    PostEntryCommand saleCommand =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_ON_CREDIT",
              "effectiveDate": "2026-04-07",
              "receivableAccountCode": "1100",
              "revenueAccountCode": "2000",
              "amount": %s,
              "tax": {
                "taxRegistrationId": "vat-lv",
                "taxCode": "vat-standard-sale"
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-credit-sale",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));
    PostEntryCommand expenseCommand =
        readFromStandardInput(
            """
            {
              "entryKind": "EXPENSE_ON_CREDIT",
              "effectiveDate": "2026-04-07",
              "expenseAccountCode": "5000",
              "payableAccountCode": "2100",
              "amount": %s,
              "tax": {
                "taxRegistrationId": "vat-lv",
                "taxCode": "vat-standard-expense"
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-credit-expense",
                "causationId": "cause-2"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.SaleOnCredit sale =
        assertInstanceOf(BookkeepingEntry.SaleOnCredit.class, saleCommand.entry());
    BookkeepingEntry.ExpenseOnCredit expense =
        assertInstanceOf(BookkeepingEntry.ExpenseOnCredit.class, expenseCommand.entry());
    var saleTaxSelection = Objects.requireNonNull(sale.taxSelection());
    var expenseTaxSelection = Objects.requireNonNull(expense.taxSelection());

    assertEquals(BookkeepingEntryKind.SALE_ON_CREDIT, sale.entryKind());
    assertEquals(new AccountCode("1100"), sale.receivableAccountCode());
    assertEquals("vat-standard-sale", saleTaxSelection.taxCode().value());
    assertEquals(BookkeepingEntryKind.EXPENSE_ON_CREDIT, expense.entryKind());
    assertEquals(new AccountCode("2100"), expense.payableAccountCode());
    assertEquals("vat-standard-expense", expenseTaxSelection.taxCode().value());
  }

  @Test
  void readPostEntryCommand_readsSalesWithInventoryRelief() {
    PostEntryCommand settledSaleCommand =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "4000",
              "amount": %s,
              "inventoryRelief": {
                "inventoryAccountCode": "1400",
                "costOfSalesAccountCode": "5000",
                "quantity": "4"
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-sale-settled-relief",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));
    PostEntryCommand creditSaleCommand =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_ON_CREDIT",
              "effectiveDate": "2026-04-07",
              "receivableAccountCode": "1100",
              "revenueAccountCode": "4000",
              "amount": %s,
              "inventoryRelief": {
                "inventoryAccountCode": "1400",
                "costOfSalesAccountCode": "5000",
                "quantity": "4"
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-sale-credit-relief",
                "causationId": "cause-2"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.SaleSettled settledSale =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, settledSaleCommand.entry());
    BookkeepingEntry.SaleOnCredit creditSale =
        assertInstanceOf(BookkeepingEntry.SaleOnCredit.class, creditSaleCommand.entry());
    var settledInventoryRelief = Objects.requireNonNull(settledSale.inventoryRelief());
    var creditInventoryRelief = Objects.requireNonNull(creditSale.inventoryRelief());

    assertEquals(new AccountCode("1400"), settledInventoryRelief.inventoryAccountCode());
    assertEquals(new AccountCode("5000"), settledInventoryRelief.costOfSalesAccountCode());
    assertEquals("4", settledInventoryRelief.quantity().value());
    assertEquals(new AccountCode("1400"), creditInventoryRelief.inventoryAccountCode());
    assertEquals(new AccountCode("5000"), creditInventoryRelief.costOfSalesAccountCode());
    assertEquals("4", creditInventoryRelief.quantity().value());
  }

  @Test
  void readPostEntryCommand_allowsExplicitNullInventoryRelief() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "4000",
              "amount": %s,
              "inventoryRelief": null,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-sale-null-relief",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.SaleSettled sale =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertNull(sale.inventoryRelief());
  }

  @Test
  void readPostEntryCommand_readsReceiptAndPaymentEntriesWithSettlementAdjunct() {
    PostEntryCommand receiptCommand =
        readFromStandardInput(
            """
            {
              "entryKind": "RECEIPT",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "receivableAccountCode": "1100",
              "amount": %s,
              "settlementAdjunct": {
                "accountCode": "6100",
                "amount": %s
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-receipt",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000"), eurMoneyJson("50")));
    PostEntryCommand paymentCommand =
        readFromStandardInput(
            """
            {
              "entryKind": "PAYMENT",
              "effectiveDate": "2026-04-07",
              "payableAccountCode": "2100",
              "cashAccountCode": "1000",
              "amount": %s,
              "settlementAdjunct": {
                "accountCode": "6200",
                "amount": %s
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-payment",
                "causationId": "cause-2"
              }
            }
            """
                .formatted(eurMoneyJson("1000"), eurMoneyJson("75")));

    BookkeepingEntry.Receipt receipt =
        assertInstanceOf(BookkeepingEntry.Receipt.class, receiptCommand.entry());
    BookkeepingEntry.Payment payment =
        assertInstanceOf(BookkeepingEntry.Payment.class, paymentCommand.entry());
    var receiptAdjunct = Objects.requireNonNull(receipt.settlementAdjunct());
    var paymentAdjunct = Objects.requireNonNull(payment.settlementAdjunct());

    assertEquals(BookkeepingEntryKind.RECEIPT, receipt.entryKind());
    assertEquals(new AccountCode("6100"), receiptAdjunct.accountCode());
    assertEquals("50", receiptAdjunct.amount().minorUnits());
    assertEquals(BookkeepingEntryKind.PAYMENT, payment.entryKind());
    assertEquals(new AccountCode("6200"), paymentAdjunct.accountCode());
    assertEquals("75", paymentAdjunct.amount().minorUnits());
  }

  @Test
  void readPostEntryCommand_allowsReceiptWithoutSettlementAdjunct() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "RECEIPT",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "receivableAccountCode": "1100",
              "amount": %s,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-receipt-plain",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.Receipt receipt =
        assertInstanceOf(BookkeepingEntry.Receipt.class, command.entry());

    assertEquals(BookkeepingEntryKind.RECEIPT, receipt.entryKind());
    assertNull(receipt.settlementAdjunct());
  }

  @Test
  void readPostEntryCommand_allowsExplicitNullSettlementAdjunct() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "PAYMENT",
              "effectiveDate": "2026-04-07",
              "payableAccountCode": "2100",
              "cashAccountCode": "1000",
              "amount": %s,
              "settlementAdjunct": null,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-payment-plain",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.Payment payment =
        assertInstanceOf(BookkeepingEntry.Payment.class, command.entry());

    assertEquals(BookkeepingEntryKind.PAYMENT, payment.entryKind());
    assertNull(payment.settlementAdjunct());
  }

  @Test
  void readPostEntryCommand_readsOwnerContributionEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "OWNER_CONTRIBUTION",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "equityAccountCode": "3000",
              "amount": %s,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-owner-contribution",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.OwnerContribution entry =
        assertInstanceOf(BookkeepingEntry.OwnerContribution.class, command.entry());

    assertEquals(BookkeepingEntryKind.OWNER_CONTRIBUTION, entry.entryKind());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(new AccountCode("3000"), entry.equityAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsOwnerWithdrawalEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "OWNER_WITHDRAWAL",
              "effectiveDate": "2026-04-07",
              "equityAccountCode": "3000",
              "cashAccountCode": "1000",
              "amount": %s,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-owner-withdrawal",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.OwnerWithdrawal entry =
        assertInstanceOf(BookkeepingEntry.OwnerWithdrawal.class, command.entry());

    assertEquals(BookkeepingEntryKind.OWNER_WITHDRAWAL, entry.entryKind());
    assertEquals(new AccountCode("3000"), entry.equityAccountCode());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsFromFile() throws IOException {
    Path requestFile = writeRequest(validRequestJson(true));
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    BookkeepingEntry.Reversal entry =
        assertInstanceOf(BookkeepingEntry.Reversal.class, command.entry());

    assertEquals("idem-1", command.requestProvenance().idempotencyKey().value());
    assertEquals(
        Optional.of(
            new ReversalReference(
                new dev.erst.fingrind.core.PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b"))),
        entry.reversal().reversalReference());
    assertEquals(
        Optional.of(new ReversalReason("operator reversal")), entry.reversal().reversalReason());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsFromStandardInputWithoutReversal() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(validRequestJson(false).getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));
    BookkeepingEntry.SaleSettled entry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE_SETTLED, entry.entryKind());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(new AccountCode("2000"), entry.revenueAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsScopedSaleWhenOperationRequiresSale() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(validRequestJson(false).getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command =
        requestReader.readPostEntryCommand(Path.of("-"), OperationId.RECORD_SALE_SETTLED);
    BookkeepingEntry.SaleSettled entry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE_SETTLED, entry.entryKind());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_acceptsExplicitNullTaxSelection() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": %s,
              "tax": null,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-sale-null-tax",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.SaleSettled entry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE_SETTLED, entry.entryKind());
    assertNull(entry.taxSelection());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsOwnedForeignExchangeFacts() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": %s,
              "foreignExchange": {
                "transactionAmount": %s,
                "functionalAmount": %s,
                "quotedRate": {
                  "transactionCurrencyAmount": %s,
                  "functionalCurrencyAmount": %s,
                  "quotedOn": "2026-04-06",
                  "quoteSource": "ecb-spot"
                },
                "treatmentKind": "SPOT_TRANSACTION"
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-sale-fx",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(
                    eurMoneyJson("9200"),
                    moneyJson("USD", "10000"),
                    eurMoneyJson("9200"),
                    moneyJson("USD", "10000"),
                    eurMoneyJson("9200")));

    BookkeepingEntry.SaleSettled entry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertNotNull(entry.foreignExchangeDetails());
    assertEquals("USD", entry.foreignExchangeDetails().transactionAmount().currencyCode());
    assertEquals("9200", entry.foreignExchangeDetails().functionalAmount().minorUnits());
    assertEquals(
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION,
        entry.foreignExchangeDetails().treatmentKind());
  }

  @Test
  void readPostEntryCommand_acceptsExplicitNullForeignExchange() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": %s,
              "foreignExchange": null,
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-sale-null-foreign-exchange",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("9200")));

    BookkeepingEntry.SaleSettled entry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertNull(entry.foreignExchangeDetails());
  }

  @Test
  void readPostEntryCommand_ignoresNonowningTopicsWhenTheyDoNotDeclareOneRequiredEntryKind()
      throws IOException {
    ObjectNode rootNode =
        (ObjectNode)
            CliJsonObjectMappers.configuredObjectMapper().readTree(validRequestJson(false));

    PostEntryCommand command =
        CliPostingRequestParser.readPostEntryCommand(rootNode, OperationId.HELP);
    BookkeepingEntry.SaleSettled entry =
        assertInstanceOf(BookkeepingEntry.SaleSettled.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE_SETTLED, entry.entryKind());
  }

  @Test
  void readPostEntryCommand_readsDirectJournalEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "DIRECT_JOURNAL",
              "effectiveDate": "2026-04-07",
              "lines": [
                {
                  "accountCode": "1000",
                  "side": "DEBIT",
                  "amount": %s
                },
                {
                  "accountCode": "2000",
                  "side": "CREDIT",
                  "amount": %s
                }
              ],
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "idem-direct-journal",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")));

    BookkeepingEntry.DirectJournal entry =
        assertInstanceOf(BookkeepingEntry.DirectJournal.class, command.entry());

    assertEquals(BookkeepingEntryKind.DIRECT_JOURNAL, entry.entryKind());
    assertEquals(2, entry.lines().size());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsScopedDirectJournalWhenOperationRequiresDirectJournal() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "DIRECT_JOURNAL",
                  "effectiveDate": "2026-04-07",
                  "lines": [
                    {
                      "accountCode": "1000",
                      "side": "DEBIT",
                      "amount": %s
                    },
                    {
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "amount": %s
                    }
                  ],
                  "provenance": {
                    "commandId": "018f0000-0000-7000-8000-000000000001",
                    "idempotencyKey": "idem-direct-journal",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command =
        requestReader.readPostEntryCommand(Path.of("-"), OperationId.POST_ENTRY);
    BookkeepingEntry.DirectJournal entry =
        assertInstanceOf(BookkeepingEntry.DirectJournal.class, command.entry());

    assertEquals(BookkeepingEntryKind.DIRECT_JOURNAL, entry.entryKind());
    assertEquals(2, entry.lines().size());
  }

  @Test
  void readPostEntryCommand_readsOpeningPosition() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "OPENING_POSITION",
                  "effectiveDate": "2026-04-07",
                  "openingBalances": [
                    {
                      "accountCode": "1000",
                      "side": "DEBIT",
                      "amount": %s
                    },
                    {
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "amount": %s
                    }
                  ],
                  "provenance": {
                    "commandId": "018f0000-0000-7000-8000-000000000001",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));
    BookkeepingEntry.OpeningPosition entry =
        assertInstanceOf(BookkeepingEntry.OpeningPosition.class, command.entry());

    assertEquals(2, entry.balances().size());
  }

  @Test
  void readPostEntryCommand_readsReversal() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "REVERSAL",
                  "effectiveDate": "2026-04-07",
                  "reversal": {
                    "priorPostingId": "e888fd00-a501-341d-9a6b-8d9059757d1b",
                    "reason": "operator reversal"
                  },
                  "provenance": {
                    "commandId": "018f0000-0000-7000-8000-000000000001",
                    "idempotencyKey": "idem-reversal",
                    "causationId": "cause-1"
                  }
                }
                """)
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));
    BookkeepingEntry.Reversal entry =
        assertInstanceOf(BookkeepingEntry.Reversal.class, command.entry());

    assertEquals(
        Optional.of(
            new ReversalReference(
                new dev.erst.fingrind.core.PostingId("e888fd00-a501-341d-9a6b-8d9059757d1b"))),
        entry.reversal().reversalReference());
    assertEquals(
        Optional.of(new ReversalReason("operator reversal")), entry.reversal().reversalReason());
    assertThrows(IllegalStateException.class, entry::journalEntry);
  }

  @Test
  void readPostEntryCommand_treatsExplicitNullOptionalTextFieldsAsEmpty() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "SALE_SETTLED",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "commandId": "018f0000-0000-7000-8000-000000000001",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1",
                    "correlationId": null
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.requestProvenance().correlationId());
  }

  @Test
  void readPostEntryCommand_readsApprovalEvidence() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "SALE_SETTLED",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "commandId": "018f0000-0000-7000-8000-000000000001",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  },
                  "evidence": {
                    "sourceDocuments": [
                      {
                        "sourceDocumentId": "cash-receipt-1",
                        "sourceDocumentType": "cash-receipt",
                        "documentDate": "2026-04-07"
                      }
                    ],
                    "approvals": [
                      {
                        "approvalId": "approval-1",
                        "approvalType": "manager-signoff",
                        "approverReference": "approver-1",
                        "approverType": "PERSON",
                        "decision": "APPROVED",
                        "approvedAt": "2026-04-07T10:20:30Z"
                      }
                    ]
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(1, command.evidence().sourceDocuments().size());
    assertEquals(1, command.evidence().approvals().size());
    assertEquals("approval-1", command.evidence().approvals().get(0).approvalId().value());
    assertEquals("manager-signoff", command.evidence().approvals().get(0).approvalType().value());
  }

  private static PostEntryCommand readFromStandardInput(String requestJson) {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(withEvidence(requestJson).getBytes(StandardCharsets.UTF_8)));
    return requestReader.readPostEntryCommand(Path.of("-"));
  }
}

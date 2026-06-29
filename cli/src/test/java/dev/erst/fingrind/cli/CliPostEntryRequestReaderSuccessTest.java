package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
              "entryKind": "EXPENSE",
              "effectiveDate": "2026-04-07",
              "expenseAccountCode": "5000",
              "cashAccountCode": "1000",
              "amount": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-expense",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.Expense entry =
        assertInstanceOf(BookkeepingEntry.Expense.class, command.entry());

    assertEquals(BookkeepingEntryKind.EXPENSE, entry.entryKind());
    assertEquals(new AccountCode("5000"), entry.expenseAccountCode());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
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
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
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
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
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
        Optional.of(new ReversalReference(new dev.erst.fingrind.core.PostingId("posting-0"))),
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
    BookkeepingEntry.Sale entry = assertInstanceOf(BookkeepingEntry.Sale.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE, entry.entryKind());
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
        requestReader.readPostEntryCommand(Path.of("-"), OperationId.RECORD_SALE);
    BookkeepingEntry.Sale entry = assertInstanceOf(BookkeepingEntry.Sale.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE, entry.entryKind());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_acceptsExplicitNullTaxSelection() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": %s,
              "tax": null,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-sale-null-tax",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.Sale entry = assertInstanceOf(BookkeepingEntry.Sale.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE, entry.entryKind());
    assertNull(entry.taxSelection());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsOwnedForeignExchangeFacts() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE",
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
                "treatmentKind": "SPOT_SETTLEMENT"
              },
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
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

    BookkeepingEntry.Sale entry = assertInstanceOf(BookkeepingEntry.Sale.class, command.entry());

    assertNotNull(entry.foreignExchangeDetails());
    assertEquals("USD", entry.foreignExchangeDetails().transactionAmount().currencyCode());
    assertEquals("9200", entry.foreignExchangeDetails().functionalAmount().minorUnits());
    assertEquals(
        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT,
        entry.foreignExchangeDetails().treatmentKind());
  }

  @Test
  void readPostEntryCommand_acceptsExplicitNullForeignExchange() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "SALE",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": %s,
              "foreignExchange": null,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-sale-null-foreign-exchange",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("9200")));

    BookkeepingEntry.Sale entry = assertInstanceOf(BookkeepingEntry.Sale.class, command.entry());

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
    BookkeepingEntry.Sale entry = assertInstanceOf(BookkeepingEntry.Sale.class, command.entry());

    assertEquals(BookkeepingEntryKind.SALE, entry.entryKind());
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
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
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
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
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
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
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
                  "lines": [
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
                  ],
                  "reversal": {
                    "priorPostingId": "posting-0",
                    "reason": "operator reversal"
                  },
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-reversal",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));
    BookkeepingEntry.Reversal entry =
        assertInstanceOf(BookkeepingEntry.Reversal.class, command.entry());

    assertEquals(2, entry.lines().size());
  }

  @Test
  void readPostEntryCommand_treatsExplicitNullOptionalTextFieldsAsEmpty() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "SALE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
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
                  "entryKind": "SALE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
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
                        "approverId": "approver-1",
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

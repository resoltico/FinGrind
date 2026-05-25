package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderSuccessTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_readsCashExpenseEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "CASH_EXPENSE",
              "effectiveDate": "2026-04-07",
              "expenseAccountCode": "5000",
              "cashAccountCode": "1000",
              "amount": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-cash-expense",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.CashExpense entry =
        assertInstanceOf(BookkeepingEntry.CashExpense.class, command.entry());

    assertEquals(new AccountCode("5000"), entry.expenseAccountCode());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsEquityContributionEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "EQUITY_CONTRIBUTION",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "equityAccountCode": "3000",
              "amount": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-equity-contribution",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.EquityContribution entry =
        assertInstanceOf(BookkeepingEntry.EquityContribution.class, command.entry());

    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(new AccountCode("3000"), entry.equityAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsEquityWithdrawalEntries() {
    PostEntryCommand command =
        readFromStandardInput(
            """
            {
              "entryKind": "EQUITY_WITHDRAWAL",
              "effectiveDate": "2026-04-07",
              "equityAccountCode": "3000",
              "cashAccountCode": "1000",
              "amount": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-equity-withdrawal",
                "causationId": "cause-1"
              }
            }
            """
                .formatted(eurMoneyJson("1000")));

    BookkeepingEntry.EquityWithdrawal entry =
        assertInstanceOf(BookkeepingEntry.EquityWithdrawal.class, command.entry());

    assertEquals(new AccountCode("3000"), entry.equityAccountCode());
    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsFromFile() throws IOException {
    Path requestFile = writeRequest(validRequestJson(true));
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    BookkeepingEntry.ReversalAdjustment entry =
        assertInstanceOf(BookkeepingEntry.ReversalAdjustment.class, command.entry());

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
    BookkeepingEntry.CashRevenue entry =
        assertInstanceOf(BookkeepingEntry.CashRevenue.class, command.entry());

    assertEquals(new AccountCode("1000"), entry.cashAccountCode());
    assertEquals(new AccountCode("2000"), entry.revenueAccountCode());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsCorrectionAdjustmentWithoutReversal() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CORRECTION_ADJUSTMENT",
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
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));
    BookkeepingEntry.CorrectionAdjustment entry =
        assertInstanceOf(BookkeepingEntry.CorrectionAdjustment.class, command.entry());

    assertEquals(2, entry.lines().size());
  }

  @Test
  void readPostEntryCommand_readsOpeningBalanceAdjustment() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "OPENING_BALANCE_ADJUSTMENT",
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
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-opening-balance",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));
    BookkeepingEntry.OpeningBalanceAdjustment entry =
        assertInstanceOf(BookkeepingEntry.OpeningBalanceAdjustment.class, command.entry());

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
                  "entryKind": "CASH_REVENUE",
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
                  "entryKind": "CASH_REVENUE",
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
                        "documentDate": "2026-04-07",
                        "capturedAt": "2026-04-07T10:15:30Z",
                        "storageLocator": "vault://fixtures/cash-receipt-1",
                        "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
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

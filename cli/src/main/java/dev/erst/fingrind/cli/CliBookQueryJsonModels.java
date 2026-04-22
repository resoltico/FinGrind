package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.CliJsonModelValidation.requireText;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Posting, account, and query JSON records emitted by the CLI transport layer. */
interface CliBookQueryJsonModels {

  record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt) {
    public DeclaredAccountPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
    }
  }

  record PostingPayload(
      String postingId,
      String effectiveDate,
      String recordedAt,
      String actorId,
      String actorType,
      String commandId,
      String idempotencyKey,
      String causationId,
      @Nullable String correlationId,
      String sourceChannel,
      @Nullable ReversalPayload reversal,
      List<JournalLinePayload> lines) {
    public PostingPayload {
      postingId = requireText(postingId, "postingId");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      actorId = requireText(actorId, "actorId");
      actorType = requireText(actorType, "actorType");
      commandId = requireText(commandId, "commandId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      causationId = requireText(causationId, "causationId");
      correlationId = requireOptionalText(correlationId, "correlationId");
      sourceChannel = requireText(sourceChannel, "sourceChannel");
      lines = copyList(lines);
    }
  }

  record ReversalPayload(String priorPostingId, String reason) {
    public ReversalPayload {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
      reason = requireText(reason, "reason");
    }
  }

  record JournalLinePayload(String accountCode, String side, String currencyCode, String amount) {
    public JournalLinePayload {
      accountCode = requireText(accountCode, "accountCode");
      side = requireText(side, "side");
      currencyCode = requireText(currencyCode, "currencyCode");
      amount = requireText(amount, "amount");
    }
  }

  record PostingListPayload(int limit, @Nullable String nextCursor, List<PostingPayload> postings) {
    public PostingListPayload {
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      postings = copyList(postings);
    }
  }

  record AccountListPayload(
      int limit, @Nullable String nextCursor, List<DeclaredAccountPayload> accounts) {
    public AccountListPayload {
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      accounts = copyList(accounts);
    }
  }

  record AccountBalancePayload(
      String accountCode,
      String accountName,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> balances) {
    public AccountBalancePayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      balances = copyList(balances);
    }
  }

  record BalanceBucketPayload(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      String balanceSide) {
    public BalanceBucketPayload {
      currencyCode = requireText(currencyCode, "currencyCode");
      debitTotal = requireText(debitTotal, "debitTotal");
      creditTotal = requireText(creditTotal, "creditTotal");
      netAmount = requireText(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }
}

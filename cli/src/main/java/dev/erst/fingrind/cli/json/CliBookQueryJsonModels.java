package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Posting, account, and query JSON records emitted by the CLI transport layer. */
public interface CliBookQueryJsonModels {

  record DeclaredAccountPayload(
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String normalBalance,
      boolean active,
      String declaredAt)
      implements CliSuccessPayload {
    public DeclaredAccountPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
    }
  }

  record PostingPayload(
      String postingId,
      String postingKind,
      String reversalState,
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
      List<JournalLinePayload> lines)
      implements CliSuccessPayload {
    public PostingPayload {
      postingId = requireText(postingId, "postingId");
      postingKind = requireText(postingKind, "postingKind");
      reversalState = requireText(reversalState, "reversalState");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
      actorId = requireText(actorId, "actorId");
      actorType = requireText(actorType, "actorType");
      commandId = requireText(commandId, "commandId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      causationId = requireText(causationId, "causationId");
      correlationId = requireOptionalText(correlationId, "correlationId");
      sourceChannel = requireText(sourceChannel, "sourceChannel");
      lines = copyList(lines, "lines");
    }
  }

  record ReversalPayload(String priorPostingId, String reason) {
    public ReversalPayload {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
      reason = requireText(reason, "reason");
    }
  }

  record JournalLinePayload(String accountCode, String side, MonetaryAmount amount) {
    public JournalLinePayload {
      accountCode = requireText(accountCode, "accountCode");
      side = requireText(side, "side");
      Objects.requireNonNull(amount, "amount");
    }
  }

  record PostingListPayload(int limit, @Nullable String nextCursor, List<PostingPayload> postings)
      implements CliSuccessPayload {
    public PostingListPayload {
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      postings = copyList(postings, "postings");
    }
  }

  record AccountListPayload(
      int limit, @Nullable String nextCursor, List<DeclaredAccountPayload> accounts)
      implements CliSuccessPayload {
    public AccountListPayload {
      requirePositive(limit, "limit");
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      accounts = copyList(accounts, "accounts");
    }
  }

  record AccountBalancePayload(
      CliReportJsonModels.ReportContextPayload context,
      String accountCode,
      String accountName,
      String accountType,
      String accountRole,
      String normalBalance,
      boolean active,
      String declaredAt,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      List<BalanceBucketPayload> balances)
      implements CliSuccessPayload {
    public AccountBalancePayload {
      Objects.requireNonNull(context, "context");
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      accountRole = requireText(accountRole, "accountRole");
      normalBalance = requireText(normalBalance, "normalBalance");
      declaredAt = requireText(declaredAt, "declaredAt");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      balances = copyList(balances, "balances");
    }
  }

  record BalanceBucketPayload(
      MonetaryAmount debitTotal,
      MonetaryAmount creditTotal,
      MonetaryAmount netAmount,
      String balanceSide) {
    public BalanceBucketPayload {
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      Objects.requireNonNull(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }
}

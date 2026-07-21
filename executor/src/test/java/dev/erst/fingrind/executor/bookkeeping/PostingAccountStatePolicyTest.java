package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Protects the retirement boundary between ordinary authored postings and historical reversals. */
class PostingAccountStatePolicyTest {
  private static final AccountCode RETIRED_ACCOUNT_CODE = new AccountCode("1000");
  private static final AccountCode COUNTER_ACCOUNT_CODE = new AccountCode("3000");

  @Test
  void historicalReversal_remainsAdmissibleForRetiredAccounts() {
    RegisteredAccount retiredAccount =
        registeredAccount(
            RETIRED_ACCOUNT_CODE,
            new AccountName("Retired Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            false,
            Instant.parse("2026-07-14T10:15:30Z"));
    PostingAccountStatePolicy policy = new PostingAccountStatePolicy();

    assertEquals(
        Optional.empty(),
        policy.rejectionFor(
            posting(
                PostingLineageModel.reversal(
                    new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new ReversalReason("historical correction"))),
            accountStore(retiredAccount, activeRevenueAccount())));
  }

  @Test
  void ordinaryPosting_isRejectedForRetiredAccounts() {
    RegisteredAccount retiredAccount =
        registeredAccount(
            RETIRED_ACCOUNT_CODE,
            new AccountName("Retired Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            false,
            Instant.parse("2026-07-14T10:15:30Z"));
    PostingAccountStatePolicy policy = new PostingAccountStatePolicy();

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(
                List.of(new BookkeepingPostingRejection.InactiveAccount(RETIRED_ACCOUNT_CODE)))),
        policy.rejectionFor(
            posting(PostingLineageModel.direct()),
            accountStore(retiredAccount, activeRevenueAccount())));
  }

  private static RegisteredAccount activeRevenueAccount() {
    return registeredAccount(
        COUNTER_ACCOUNT_CODE,
        new AccountName("Revenue"),
        AccountType.REVENUE,
        NormalBalance.CREDIT,
        true,
        Instant.parse("2026-07-14T10:15:30Z"));
  }

  private static PostingValidationStore accountStore(
      RegisteredAccount retiredAccount, RegisteredAccount activeRevenueAccount) {
    return new EmptyValidationStore() {
      @Override
      public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
        return Map.of(
            retiredAccount.accountCode(), retiredAccount,
            activeRevenueAccount.accountCode(), activeRevenueAccount);
      }
    };
  }

  private static AcceptedPosting posting(PostingLineageModel postingLineage) {
    boolean historicalReversal = postingLineage.isReversal();
    return new AcceptedPosting(
        new JournalEntry(
            LocalDate.parse("2026-07-14"),
            List.of(
                new JournalLine(
                    RETIRED_ACCOUNT_CODE,
                    historicalReversal ? JournalLine.EntrySide.CREDIT : JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    COUNTER_ACCOUNT_CODE,
                    historicalReversal ? JournalLine.EntrySide.DEBIT : JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        postingLineage,
        PostingKind.STANDARD,
        historicalReversal ? PostingOriginKind.REVERSAL : PostingOriginKind.DIRECT_JOURNAL,
        accountingEvidence("account-state-" + historicalReversal),
        new RequestProvenance(
            new CommandId("5231a5c1-a00e-31c7-8ea5-6018a30ff18e"),
            new IdempotencyKey("account-state-" + historicalReversal),
            new CausationId("account-state-cause"),
            Optional.empty()),
        SourceChannel.CLI,
        null,
        null,
        List.of(),
        Map.of());
  }
}

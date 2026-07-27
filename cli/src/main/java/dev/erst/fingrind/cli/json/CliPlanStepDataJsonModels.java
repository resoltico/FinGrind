package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels.BalanceBucketPayload;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels.DeclaredAccountPayload;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels.PostingPayload;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels.PostingSummaryPayload;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Typed per-step fact JSON records emitted inside a ledger-plan execution journal. */
public interface CliPlanStepDataJsonModels {

  /** Tagged union for typed execute-plan journal step payloads. */
  sealed interface LedgerStepDataPayload
      permits LedgerAdministrativeStepDataPayload,
          LedgerBookkeepingStepDataPayload,
          LedgerControlStepDataPayload {}

  /** Administrative setup facts in one execute-plan journal step. */
  sealed interface LedgerAdministrativeStepDataPayload extends LedgerStepDataPayload
      permits AccountDeclarationStepDataPayload, TaxRegistrationDeclarationStepDataPayload {}

  /** Bookkeeping and read facts in one execute-plan journal step. */
  sealed interface LedgerBookkeepingStepDataPayload extends LedgerStepDataPayload
      permits PreflightEntryStepDataPayload,
          CommittedEntryStepDataPayload,
          BookInspectionStepDataPayload,
          AccountPageStepDataPayload,
          PostingStepDataPayload,
          PostingPageStepDataPayload,
          AccountBalanceStepDataPayload {}

  /** Assertion and transaction-boundary facts in one execute-plan journal step. */
  sealed interface LedgerControlStepDataPayload extends LedgerStepDataPayload
      permits AccountCodeAssertionStepDataPayload,
          PostingIdAssertionStepDataPayload,
          PlanBoundaryStepDataPayload {}

  record AccountDeclarationStepDataPayload(String outcome, DeclaredAccountPayload account)
      implements LedgerAdministrativeStepDataPayload {
    public AccountDeclarationStepDataPayload {
      outcome = requireText(outcome, "outcome");
      account = requireValue(account, "account");
    }
  }

  record TaxRegistrationDeclarationStepDataPayload(
      String outcome, CliTaxJsonModels.DeclaredTaxRegistrationPayload taxRegistration)
      implements LedgerAdministrativeStepDataPayload {
    public TaxRegistrationDeclarationStepDataPayload {
      outcome = requireText(outcome, "outcome");
      taxRegistration = requireValue(taxRegistration, "taxRegistration");
    }
  }

  record PreflightEntryStepDataPayload(String idempotencyKey, String effectiveDate)
      implements LedgerBookkeepingStepDataPayload {
    public PreflightEntryStepDataPayload {
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
    }
  }

  record CommittedEntryStepDataPayload(
      String postingId, String idempotencyKey, String effectiveDate, String recordedAt)
      implements LedgerBookkeepingStepDataPayload {
    public CommittedEntryStepDataPayload {
      postingId = requireText(postingId, "postingId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
    }
  }

  record BookInspectionStepDataPayload(
      String state, boolean initialized, boolean compatibleWithCurrentBinary)
      implements LedgerBookkeepingStepDataPayload {
    public BookInspectionStepDataPayload {
      state = requireText(state, "state");
    }
  }

  record AccountPageStepDataPayload(
      int count,
      int pageLimit,
      @Nullable String nextCursor,
      boolean hasMore,
      List<DeclaredAccountPayload> accounts)
      implements LedgerBookkeepingStepDataPayload {
    public AccountPageStepDataPayload {
      if (count < 0) {
        throw new IllegalArgumentException("count must be non-negative.");
      }
      if (pageLimit <= 0) {
        throw new IllegalArgumentException("pageLimit must be positive.");
      }
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      accounts = copyList(accounts, "accounts");
      if (count > pageLimit) {
        throw new IllegalArgumentException("count must not exceed pageLimit.");
      }
      if (count != accounts.size()) {
        throw new IllegalArgumentException("count must match accounts.size().");
      }
      if (hasMore != (nextCursor != null)) {
        throw new IllegalArgumentException("hasMore must match nextCursor presence.");
      }
    }
  }

  record PostingStepDataPayload(PostingPayload posting)
      implements LedgerBookkeepingStepDataPayload {
    public PostingStepDataPayload {
      posting = requireValue(posting, "posting");
    }
  }

  record PostingPageStepDataPayload(
      int count,
      int pageLimit,
      @Nullable String nextCursor,
      boolean hasMore,
      List<PostingSummaryPayload> postings)
      implements LedgerBookkeepingStepDataPayload {
    public PostingPageStepDataPayload {
      if (count < 0) {
        throw new IllegalArgumentException("count must be non-negative.");
      }
      if (pageLimit <= 0) {
        throw new IllegalArgumentException("pageLimit must be positive.");
      }
      nextCursor = requireOptionalText(nextCursor, "nextCursor");
      postings = copyList(postings, "postings");
      if (count > pageLimit) {
        throw new IllegalArgumentException("count must not exceed pageLimit.");
      }
      if (count != postings.size()) {
        throw new IllegalArgumentException("count must match postings.size().");
      }
      if (hasMore != (nextCursor != null)) {
        throw new IllegalArgumentException("hasMore must match nextCursor presence.");
      }
    }
  }

  record AccountBalanceStepDataPayload(
      DeclaredAccountPayload account,
      @Nullable String effectiveDateFrom,
      @Nullable String effectiveDateTo,
      int bucketCount,
      List<BalanceBucketPayload> balances)
      implements LedgerBookkeepingStepDataPayload {
    public AccountBalanceStepDataPayload {
      account = requireValue(account, "account");
      effectiveDateFrom = requireOptionalText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireOptionalText(effectiveDateTo, "effectiveDateTo");
      if (bucketCount < 0) {
        throw new IllegalArgumentException("bucketCount must be non-negative.");
      }
      balances = copyList(balances, "balances");
      if (bucketCount != balances.size()) {
        throw new IllegalArgumentException("bucketCount must match balances.size().");
      }
    }
  }

  record AccountCodeAssertionStepDataPayload(String accountCode)
      implements LedgerControlStepDataPayload {
    public AccountCodeAssertionStepDataPayload {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PostingIdAssertionStepDataPayload(String postingId)
      implements LedgerControlStepDataPayload {
    public PostingIdAssertionStepDataPayload {
      postingId = requireText(postingId, "postingId");
    }
  }

  record PlanBoundaryStepDataPayload(String checkpoint) implements LedgerControlStepDataPayload {
    public PlanBoundaryStepDataPayload {
      checkpoint = requireText(checkpoint, "checkpoint");
    }
  }
}

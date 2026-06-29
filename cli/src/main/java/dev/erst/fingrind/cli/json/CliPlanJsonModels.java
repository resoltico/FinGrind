package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Ledger-plan JSON records emitted by the CLI transport layer. */
public interface CliPlanJsonModels extends CliBookQueryJsonModels, CliPlanLedgerFactJsonModels {

  record LedgerPlanPayload(
      String planId,
      LedgerPlanStatus status,
      PlanResultDetail resultDetail,
      LedgerPlanSummaryPayload summary,
      @Nullable LedgerExecutionJournalPayload journal)
      implements CliSuccessPayload {
    public LedgerPlanPayload {
      planId = requireText(planId, "planId");
      status = requireValue(status, "status");
      resultDetail = requireValue(resultDetail, "resultDetail");
      summary = requireValue(summary, "summary");
      if (resultDetail == PlanResultDetail.FULL) {
        if (journal == null) {
          throw new IllegalArgumentException("journal must be present when resultDetail is full.");
        }
      } else if (journal != null) {
        throw new IllegalArgumentException("journal must be absent unless resultDetail is full.");
      }
    }
  }

  record LedgerPlanSummaryPayload(
      String startedAt,
      String finishedAt,
      int stepCount,
      int succeededStepCount,
      int failedStepCount,
      @Nullable String failedStepId) {
    public LedgerPlanSummaryPayload {
      startedAt = requireText(startedAt, "startedAt");
      finishedAt = requireText(finishedAt, "finishedAt");
      if (stepCount <= 0) {
        throw new IllegalArgumentException("stepCount must be positive.");
      }
      if (succeededStepCount < 0) {
        throw new IllegalArgumentException("succeededStepCount must be non-negative.");
      }
      if (failedStepCount < 0) {
        throw new IllegalArgumentException("failedStepCount must be non-negative.");
      }
      if (failedStepCount > 1) {
        throw new IllegalArgumentException("failedStepCount must be zero or one.");
      }
      if (succeededStepCount > stepCount) {
        throw new IllegalArgumentException("succeededStepCount must not exceed stepCount.");
      }
      if (succeededStepCount + failedStepCount != stepCount) {
        throw new IllegalArgumentException(
            "succeededStepCount and failedStepCount must add up to stepCount.");
      }
      failedStepId = requireOptionalText(failedStepId, "failedStepId");
      if (failedStepCount == 0 && failedStepId != null) {
        throw new IllegalArgumentException(
            "failedStepId must be absent when failedStepCount is zero.");
      }
      if (failedStepCount == 1 && failedStepId == null) {
        throw new IllegalArgumentException("failedStepId is required when failedStepCount is one.");
      }
    }
  }

  record LedgerExecutionJournalPayload(
      String startedAt, String finishedAt, List<LedgerJournalEntryPayload> steps) {
    public LedgerExecutionJournalPayload {
      startedAt = requireText(startedAt, "startedAt");
      finishedAt = requireText(finishedAt, "finishedAt");
      steps = copyList(steps, "steps");
      if (steps.isEmpty()) {
        throw new IllegalArgumentException("steps must not be empty.");
      }
    }
  }

  record LedgerJournalEntryPayload(
      String stepId,
      LedgerJournalKind kind,
      @Nullable LedgerAssertionKind detailKind,
      @Nullable LedgerBoundaryCheckpoint boundaryCheckpoint,
      LedgerStepStatus status,
      String startedAt,
      String finishedAt,
      @Nullable LedgerStepDataPayload data,
      @Nullable LedgerStepFailurePayload failure) {
    public LedgerJournalEntryPayload {
      stepId = requireText(stepId, "stepId");
      kind = requireValue(kind, "kind");
      status = requireValue(status, "status");
      startedAt = requireText(startedAt, "startedAt");
      finishedAt = requireText(finishedAt, "finishedAt");
      if (kind == LedgerJournalKind.ASSERT) {
        Objects.requireNonNull(detailKind, "detailKind");
      } else if (detailKind != null) {
        throw new IllegalArgumentException("detailKind must be absent unless kind is ASSERT.");
      }
      if (kind == LedgerJournalKind.PLAN_BOUNDARY) {
        Objects.requireNonNull(boundaryCheckpoint, "boundaryCheckpoint");
      } else if (boundaryCheckpoint != null) {
        throw new IllegalArgumentException(
            "boundaryCheckpoint must be absent unless kind is PLAN_BOUNDARY.");
      }
      if (status == LedgerStepStatus.SUCCEEDED) {
        if (failure != null) {
          throw new IllegalArgumentException(
              "failure must be absent when step status is SUCCEEDED.");
        }
      } else if (failure == null) {
        throw new IllegalArgumentException(
            "failure is required when step status is REJECTED or ASSERTION_FAILED.");
      }
      if (status == LedgerStepStatus.ASSERTION_FAILED && kind != LedgerJournalKind.ASSERT) {
        throw new IllegalArgumentException(
            "ASSERTION_FAILED steps must carry the ASSERT journal kind.");
      }
    }
  }

  record LedgerStepFailurePayload(String code, String message, List<LedgerFactPayload> details) {
    public LedgerStepFailurePayload {
      code = requireText(code, "code");
      message = requireText(message, "message");
      details = copyList(details, "details");
    }
  }

  /** Tagged union for typed execute-plan journal step payloads. */
  sealed interface LedgerStepDataPayload
      permits EnsureBookStepDataPayload,
          AccountDeclarationStepDataPayload,
          PreflightEntryStepDataPayload,
          CommittedEntryStepDataPayload,
          BookInspectionStepDataPayload,
          AccountPageStepDataPayload,
          PostingStepDataPayload,
          PostingPageStepDataPayload,
          AccountBalanceStepDataPayload,
          AccountCodeAssertionStepDataPayload,
          PostingIdAssertionStepDataPayload,
          PlanBoundaryStepDataPayload {}

  record EnsureBookStepDataPayload(
      String initializedAt, String entityName, String functionalCurrency, String fiscalYearStart)
      implements LedgerStepDataPayload {
    public EnsureBookStepDataPayload {
      initializedAt = requireText(initializedAt, "initializedAt");
      entityName = requireText(entityName, "entityName");
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
    }
  }

  record AccountDeclarationStepDataPayload(String outcome, DeclaredAccountPayload account)
      implements LedgerStepDataPayload {
    public AccountDeclarationStepDataPayload {
      outcome = requireText(outcome, "outcome");
      account = requireValue(account, "account");
    }
  }

  record PreflightEntryStepDataPayload(String idempotencyKey, String effectiveDate)
      implements LedgerStepDataPayload {
    public PreflightEntryStepDataPayload {
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
    }
  }

  record CommittedEntryStepDataPayload(
      String postingId, String idempotencyKey, String effectiveDate, String recordedAt)
      implements LedgerStepDataPayload {
    public CommittedEntryStepDataPayload {
      postingId = requireText(postingId, "postingId");
      idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
      effectiveDate = requireText(effectiveDate, "effectiveDate");
      recordedAt = requireText(recordedAt, "recordedAt");
    }
  }

  record BookInspectionStepDataPayload(
      String state, boolean initialized, boolean compatibleWithCurrentBinary)
      implements LedgerStepDataPayload {
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
      implements LedgerStepDataPayload {
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

  record PostingStepDataPayload(PostingPayload posting) implements LedgerStepDataPayload {
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
      implements LedgerStepDataPayload {
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
      implements LedgerStepDataPayload {
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

  record AccountCodeAssertionStepDataPayload(String accountCode) implements LedgerStepDataPayload {
    public AccountCodeAssertionStepDataPayload {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PostingIdAssertionStepDataPayload(String postingId) implements LedgerStepDataPayload {
    public PostingIdAssertionStepDataPayload {
      postingId = requireText(postingId, "postingId");
    }
  }

  record PlanBoundaryStepDataPayload(String checkpoint) implements LedgerStepDataPayload {
    public PlanBoundaryStepDataPayload {
      checkpoint = requireText(checkpoint, "checkpoint");
    }
  }
}

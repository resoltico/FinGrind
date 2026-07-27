package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.cli.json.CliPlanStepDataJsonModels.LedgerStepDataPayload;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Ledger-plan execution envelope and journal JSON records emitted by the CLI transport layer. */
public interface CliPlanResultJsonModels {

  record LedgerPlanPayload(
      String planId,
      LedgerPlanStatus status,
      PlanResultDetail resultDetail,
      LedgerPlanSummaryPayload summary,
      @JsonInclude(JsonInclude.Include.ALWAYS)
          @Nullable LedgerPlanAttestationDisposition attestationDisposition,
      @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable AttestationCommitPayload attestationCommit,
      @Nullable LedgerExecutionJournalPayload journal)
      implements CliSuccessPayload {
    public LedgerPlanPayload {
      planId = requireText(planId, "planId");
      status = requireValue(status, "status");
      resultDetail = requireValue(resultDetail, "resultDetail");
      summary = requireValue(summary, "summary");
      validateAttestation(status, attestationDisposition, attestationCommit);
      if (resultDetail == PlanResultDetail.FULL) {
        if (journal == null) {
          throw new IllegalArgumentException("journal must be present when resultDetail is full.");
        }
      } else if (journal != null) {
        throw new IllegalArgumentException("journal must be absent unless resultDetail is full.");
      }
    }
  }

  private static void validateAttestation(
      LedgerPlanStatus status,
      @Nullable LedgerPlanAttestationDisposition attestationDisposition,
      @Nullable AttestationCommitPayload attestationCommit) {
    if (status != LedgerPlanStatus.SUCCEEDED) {
      rejectUnexpectedAttestation(attestationDisposition, attestationCommit);
      return;
    }
    if (attestationDisposition == null) {
      throw new IllegalArgumentException(
          "attestationDisposition is required when status is SUCCEEDED.");
    }
    validateAttestationCommit(attestationDisposition, attestationCommit);
  }

  private static void rejectUnexpectedAttestation(
      @Nullable LedgerPlanAttestationDisposition attestationDisposition,
      @Nullable AttestationCommitPayload attestationCommit) {
    if (attestationDisposition != null || attestationCommit != null) {
      throw new IllegalArgumentException(
          "attestation disposition and commit must be absent unless status is SUCCEEDED.");
    }
  }

  private static void validateAttestationCommit(
      LedgerPlanAttestationDisposition attestationDisposition,
      @Nullable AttestationCommitPayload attestationCommit) {
    if (attestationDisposition.requiresAttestationCommit() && attestationCommit == null) {
      throw new IllegalArgumentException(
          "attestationCommit is required for the selected attestation disposition.");
    }
    if (!attestationDisposition.requiresAttestationCommit() && attestationCommit != null) {
      throw new IllegalArgumentException(
          "attestationCommit must be null for the selected attestation disposition.");
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
      failedStepId = CliJsonModelValidation.requireOptionalText(failedStepId, "failedStepId");
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
      steps = CliJsonModelValidation.copyList(steps, "steps");
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
      if (kind == LedgerStepKind.ASSERT) {
        Objects.requireNonNull(detailKind, "detailKind");
      } else if (detailKind != null) {
        throw new IllegalArgumentException("detailKind must be absent unless kind is ASSERT.");
      }
      if (kind == LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY) {
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
      if (status == LedgerStepStatus.ASSERTION_FAILED && kind != LedgerStepKind.ASSERT) {
        throw new IllegalArgumentException(
            "ASSERTION_FAILED steps must carry the ASSERT journal kind.");
      }
    }
  }

  record LedgerStepFailurePayload(
      String code, String message, List<CliPlanLedgerFactJsonModels.LedgerFactPayload> details) {
    public LedgerStepFailurePayload {
      code = requireText(code, "code");
      message = requireText(message, "message");
      details = CliJsonModelValidation.copyList(details, "details");
    }
  }
}

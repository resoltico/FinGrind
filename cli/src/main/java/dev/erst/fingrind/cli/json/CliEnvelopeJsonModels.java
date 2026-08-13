package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Envelope-level JSON records emitted by the CLI transport layer. */
public interface CliEnvelopeJsonModels {
  String PLAN_OPERATION = ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN);

  /** Shared marker for typed non-success detail payloads that can appear at the top level. */
  sealed interface EnvelopeDetails
      permits CliErrorJsonModels.ErrorDetails, CliRejectionJsonModels.RejectionDetails {}

  /**
   * Publishes one successful artifact with either legacy stage or transaction completion evidence.
   */
  record SuccessArtifact(
      String format,
      String path,
      @Nullable String retainedStage,
      @Nullable PublicationTransaction publicationTransaction) {
    public SuccessArtifact {
      format = requireText(format, "format");
      path = requireText(path, "path");
      retainedStage = CliJsonModelValidation.requireOptionalText(retainedStage, "retainedStage");
      if ((retainedStage == null) == (publicationTransaction == null)) {
        throw new IllegalArgumentException(
            "A success artifact requires exactly one publication-evidence form.");
      }
      if (path.equals(retainedStage)) {
        throw new IllegalArgumentException(
            "path and retainedStage must identify distinct artifacts.");
      }
    }

    /**
     * Retains the protocol-58 constructor while production publishers migrate transaction by
     * transaction.
     */
    public SuccessArtifact(String format, String path, String retainedStage) {
      this(format, path, retainedStage, null);
    }

    /** Creates one success artifact whose safe evidence is an ID-only publication transaction. */
    public SuccessArtifact(
        String format, String path, PublicationTransaction publicationTransaction) {
      this(format, path, null, publicationTransaction);
    }
  }

  /**
   * The ID-only durable result that authorizes inspection or recovery of one failed publication.
   */
  record PublicationTransaction(
      String id, String state, String commitOutcome, String cleanupOutcome) {
    public PublicationTransaction {
      id = requireText(id, "id");
      state = requireText(state, "state");
      commitOutcome = requireText(commitOutcome, "commitOutcome");
      cleanupOutcome = requireText(cleanupOutcome, "cleanupOutcome");
    }
  }

  record Envelope<T extends ProtocolSuccessPayload>(
      ProtocolEnvelopeStatus status,
      @Nullable T payload,
      @Nullable String code,
      @Nullable String message,
      @Nullable String hint,
      @Nullable String argument,
      @Nullable String idempotencyKey,
      @Nullable EnvelopeDetails details,
      @Nullable List<SuccessArtifact> artifacts,
      @Nullable String path,
      @Nullable List<String> relatedPaths,
      @Nullable String retainedStage) {
    public Envelope {
      status = requireValue(status, "status");
      code = CliJsonModelValidation.requireOptionalText(code, "code");
      message = CliJsonModelValidation.requireOptionalText(message, "message");
      hint = CliJsonModelValidation.requireOptionalText(hint, "hint");
      argument = CliJsonModelValidation.requireOptionalText(argument, "argument");
      idempotencyKey = CliJsonModelValidation.requireOptionalText(idempotencyKey, "idempotencyKey");
      artifacts = artifacts == null ? null : java.util.List.copyOf(artifacts);
      path = CliJsonModelValidation.requireOptionalText(path, "path");
      relatedPaths = relatedPaths == null ? null : java.util.List.copyOf(relatedPaths);
      retainedStage = CliJsonModelValidation.requireOptionalText(retainedStage, "retainedStage");
      if (artifacts != null) {
        if (artifacts.isEmpty()) {
          throw new IllegalArgumentException("artifacts must not be empty when present.");
        }
        var artifactLocations = new HashSet<List<String>>();
        for (SuccessArtifact artifact : artifacts) {
          if (!artifactLocations.add(List.of(artifact.format(), artifact.path()))) {
            throw new IllegalArgumentException("artifacts must not contain duplicate entries.");
          }
        }
      }
      if (status == ProtocolEnvelopeStatus.OK) {
        payload = requireValue(java.util.Objects.requireNonNull(payload, "payload"), "payload");
        requireAbsent(code, "code");
        requireAbsent(message, "message");
        requireAbsent(hint, "hint");
        requireAbsent(argument, "argument");
        requireAbsent(idempotencyKey, "idempotencyKey");
        requireAbsent(details, "details");
        requireAbsent(path, "path");
        requireAbsent(relatedPaths, "relatedPaths");
        requireAbsent(retainedStage, "retainedStage");
      } else if (status == ProtocolEnvelopeStatus.REJECTED) {
        validateNonSuccessPayload(status, payload);
        code = requireText(java.util.Objects.requireNonNull(code, "code"), "code");
        message = requireText(java.util.Objects.requireNonNull(message, "message"), "message");
        requireAbsent(argument, "argument");
        requireAbsent(artifacts, "artifacts");
        validateFailurePaths(path, relatedPaths);
        if (details != null && !(details instanceof CliRejectionJsonModels.RejectionDetails)) {
          throw new IllegalArgumentException("Rejected envelopes only admit rejection details.");
        }
      } else {
        validateNonSuccessPayload(status, payload);
        code = requireText(java.util.Objects.requireNonNull(code, "code"), "code");
        message = requireText(java.util.Objects.requireNonNull(message, "message"), "message");
        requireAbsent(idempotencyKey, "idempotencyKey");
        requireAbsent(artifacts, "artifacts");
        validateFailurePaths(path, relatedPaths);
        if (details != null && !(details instanceof CliErrorJsonModels.ErrorDetails)) {
          throw new IllegalArgumentException("Error envelopes only admit error details.");
        }
      }
    }

    /** Returns the single taxonomy category for every non-success envelope. */
    @JsonProperty("category")
    public @Nullable FailureCategory category() {
      return status == ProtocolEnvelopeStatus.OK
          ? null
          : ContractResponseCatalog.failureCategoryFor(
              java.util.Objects.requireNonNull(code, "code"));
    }
  }

  private static void validateFailurePaths(
      @Nullable String path, @Nullable List<String> relatedPaths) {
    if (path == null) {
      requireAbsent(relatedPaths, "relatedPaths");
      return;
    }
    if (relatedPaths == null) {
      throw new IllegalArgumentException("relatedPaths must be present when path is present.");
    }
    for (String relatedPath : relatedPaths) {
      requireText(relatedPath, "relatedPaths element");
    }
  }

  private static void validateNonSuccessPayload(
      ProtocolEnvelopeStatus status, @Nullable ProtocolSuccessPayload payload) {
    if (payload == null) {
      return;
    }
    if (!(payload instanceof CliPlanResultJsonModels.LedgerPlanPayload planPayload)) {
      throw new IllegalArgumentException(
          "payload must be absent unless this non-success envelope carries a "
              + PLAN_OPERATION
              + " result.");
    }
    if (status == ProtocolEnvelopeStatus.REJECTED
        && planPayload.status() != LedgerPlanStatus.REJECTED) {
      throw new IllegalArgumentException(
          "Rejected " + PLAN_OPERATION + " envelopes must carry a rejected plan payload.");
    }
    if (status == ProtocolEnvelopeStatus.ERROR
        && planPayload.status() != LedgerPlanStatus.ASSERTION_FAILED) {
      throw new IllegalArgumentException(
          "Error " + PLAN_OPERATION + " envelopes must carry an assertion-failed plan payload.");
    }
  }

  private static void requireAbsent(@Nullable Object value, String fieldName) {
    if (value != null) {
      throw new IllegalArgumentException(fieldName + " must be absent for this envelope status.");
    }
  }
}

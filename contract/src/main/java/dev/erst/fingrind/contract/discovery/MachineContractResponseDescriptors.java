package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.ResponseModelDescriptor;
import java.util.List;

/** Builds rejection and error response descriptors for the machine contract. */
final class MachineContractResponseDescriptors {
  private MachineContractResponseDescriptors() {}

  static ResponseModelDescriptor responseModel() {
    return new ResponseModelDescriptor(
        ProtocolCatalog.envelopes().successStatus(),
        List.of(
            new FieldDescriptor("status", "Literal success status."),
            new FieldDescriptor("payload", lifecycleSuccessPayloadDescription()),
            new FieldDescriptor(
                "artifacts",
                "Optional artifact metadata array that owns every successful artifact path published beside the primary payload. Every entry has format, canonical path, and mandatory retainedStage immutable evidence.")),
        ProtocolCatalog.envelopes().rejectionStatus(),
        ProtocolCatalog.envelopes().errorStatus(),
        ContractResponseCatalog.rejectionDescriptors(),
        AttestationVerificationFailure.admissionDiagnosticContexts(),
        AttestationVerificationFailure.verificationDiagnosticSurfaces(),
        ContractResponseCatalog.errorDescriptors(),
        List.of(
            new FieldDescriptor("status", "Literal rejection status."),
            liftedPlanOutcomePayloadField(),
            new FieldDescriptor("code", "Stable machine rejection code."),
            new FieldDescriptor(
                "category", "Explicit transport category for the published rejection code."),
            new FieldDescriptor("message", "Plain-language explanation of the rejection."),
            new FieldDescriptor(
                "hint", "Optional operator hint for repairing or rerunning the request."),
            new FieldDescriptor(
                "path", "Optional canonical primary filesystem path for the rejection."),
            new FieldDescriptor(
                "relatedPaths",
                "Optional additional canonical filesystem paths that must be preserved with path."),
            new FieldDescriptor(
                "details",
                "Optional structured rejection-specific detail object. attestation-review-required"
                    + " carries bookId, verifiedAttestationHead, previousHead, and reviewFindings; "
                    + "a backup acknowledgement authorization rejection carries the published "
                    + "bookFile, backupFile, backupKeyFile, backupId, and "
                    + "pairPublicationCompletion facts. Maintenance artifactRole has the closed "
                    + "wire vocabulary "
                    + BookMaintenanceArtifactRole.wireValues()
                    + "; maintenance pathFailure has the closed wire vocabulary "
                    + PublicationPathFailure.wireValues()
                    + ".")),
        List.of(
            new FieldDescriptor("status", "Literal rejection status."),
            new FieldDescriptor("code", "Stable machine rejection code."),
            new FieldDescriptor(
                "category", "Explicit transport category for the published rejection code."),
            new FieldDescriptor("message", "Plain-language explanation of the rejection."),
            new FieldDescriptor(
                "hint", "Optional operator hint for repairing or rerunning the request."),
            new FieldDescriptor(
                "idempotencyKey", "The caller-supplied idempotency key from the rejected request."),
            new FieldDescriptor(
                "path", "Optional canonical primary filesystem path for the rejection."),
            new FieldDescriptor(
                "relatedPaths",
                "Optional additional canonical filesystem paths that must be preserved with path."),
            new FieldDescriptor(
                "details", "Optional structured rejection-specific detail object.")),
        List.of(
            new FieldDescriptor("status", "Literal runtime or invalid-request error status."),
            liftedPlanOutcomePayloadField(),
            new FieldDescriptor("code", "Stable machine error code."),
            new FieldDescriptor(
                "category", "Explicit transport category for the published error code."),
            new FieldDescriptor("message", "Plain-language explanation of the error."),
            new FieldDescriptor("hint", "Optional operator hint for repairing the invocation."),
            new FieldDescriptor("argument", "Optional argument name associated with the failure."),
            new FieldDescriptor(
                "path", "Optional canonical primary filesystem path for the error."),
            new FieldDescriptor(
                "relatedPaths",
                "Optional additional canonical filesystem paths that must be preserved with path."),
            new FieldDescriptor(
                "retainedStage",
                "Optional canonical immutable private stage retained with the primary error; present whenever one such stage exists."),
            new FieldDescriptor(
                "details",
                "Optional structured error-specific detail object. "
                    + "protected-book-pair-publication-uncertain carries operation and "
                    + "pairPublication; pairPublication has canonical bookTarget and "
                    + "generatedSecretTarget facts, each with path and state, and has "
                    + "an always-present nullable recoveryRecordState: durably-retained or "
                    + "durability-unconfirmed exactly when neither final member was attempted, "
                    + "otherwise null.")));
  }

  private static String lifecycleSuccessPayloadDescription() {
    return "Operation-specific success payload object. Successful "
        + ProtocolCatalog.operationName(OperationId.BACKUP_BOOK)
        + ", "
        + ProtocolCatalog.operationName(OperationId.RESTORE_BOOK)
        + ", and "
        + ProtocolCatalog.operationName(OperationId.REKEY_BOOK)
        + " payloads include pairPublicationCompletion with the closed wire vocabulary "
        + ProtectedBookPairPublicationCompletion.wireValues()
        + ": published means this invocation durably published the final pair; recovered means "
        + "this invocation completed an earlier retained recovery record; already-published means "
        + "an exact backup acknowledgement retry verified an existing complete pair without "
        + "publishing it again. pairPublicationRetention is required for published and recovered "
        + "and contains authoritative bookPublication and generatedSecretPublication facts, each "
        + "with canonical path and retainedStage; it is "
        + "explicitly null only for already-published acknowledgement where this invocation has no "
        + "FinGrind stage evidence.";
  }

  private static FieldDescriptor liftedPlanOutcomePayloadField() {
    return new FieldDescriptor(
        "payload",
        "Optional "
            + ProtocolCatalog.operationName(OperationId.EXECUTE_PLAN)
            + " outcome payload object published when a rejected or assertion-failed plan result still owns the primary operation body beside lifted top-level diagnostics.");
  }
}

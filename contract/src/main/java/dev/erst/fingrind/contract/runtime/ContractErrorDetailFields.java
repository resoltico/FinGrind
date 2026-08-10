package dev.erst.fingrind.contract.runtime;

import java.util.List;

/** Owns typed detail-field metadata for deterministic errors that carry structured facts. */
final class ContractErrorDetailFields {
  private ContractErrorDetailFields() {}

  static List<FieldDescriptor> forDescriptor(ContractErrors.Descriptor descriptor) {
    return switch (descriptor) {
      case INVALID_REQUEST -> invalidRequestDetailFields();
      case ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN ->
          artifactPublicationDurabilityUncertainDetailFields();
      case PUBLICATION_TRANSACTION_INCOMPLETE -> publicationTransactionIncompleteDetailFields();
      case PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN ->
          protectedBookPairPublicationUncertainDetailFields();
      case PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED ->
          protectedBookPairPublicationEvidenceBlockedDetailFields();
      case ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN ->
          artifactPublicationOutcomeUncertainDetailFields();
      case STALE_HEAD -> staleHeadDetailFields();
      case ATTESTATION_REVIEW_WINDOW_EXCEEDS_HEAD -> attestationReviewWindowDetailFields();
      case UNSUPPORTED_BOOK_FORMAT_VERSION -> unsupportedBookFormatVersionDetailFields();
      case OPEN_BOOK_PREPARATION_ARTIFACTS_RETAINED ->
          openBookPreparationArtifactsRetainedDetailFields();
      case OPEN_BOOK_PUBLICATION_PROGRESS -> openBookPublicationProgressDetailFields();
      case OPEN_BOOK_COMPLETION_UNCERTAIN -> openBookCompletionUncertainDetailFields();
      default -> List.of();
    };
  }

  private static List<FieldDescriptor> staleHeadDetailFields() {
    return List.of(
        new FieldDescriptor(
            "observedHead", "Lowercase hexadecimal operation head used for the refused signature."),
        new FieldDescriptor(
            "currentHead", "Lowercase hexadecimal operation head authenticated at admission."),
        new FieldDescriptor(
            "currentOrder",
            "Canonical decimal order of the authenticated current operation head."));
  }

  private static List<FieldDescriptor> artifactPublicationDurabilityUncertainDetailFields() {
    return List.of(
        new FieldDescriptor(
            "publishedArtifact",
            "Canonical final artifact path and its mandatory retained private stage after parent-directory durability could not be confirmed."));
  }

  private static List<FieldDescriptor> publicationTransactionIncompleteDetailFields() {
    return List.of(
        new FieldDescriptor(
            "candidateArtifact",
            "Canonical requested final artifact path whose publication transaction did not complete."),
        new FieldDescriptor(
            "publicationTransaction",
            "ID-only transaction result: id, state, commitOutcome, and cleanupOutcome. The identifier is the sole recovery handle."));
  }

  private static List<FieldDescriptor> protectedBookPairPublicationUncertainDetailFields() {
    return List.of(
        new FieldDescriptor(
            "operation",
            "Canonical maintenance operation whose exact retry may reconcile the protected-book pair."),
        new FieldDescriptor(
            "pairPublication",
            "Both canonical final pair members: bookTarget and generatedSecretTarget each carry path and the strongest established publication state; recoveryRecordState is always-present nullable, with durably-retained or durability-unconfirmed exactly when neither final member was attempted and null otherwise; pairPublicationRetention is always-present nullable and, when present, binds both final paths to their exact retained private stages."));
  }

  private static List<FieldDescriptor> protectedBookPairPublicationEvidenceBlockedDetailFields() {
    return List.of(
        new FieldDescriptor(
            "pairPublication",
            "Both canonical final pair members: bookTarget and generatedSecretTarget each carry path and the strongest established publication state. pairPublicationRetention is always-present nullable and, when present, binds both final paths to their exact retained private stages. At least one state is unestablished, and recoveryRecordState is null."));
  }

  private static List<FieldDescriptor> artifactPublicationOutcomeUncertainDetailFields() {
    return List.of(
        new FieldDescriptor(
            "candidateArtifact",
            "Canonical requested final artifact path whose existence was not established."),
        new FieldDescriptor(
            "retainedStage",
            "Optional canonical private stage retained before the indeterminate final-link attempt; absent only when no stage was created."));
  }

  private static List<FieldDescriptor> attestationReviewWindowDetailFields() {
    return List.of(
        new FieldDescriptor(
            "credentialKeyId",
            "Lowercase SHA-256 credential key identifier named by the invalid review declaration."),
        new FieldDescriptor(
            "firstAffectedOrder",
            "Canonical unsigned-64 first operation order of the declared inclusive review window."),
        new FieldDescriptor(
            "lastAffectedOrder",
            "Always-present nullable canonical unsigned-64 final order; null means through the verified head."),
        new FieldDescriptor(
            "verifiedHeadOrder",
            "Canonical unsigned-64 final operation order authenticated by the selected book."));
  }

  private static List<FieldDescriptor> unsupportedBookFormatVersionDetailFields() {
    return List.of(
        new FieldDescriptor(
            "detectedBookFormatVersion",
            "Non-negative SQLite user_version declared by the selected authenticated FinGrind book."),
        new FieldDescriptor(
            "supportedBookFormatVersion",
            "The sole positive FinGrind book format version supported by this binary."));
  }

  private static List<FieldDescriptor> openBookPreparationArtifactsRetainedDetailFields() {
    return List.of(
        new FieldDescriptor(
            "retainedArtifacts",
            "Non-empty ordered artifacts intentionally retained after book opening did not complete; each fact contains role, canonical path, and an optional retained private stage."));
  }

  private static List<FieldDescriptor> openBookPublicationProgressDetailFields() {
    return List.of(
        new FieldDescriptor(
            "publishedFounderKeyArtifacts",
            "Completed founder-key artifact paths with their ID-only publication transaction results."),
        new FieldDescriptor(
            "incompleteFounderKeyPublication",
            "Always-present nullable founder-key candidate and ID-only incomplete transaction result."));
  }

  private static List<FieldDescriptor> openBookCompletionUncertainDetailFields() {
    return List.of(
        new FieldDescriptor(
            "bookFile",
            "Canonical selected book-file path whose initialized state requires verification before reuse."),
        new FieldDescriptor(
            "initializedAt",
            "Initialization timestamp returned before SQLite could confirm durable completion after initialization COMMIT or session shutdown."),
        new FieldDescriptor(
            "bookIdentity", "Selected accounting identity returned by the opening operation."),
        new FieldDescriptor(
            "attestationTrustRoot",
            "Genesis attestation registry facts returned by the opening operation."),
        new FieldDescriptor(
            "attestationCommit", "Genesis operation reference returned by the opening operation."),
        new FieldDescriptor(
            "retainedFounderKeyArtifacts",
            "Founder-key artifacts published during the opening attempt, each with canonical path and mandatory retained private stage."),
        new FieldDescriptor(
            "retainedBookArtifacts",
            "Canonical book-file and SQLite sidecar paths retained after the incomplete opening attempt."));
  }

  private static List<FieldDescriptor> invalidRequestDetailFields() {
    return List.of(
        new FieldDescriptor(
            "parseMessage",
            "Parser-provided explanation for syntactically invalid JSON request input."),
        new FieldDescriptor(
            "line", "1-based JSON source line for syntactically invalid request input."),
        new FieldDescriptor(
            "column", "1-based JSON source column for syntactically invalid request input."),
        new FieldDescriptor(
            "violations",
            "Ordered list of deterministic request-validation violations when a malformed request produces multiple diagnoses."));
  }
}

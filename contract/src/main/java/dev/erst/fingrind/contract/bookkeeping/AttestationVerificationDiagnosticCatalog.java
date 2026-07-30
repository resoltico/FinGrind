package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionDiagnosticsDescriptor;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.DiagnosticDescriptor;
import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.VerificationDiagnosticsDescriptor;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Builds exact attestation diagnostics from the closed published failure vocabulary. */
final class AttestationVerificationDiagnosticCatalog {
  private static final String VERIFY_RECEIPT_RECOVERY =
      "Preserve the selected receipt for investigation and compare it with a verified protected"
          + " book. If the book verifies, export a new receipt; otherwise restore a verified"
          + " independently retained backup.";

  private static final List<AttestationVerificationFailure> AUTHORIZATION_FAILURES =
      Arrays.stream(AttestationAuthorizationFailure.values())
          .map(
              authorizationFailure ->
                  AttestationVerificationFailure.fromWireCode(authorizationFailure.code()))
          .toList();

  private static final List<AttestationVerificationFailure> ORDINARY_ADMISSION_FAILURES =
      requireAuthorizationVocabulary(
          failuresMatching(AttestationDiagnosticReachability::ordinaryLiveAdmission));
  private static final List<AttestationVerificationFailure> OPERATION_ADMISSION_FAILURES =
      failuresMatching(AttestationDiagnosticReachability::operationAdmission);

  private static final List<AttestationVerificationFailure> BOOK_CHAIN_FAILURES =
      failuresMatching(AttestationDiagnosticReachability::bookChainVerification);

  private static final List<AttestationVerificationFailure> RECEIPT_VERIFICATION_FAILURES =
      failuresMatching(AttestationDiagnosticReachability::receiptVerification);

  private static final Map<AttestationVerificationFailure, RegistryTargetDiagnostic>
      REGISTRY_TARGET_DIAGNOSTICS =
          Map.of(
              AttestationVerificationFailure.DUPLICATE_PRINCIPAL,
              new RegistryTargetDiagnostic(
                  "The requested credential enrollment repeats a principal already represented in the current attestation registry.",
                  "Use "
                      + OperationId.ROLLOVER_KEY.wireName()
                      + " for a replacement credential, or choose a principal ID not already enrolled."),
              AttestationVerificationFailure.DUPLICATE_KEY,
              new RegistryTargetDiagnostic(
                  "The requested credential is already represented in the current attestation registry.",
                  "Generate a different credential for this enrollment or use the existing credential's principal."),
              AttestationVerificationFailure.KEY_NOT_ENROLLED,
              new RegistryTargetDiagnostic(
                  "The requested rollover or revocation target is not enrolled at the current attestation head.",
                  "Run "
                      + OperationId.VERIFY_BOOK.wireName()
                      + " to inspect the current attestation registry, then select an enrolled credential."),
              AttestationVerificationFailure.KEY_REVOKED,
              new RegistryTargetDiagnostic(
                  "The requested rollover or revocation target is already revoked at the current attestation head.",
                  "Run "
                      + OperationId.VERIFY_BOOK.wireName()
                      + " to inspect the current registry; select an active enrolled credential instead."),
              AttestationVerificationFailure.KEY_SUPERSEDED,
              new RegistryTargetDiagnostic(
                  "The requested rollover or revocation target was already superseded by a replacement credential at the current attestation head.",
                  "Run "
                      + OperationId.VERIFY_BOOK.wireName()
                      + " to identify the active replacement credential; superseded credentials cannot be changed again."),
              AttestationVerificationFailure.KEY_PRINCIPAL_MISMATCH,
              new RegistryTargetDiagnostic(
                  "The requested credential belongs to a different principal in the current attestation registry.",
                  "Use the principal ID bound to that credential, or select the intended principal's credential."));

  private static final List<RejectionDescriptor> REJECTION_DESCRIPTORS =
      Arrays.stream(AttestationVerificationFailure.values())
          .map(
              failure ->
                  new RejectionDescriptor(
                      failure.wireCode(),
                      FailureCategory.STRUCTURAL_INVALID,
                      2,
                      failure.description()))
          .toList();

  private static final AdmissionDiagnosticsDescriptor ORDINARY_LIVE_ADMISSION_DIAGNOSTICS =
      admissionDiagnostics(AdmissionContext.ORDINARY_LIVE_ADMISSION);
  private static final AdmissionDiagnosticsDescriptor REGISTRY_MUTATION_DIAGNOSTICS =
      admissionDiagnostics(AdmissionContext.REGISTRY_MUTATION);
  private static final AdmissionDiagnosticsDescriptor BACKUP_ACKNOWLEDGEMENT_DIAGNOSTICS =
      admissionDiagnostics(AdmissionContext.BACKUP_ACKNOWLEDGEMENT);

  private static final List<AdmissionDiagnosticsDescriptor> ADMISSION_DIAGNOSTIC_CONTEXTS =
      List.of(
          ORDINARY_LIVE_ADMISSION_DIAGNOSTICS,
          REGISTRY_MUTATION_DIAGNOSTICS,
          BACKUP_ACKNOWLEDGEMENT_DIAGNOSTICS);

  private static final List<VerificationDiagnosticsDescriptor> VERIFICATION_DIAGNOSTIC_SURFACES =
      List.of(
          verificationDiagnostics(
              OperationId.VERIFY_BOOK,
              "The selected book's attestation chain failed verification.",
              BOOK_CHAIN_FAILURES,
              false),
          verificationDiagnostics(
              OperationId.ATTESTATION_REVIEW,
              "The selected book's attestation chain cannot be reviewed.",
              BOOK_CHAIN_FAILURES,
              false),
          verificationDiagnostics(
              OperationId.EXPORT_ATTESTATION_RECEIPT,
              "The selected book's attestation chain cannot support receipt export.",
              BOOK_CHAIN_FAILURES,
              false),
          verificationDiagnostics(
              OperationId.VERIFY_RECEIPT,
              "The selected receipt or its referenced attestation chain failed verification.",
              RECEIPT_VERIFICATION_FAILURES,
              true));

  private AttestationVerificationDiagnosticCatalog() {}

  static List<RejectionDescriptor> rejectionDescriptors() {
    return REJECTION_DESCRIPTORS;
  }

  static List<AdmissionDiagnosticsDescriptor> admissionDiagnosticContexts() {
    return ADMISSION_DIAGNOSTIC_CONTEXTS;
  }

  static DiagnosticDescriptor admissionDiagnostic(
      AttestationVerificationFailure failure, AdmissionContext context) {
    AttestationVerificationFailure checkedFailure = Objects.requireNonNull(failure, "failure");
    AdmissionContext checkedContext = Objects.requireNonNull(context, "context");
    return diagnosticContext(checkedContext).diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals(checkedFailure.wireCode()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Attestation verification failure is not reachable during "
                        + checkedContext.wireValue()
                        + ": "
                        + checkedFailure.wireCode()));
  }

  static List<VerificationDiagnosticsDescriptor> verificationDiagnosticSurfaces() {
    return VERIFICATION_DIAGNOSTIC_SURFACES;
  }

  static DiagnosticDescriptor verificationDiagnostic(
      AttestationVerificationFailure failure, OperationId surface) {
    AttestationVerificationFailure checkedFailure = Objects.requireNonNull(failure, "failure");
    OperationId checkedSurface = Objects.requireNonNull(surface, "surface");
    return verificationSurface(checkedSurface).diagnostics().stream()
        .filter(diagnostic -> diagnostic.code().equals(checkedFailure.wireCode()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Attestation verification failure is not emitted by "
                        + checkedSurface.wireName()
                        + ": "
                        + checkedFailure.wireCode()));
  }

  private static List<AttestationVerificationFailure> failuresMatching(
      Predicate<AttestationDiagnosticReachability> predicate) {
    return Arrays.stream(AttestationVerificationFailure.values())
        .filter(failure -> predicate.test(failure.diagnosticReachability()))
        .toList();
  }

  /**
   * Ensures ordinary live admission stays exactly aligned with the core authorization vocabulary.
   */
  static List<AttestationVerificationFailure> requireAuthorizationVocabulary(
      List<AttestationVerificationFailure> failures) {
    if (!AUTHORIZATION_FAILURES.equals(failures)) {
      throw new IllegalStateException(
          "Ordinary attestation admission must match the core authorization failure vocabulary.");
    }
    return failures;
  }

  private static AdmissionDiagnosticsDescriptor admissionDiagnostics(AdmissionContext context) {
    return new AdmissionDiagnosticsDescriptor(
        context,
        admissionFailures(context).stream()
            .map(failure -> buildAdmissionDiagnostic(failure, context))
            .toList());
  }

  private static List<AttestationVerificationFailure> admissionFailures(AdmissionContext context) {
    return switch (context) {
      // This public envelope also carries backup-artifact and receipt-export authorization results.
      case ORDINARY_LIVE_ADMISSION -> ORDINARY_ADMISSION_FAILURES;
      case REGISTRY_MUTATION, BACKUP_ACKNOWLEDGEMENT -> OPERATION_ADMISSION_FAILURES;
    };
  }

  private static DiagnosticDescriptor buildAdmissionDiagnostic(
      AttestationVerificationFailure failure, AdmissionContext context) {
    return switch (context) {
      case ORDINARY_LIVE_ADMISSION -> ordinaryAdmissionDiagnostic(failure);
      case REGISTRY_MUTATION -> registryMutationDiagnostic(failure);
      case BACKUP_ACKNOWLEDGEMENT -> backupAcknowledgementDiagnostic(failure);
    };
  }

  private static DiagnosticDescriptor ordinaryAdmissionDiagnostic(
      AttestationVerificationFailure failure) {
    return new DiagnosticDescriptor(
        failure.wireCode(), failure.admissionDescription(), failure.admissionRemediation());
  }

  private static DiagnosticDescriptor registryMutationDiagnostic(
      AttestationVerificationFailure failure) {
    RegistryTargetDiagnostic targetDiagnostic = REGISTRY_TARGET_DIAGNOSTICS.get(failure);
    if (targetDiagnostic == null) {
      return ordinaryAdmissionDiagnostic(failure);
    }
    return new DiagnosticDescriptor(
        failure.wireCode(),
        targetDiagnostic.message(),
        targetDiagnostic.contextHint() + " " + failure.admissionRemediation());
  }

  private static DiagnosticDescriptor backupAcknowledgementDiagnostic(
      AttestationVerificationFailure failure) {
    DiagnosticDescriptor ordinary = ordinaryAdmissionDiagnostic(failure);
    return new DiagnosticDescriptor(
        failure.wireCode(),
        "The backup artifact was published, but its source-book acknowledgement was rejected. "
            + ordinary.message(),
        "Retain the published backup pair. "
            + ordinary.hint()
            + " Rerun "
            + OperationId.BACKUP_BOOK.wireName()
            + " with the same backup ID.");
  }

  private static VerificationDiagnosticsDescriptor verificationDiagnostics(
      OperationId surface,
      String surfaceMessage,
      List<AttestationVerificationFailure> failures,
      boolean receiptRecovery) {
    return new VerificationDiagnosticsDescriptor(
        surface,
        failures.stream()
            .map(
                failure ->
                    new DiagnosticDescriptor(
                        failure.wireCode(),
                        surfaceMessage + " " + failure.description(),
                        receiptRecovery
                            ? VERIFY_RECEIPT_RECOVERY
                            : failure.verificationRemediation()))
            .toList());
  }

  private static AdmissionDiagnosticsDescriptor diagnosticContext(AdmissionContext context) {
    return switch (context) {
      case ORDINARY_LIVE_ADMISSION -> ORDINARY_LIVE_ADMISSION_DIAGNOSTICS;
      case REGISTRY_MUTATION -> REGISTRY_MUTATION_DIAGNOSTICS;
      case BACKUP_ACKNOWLEDGEMENT -> BACKUP_ACKNOWLEDGEMENT_DIAGNOSTICS;
    };
  }

  private static VerificationDiagnosticsDescriptor verificationSurface(OperationId surface) {
    return VERIFICATION_DIAGNOSTIC_SURFACES.stream()
        .filter(diagnostics -> diagnostics.surface() == surface)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported attestation verification diagnostic surface: "
                        + surface.wireName()));
  }

  private record RegistryTargetDiagnostic(String message, String contextHint) {}
}

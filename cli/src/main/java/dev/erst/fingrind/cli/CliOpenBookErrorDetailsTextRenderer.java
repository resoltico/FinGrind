package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliBookInspectionJsonModels;
import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Renders the evidence-preserving error details unique to protected-book initialization. */
final class CliOpenBookErrorDetailsTextRenderer {
  private static final String OPEN_BOOK_OPERATION = OperationId.OPEN_BOOK.wireName();

  private CliOpenBookErrorDetailsTextRenderer() {}

  static void appendRetainedArtifactRows(
      List<List<String>> rows,
      CliOpenBookErrorJsonModels.OpenBookPreparationArtifactsRetainedDetails details) {
    for (CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact artifact :
        details.retainedArtifacts()) {
      rows.add(List.of("Retained " + OPEN_BOOK_OPERATION + " artifact role", artifact.role()));
      rows.add(
          List.of(
              "Retained " + OPEN_BOOK_OPERATION + " artifact path",
              CliTextDisplay.serializedAbsolutePath(artifact.path())));
      if (artifact.retainedStage() != null) {
        rows.add(
            List.of(
                "Retained stage path",
                CliTextDisplay.serializedAbsolutePath(artifact.retainedStage())));
      }
    }
  }

  static void appendCompletionRows(
      List<List<String>> rows,
      CliOpenBookErrorJsonModels.OpenBookCompletionUncertainDetails details) {
    rows.add(
        List.of("Reported book file", CliTextDisplay.serializedAbsolutePath(details.bookFile())));
    rows.add(List.of("Reported initialized at", details.initializedAt()));
    rows.add(List.of("Reported attestation book ID", details.attestationBookId()));
    CliAttestationCommitPresentation.appendPayloadTextRows(
        rows, details.attestationCommit(), "No reported genesis operation was returned");
    appendCompletionBookIdentityRows(rows, details.bookIdentity());
    rows.add(
        List.of(
            "Reported founders",
            renderedTrustRootFounders(details.attestationTrustRoot().registry())));
    rows.add(
        List.of(
            "Reported initial quorum policy",
            renderedTrustRootPolicies(details.attestationTrustRoot().registry())));
    appendFounderKeyRows(rows, details.retainedFounderKeyArtifacts());
    appendRetainedBookArtifactRows(rows, details.retainedBookArtifacts());
  }

  private static void appendCompletionBookIdentityRows(
      List<List<String>> rows, CliBookInspectionJsonModels.BookIdentityPayload bookIdentity) {
    rows.add(List.of("Reported entity", bookIdentity.entityName()));
    rows.add(List.of("Reported accounting kernel", bookIdentity.accountingKernelProfile()));
    rows.add(List.of("Reported accounting basis", bookIdentity.accountingBasis()));
    rows.add(List.of("Reported accounting posture", bookIdentity.accountingFrameworkPosition()));
    rows.add(List.of("Reported entity form", bookIdentity.entityForm()));
    rows.add(List.of("Reported seed template", bookIdentity.bookTemplateId()));
    if (bookIdentity.inventoryCostingDoctrine() != null) {
      rows.add(List.of("Reported inventory costing", bookIdentity.inventoryCostingDoctrine()));
    }
    rows.add(List.of("Reported functional currency", bookIdentity.functionalCurrency()));
    rows.add(List.of("Reported fiscal year start", bookIdentity.fiscalYearStart()));
    rows.add(List.of("Reported book start effective date", bookIdentity.bookStartEffectiveDate()));
  }

  private static String renderedTrustRootFounders(
      CliAttestationJsonModels.AttestationRegistryPayload trustRoot) {
    return trustRoot.credentials().isEmpty()
        ? "(none)"
        : CliTextFormat.joined(
            trustRoot.credentials().stream()
                .map(credential -> credential.principalId() + "; keyId=" + credential.keyId())
                .toList());
  }

  private static String renderedTrustRootPolicies(
      CliAttestationJsonModels.AttestationRegistryPayload trustRoot) {
    return trustRoot.capabilityPolicies().isEmpty()
        ? "(none)"
        : CliTextFormat.joined(
            trustRoot.capabilityPolicies().stream()
                .map(policy -> policy.capability() + "; quorum=" + policy.quorum())
                .toList());
  }

  private static void appendFounderKeyRows(
      List<List<String>> rows, List<CliEnvelopeJsonModels.SuccessArtifact> founderKeys) {
    for (CliEnvelopeJsonModels.SuccessArtifact founderKey : founderKeys) {
      rows.add(
          List.of(
              "New founder key file", CliTextDisplay.serializedAbsolutePath(founderKey.path())));
      if (founderKey.retainedStage() != null) {
        rows.add(
            List.of(
                "Founder-key retained stage",
                CliTextDisplay.serializedAbsolutePath(founderKey.retainedStage())));
      } else {
        rows.add(
            List.of(
                "Founder-key publication transaction",
                java.util.Objects.requireNonNull(
                        founderKey.publicationTransaction(), "founder key transaction")
                    .id()));
      }
    }
  }

  private static void appendRetainedBookArtifactRows(
      List<List<String>> rows,
      List<CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact> retainedBookArtifacts) {
    for (CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact artifact :
        retainedBookArtifacts) {
      rows.add(List.of("Retained book artifact role", artifact.role()));
      rows.add(
          List.of(
              "Retained book artifact path",
              CliTextDisplay.serializedAbsolutePath(artifact.path())));
      if (artifact.retainedStage() != null) {
        rows.add(
            List.of(
                "Retained stage path",
                CliTextDisplay.serializedAbsolutePath(artifact.retainedStage())));
      }
    }
  }
}

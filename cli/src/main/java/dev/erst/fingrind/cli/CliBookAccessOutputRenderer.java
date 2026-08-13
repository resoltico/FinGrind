package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders operator-facing text for book access and key-management mutations. */
final class CliBookAccessOutputRenderer {
  private CliBookAccessOutputRenderer() {}

  static String renderGeneratedBookKeyFileText(GeneratedBookKeyFile generatedKeyFile) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(
        List.of(
            "Book key file",
            CliTextDisplay.path(generatedKeyFile.publication().publishedArtifactPath())));
    rows.add(List.of("Encoding", generatedKeyFile.encoding()));
    rows.add(List.of("Entropy bits", Integer.toString(generatedKeyFile.entropyBits())));
    rows.add(List.of("Permissions", generatedKeyFile.permissions()));
    rows.add(
        List.of(
            "Publication transaction",
            generatedKeyFile.publication().transactionResult().transactionId().value()));
    return CliTextFormat.renderTitledBlock(
        "Book Key File Generated", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderAttestationKeyFileMetadata(
      String title,
      AttestationKeyFileMetadata metadata,
      @Nullable PublicationTransactionArtifact publication) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(
        List.of("Attestation key file", CliTextDisplay.path(metadata.attestationKeyFilePath())));
    rows.add(List.of("Credential SPKI", metadata.credentialSpki()));
    rows.add(List.of("Key ID", metadata.keyId()));
    if (publication != null) {
      rows.add(
          List.of(
              "Publication transaction", publication.transactionResult().transactionId().value()));
    }
    return CliTextFormat.renderTitledBlock(
        title, CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderOpenBookText(Path bookFilePath, OpenBookResult.Opened opened) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(bookFilePath)));
    rows.addAll(CliBookIdentityDisplay.rows(opened.bookIdentity()));
    rows.add(List.of("Initialized at", CliTextDisplay.instant(opened.initializedAt())));
    rows.add(List.of("Attestation book ID", opened.attestationTrustRoot().bookId().toString()));
    CliAttestationCommitPresentation.appendTextRows(
        rows, opened.attestationCommit(), "No attestation operation was returned");
    rows.add(
        List.of(
            "Founders",
            CliAttestationPayloadMapper.renderedCredentials(
                opened.attestationTrustRoot().credentials())));
    rows.add(
        List.of(
            "Initial quorum policy",
            CliAttestationPayloadMapper.renderedCapabilityPolicies(
                opened.attestationTrustRoot().capabilityPolicies())));
    appendPublishedFounderKeyRows(rows, opened.publishedFounderKeyArtifacts());
    return CliTextFormat.renderTitledBlock(
        "Book Initialized", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderRekeyBookText(RekeyBookResult.Rekeyed rekeyed) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(rekeyed.bookFilePath())));
    rows.add(List.of("New book key file", CliTextDisplay.path(rekeyed.newBookKeyFilePath())));
    rows.add(
        List.of("Pair publication completion", rekeyed.pairPublicationCompletion().wireValue()));
    CliProtectedBookPairPublicationPresentation.appendTextRows(rows, rekeyed.pairPublication());
    CliAttestationCommitPresentation.appendTextRows(
        rows, rekeyed.attestationCommit(), "No attestation operation was returned");
    return CliTextFormat.renderTitledBlock(
        "Book Rekeyed", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  private static void appendPublishedFounderKeyRows(
      List<List<String>> rows, List<PublicationTransactionArtifact> publishedFounderKeyArtifacts) {
    for (PublicationTransactionArtifact publication : publishedFounderKeyArtifacts) {
      rows.add(
          List.of(
              "New founder key file", CliTextDisplay.path(publication.publishedArtifactPath())));
      rows.add(
          List.of(
              "Founder-key publication transaction",
              publication.transactionResult().transactionId().value()));
    }
  }
}

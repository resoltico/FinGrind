package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import java.nio.file.Path;
import java.util.List;

/** Renders operator-facing text for book access and key-management mutations. */
final class CliBookAccessOutputRenderer {
  private CliBookAccessOutputRenderer() {}

  static String renderGeneratedBookKeyFileText(
      GeneratedBookKeyFile generatedKeyFile, List<Path> tightenedParentDirectories) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book key file", CliTextDisplay.path(generatedKeyFile.bookKeyFilePath())));
    rows.add(List.of("Encoding", generatedKeyFile.encoding()));
    rows.add(List.of("Entropy bits", Integer.toString(generatedKeyFile.entropyBits())));
    rows.add(List.of("Permissions", generatedKeyFile.permissions()));
    appendTightenedParentRows(rows, tightenedParentDirectories);
    return CliTextFormat.renderTitledBlock(
        "Book Key File Generated", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderAttestationKeyFileMetadata(
      String title, AttestationKeyFileMetadata metadata) {
    return CliTextFormat.renderTitledBlock(
        title,
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Attestation key file", CliTextDisplay.path(metadata.attestationKeyFilePath())),
                List.of("Credential SPKI", metadata.credentialSpki()),
                List.of("Key ID", metadata.keyId()))));
  }

  static String renderOpenBookText(
      Path bookFilePath, List<Path> tightenedParentDirectories, OpenBookResult.Opened opened) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(bookFilePath)));
    rows.addAll(CliBookIdentityDisplay.rows(opened.bookIdentity()));
    rows.add(List.of("Initialized at", CliTextDisplay.instant(opened.initializedAt())));
    rows.add(List.of("Attestation book ID", opened.attestationTrustRoot().bookId().toString()));
    rows.add(List.of("Genesis operation head", opened.attestationTrustRoot().operationHeadHex()));
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
    appendTightenedParentRows(rows, tightenedParentDirectories);
    return CliTextFormat.renderTitledBlock(
        "Book Initialized", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderRekeyBookText(RekeyBookResult.Rekeyed rekeyed, Path newBookKeyFilePath) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Book file", CliTextDisplay.path(rekeyed.bookFilePath())));
    rows.add(List.of("New book key file", CliTextDisplay.path(newBookKeyFilePath)));
    return CliTextFormat.renderTitledBlock(
        "Book Rekeyed", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  private static void appendTightenedParentRows(
      List<List<String>> rows, List<Path> tightenedParentDirectories) {
    tightenedParentDirectories.forEach(
        tightenedParentDirectory ->
            rows.add(
                List.of(
                    "Tightened parent directory", CliTextDisplay.path(tightenedParentDirectory))));
  }
}

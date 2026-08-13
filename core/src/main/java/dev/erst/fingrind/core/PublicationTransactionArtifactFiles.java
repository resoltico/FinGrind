package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Exact private-file operations used by transaction-owned stage, commit, and residue cleanup. */
final class PublicationTransactionArtifactFiles {
  private PublicationTransactionArtifactFiles() {}

  static PublicationTransactionStagedArtifact createStage(Path stagePath, byte[] bytes)
      throws IOException {
    Path checkedStagePath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(stagePath, "stagePath");
    byte[] checkedBytes = Objects.requireNonNull(bytes, "bytes");
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(checkedStagePath)) {
      PublicationTransactionArtifactChannels.writeExactly(opened, checkedBytes);
      opened.force();
      return new PublicationTransactionStagedArtifact(
          checkedStagePath,
          opened.physicalObjectIdentity(),
          CryptographicPrimitives.sha256Hex(checkedBytes));
    }
  }

  /**
   * Copies one admitted owner-only source through retained no-follow handles into a fresh stage.
   *
   * <p>The source pathname never enters the journal. The returned stage evidence is derived from
   * the exact forced stage channel, rather than trusting a separately reopened source pathname.
   */
  static PublicationTransactionStagedArtifact createStage(Path stagePath, Path privateSourcePath)
      throws IOException {
    Path checkedStagePath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(stagePath, "stagePath");
    Path checkedSourcePath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(
            privateSourcePath, "privateSourcePath");
    try (PrivateOutputFile.OpenedFile source =
            PrivateOutputFile.openExisting(checkedSourcePath, PrivateOutputFile.Access.READ_ONLY);
        PrivateOutputFile.OpenedFile stage = PrivateOutputFile.createNew(checkedStagePath)) {
      source.position(0L);
      PublicationTransactionArtifactChannels.copyExactly(source, stage);
      stage.force();
      stage.position(0L);
      return new PublicationTransactionStagedArtifact(
          checkedStagePath,
          stage.physicalObjectIdentity(),
          CryptographicChannelDigest.sha256Hex(stage));
    }
  }

  static void createNoReplaceHardLink(Path finalPath, Path stagePath) throws IOException {
    Files.createLink(
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath"),
        PublicationTransactionStagedArtifact.normalizedArtifactPath(stagePath, "stagePath"));
  }

  static void replaceFinalWithStage(Path finalPath, Path stagePath) throws IOException {
    Files.move(
        PublicationTransactionStagedArtifact.normalizedArtifactPath(stagePath, "stagePath"),
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath"),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
  }
}

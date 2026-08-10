package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
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
      writeExactly(opened, checkedBytes);
      opened.force();
      return new PublicationTransactionStagedArtifact(
          checkedStagePath,
          opened.physicalObjectIdentity(),
          CryptographicPrimitives.sha256Hex(checkedBytes));
    }
  }

  static PublicationTransactionFinalizedArtifact finalEvidence(Path finalPath) throws IOException {
    PublicationTransactionFileEvidence evidence = evidence(finalPath);
    return new PublicationTransactionFinalizedArtifact(
        evidence.physicalIdentity(), evidence.sha256Hex());
  }

  static void createNoReplaceHardLink(Path finalPath, Path stagePath) throws IOException {
    Files.createLink(
        PublicationTransactionStagedArtifact.normalizedArtifactPath(finalPath, "finalPath"),
        PublicationTransactionStagedArtifact.normalizedArtifactPath(stagePath, "stagePath"));
  }

  static void requireSafeResidueRemoval(PublicationTransactionMember member) throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionStagedArtifact staged = checkedMember.stagedArtifact().orElseThrow();
    PublicationTransactionFinalizedArtifact finalized =
        checkedMember.finalizedArtifact().orElseThrow();
    PublicationTransactionFileEvidence currentStage = evidence(staged.stagePath());
    PublicationTransactionFileEvidence currentFinal = evidence(checkedMember.finalPath());
    if (!staged.physicalIdentity().equals(currentStage.physicalIdentity())
        || !staged.sha256Hex().equals(currentStage.sha256Hex())
        || !finalized.physicalIdentity().equals(currentFinal.physicalIdentity())
        || !finalized.sha256Hex().equals(currentFinal.sha256Hex())
        || !currentStage.physicalIdentity().equals(currentFinal.physicalIdentity())) {
      throw new IOException(
          "Publication transaction residue no longer matches its authenticated staged and final evidence.");
    }
  }

  static void deleteStageAfterFreshValidation(PublicationTransactionMember member)
      throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    requireSafeResidueRemoval(checkedMember);
    Files.delete(checkedMember.stagePath());
  }

  private static PublicationTransactionFileEvidence evidence(Path path) throws IOException {
    try (PrivateOutputFile.OpenedFile opened =
        PrivateOutputFile.openExisting(
            PublicationTransactionStagedArtifact.normalizedArtifactPath(path, "artifactPath"),
            PrivateOutputFile.Access.READ_ONLY)) {
      return new PublicationTransactionFileEvidence(
          opened.physicalObjectIdentity(), CryptographicChannelDigest.sha256Hex(opened));
    }
  }

  static void writeExactly(WritableByteChannel channel, byte[] bytes) throws IOException {
    WritableByteChannel checkedChannel = Objects.requireNonNull(channel, "channel");
    ByteBuffer pending = ByteBuffer.wrap(bytes);
    while (pending.hasRemaining()) {
      if (checkedChannel.write(pending) <= 0) {
        throw new IOException("FinGrind could not write the complete transaction-owned stage.");
      }
    }
  }
}

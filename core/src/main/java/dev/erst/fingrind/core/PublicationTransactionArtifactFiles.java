package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

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

  static Optional<PublicationTransactionFileEvidence> evidenceIfPresent(Path path)
      throws IOException {
    Path checkedPath =
        PublicationTransactionStagedArtifact.normalizedArtifactPath(path, "artifactPath");
    if (Files.notExists(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    return Optional.of(evidence(checkedPath));
  }

  static void requireCurrentStageEvidence(PublicationTransactionMember member) throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionStagedArtifact staged = checkedMember.stagedArtifact().orElseThrow();
    if (!matches(staged, evidence(staged.stagePath()))) {
      throw new IOException(
          "Publication transaction stage no longer matches its authenticated evidence.");
    }
  }

  static void requireCurrentFinalEvidence(PublicationTransactionMember member) throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionStagedArtifact staged = checkedMember.stagedArtifact().orElseThrow();
    PublicationTransactionFinalizedArtifact finalized =
        checkedMember.finalizedArtifact().orElseThrow();
    PublicationTransactionFileEvidence currentFinal = evidence(checkedMember.finalPath());
    if (!matches(staged, currentFinal) || !matches(finalized, currentFinal)) {
      throw new IOException(
          "Publication transaction final no longer matches its authenticated evidence.");
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

  static void requireSafeResidueRemoval(PublicationTransactionMember member) throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionStagedArtifact staged = checkedMember.stagedArtifact().orElseThrow();
    PublicationTransactionFinalizedArtifact finalized =
        checkedMember.finalizedArtifact().orElseThrow();
    PublicationTransactionFileEvidence currentStage = evidence(staged.stagePath());
    PublicationTransactionFileEvidence currentFinal = evidence(checkedMember.finalPath());
    if (!matches(staged, currentStage)
        || !matches(finalized, currentFinal)
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

  static PublicationTransactionFileEvidence evidence(Path path) throws IOException {
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

  private static boolean matches(
      PublicationTransactionStagedArtifact expected, PublicationTransactionFileEvidence actual) {
    return expected.physicalIdentity().equals(actual.physicalIdentity())
        && expected.sha256Hex().equals(actual.sha256Hex());
  }

  private static boolean matches(
      PublicationTransactionFinalizedArtifact expected, PublicationTransactionFileEvidence actual) {
    return expected.physicalIdentity().equals(actual.physicalIdentity())
        && expected.sha256Hex().equals(actual.sha256Hex());
  }
}

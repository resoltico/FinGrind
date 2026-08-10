package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
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
      copyExactly(source, stage);
      stage.force();
      stage.position(0L);
      return new PublicationTransactionStagedArtifact(
          checkedStagePath,
          stage.physicalObjectIdentity(),
          CryptographicChannelDigest.sha256Hex(stage));
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

  static void copyExactly(ReadableByteChannel source, WritableByteChannel destination)
      throws IOException {
    ReadableByteChannel checkedSource = Objects.requireNonNull(source, "source");
    WritableByteChannel checkedDestination = Objects.requireNonNull(destination, "destination");
    ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
    while (true) {
      int read = checkedSource.read(buffer);
      if (read < 0) {
        return;
      }
      if (read == 0) {
        throw new IOException("FinGrind could not read the complete transaction private source.");
      }
      buffer.flip();
      while (buffer.hasRemaining()) {
        if (checkedDestination.write(buffer) <= 0) {
          throw new IOException("FinGrind could not write the complete transaction-owned stage.");
        }
      }
      buffer.clear();
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

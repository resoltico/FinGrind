package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Creates the immutable, fully identified journal plan before any secret stage can materialize. */
final class PublicationTransactionPlan {
  private PublicationTransactionPlan() {}

  static PublicationTransactionJournal prepare(
      PublicationTransactionRequest request, PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionRequest checkedRequest = Objects.requireNonNull(request, "request");
    PublicationTransactionRuntime checkedRuntime = Objects.requireNonNull(runtime, "runtime");
    List<PublicationTransactionMember> members = new ArrayList<>();
    for (PublicationTransactionMemberRequest member : checkedRequest.members()) {
      members.add(plannedMember(member));
    }
    Instant createdAt = checkedRuntime.now();
    return PublicationTransactionJournal.prepared(
        PublicationTransactionId.fresh(),
        HexFormat.of().formatHex(CryptographicPrimitives.secureBytes(16)),
        checkedRuntime.repository().ownerKeyFingerprint(),
        checkedRequest.ownerContext(),
        createdAt,
        members);
  }

  static List<Path> leaseDirectories(PublicationTransactionJournal journal) {
    PublicationTransactionJournal checkedJournal = Objects.requireNonNull(journal, "journal");
    return checkedJournal.members().stream()
        .map(PublicationTransactionPlan::parentDirectory)
        .toList();
  }

  static void requireCurrentPrivateDirectories(PublicationTransactionJournal journal)
      throws IOException {
    for (PublicationTransactionMember member :
        Objects.requireNonNull(journal, "journal").members()) {
      Path parent = parentDirectory(member);
      PrivateOutputDirectory.requireExistingOwnerOnly(parent);
      if (!member
          .physicalDirectoryIdentity()
          .equals(PrivateOutputDirectory.physicalObjectIdentity(parent))) {
        throw new IOException(
            "Publication member directory changed physical identity after planning.");
      }
    }
  }

  private static PublicationTransactionMember plannedMember(
      PublicationTransactionMemberRequest request) throws IOException {
    PublicationTransactionMemberRequest checkedRequest = Objects.requireNonNull(request, "request");
    Path requestedFinalPath = checkedRequest.finalPath();
    Path parent = Objects.requireNonNull(requestedFinalPath.getParent(), "final artifact parent");
    PrivateOutputDirectory.requireExistingOwnerOnly(parent);
    Path finalPath = parent.toRealPath().resolve(requestedFinalPath.getFileName());
    java.util.Optional<PublicationTransactionFinalizedArtifact> replacementTarget =
        checkedRequest.publicationMode() == PublicationMode.REPLACE
                && java.nio.file.Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)
            ? java.util.Optional.of(PublicationTransactionArtifactFiles.finalEvidence(finalPath))
            : java.util.Optional.empty();
    return new PublicationTransactionMember(
        checkedRequest.memberId(),
        checkedRequest.role(),
        finalPath,
        stagePath(parent),
        PrivateOutputDirectory.physicalObjectIdentity(parent),
        checkedRequest.publicationMode(),
        replacementTarget,
        PublicationTransactionMemberProgress.PLANNED,
        java.util.Optional.empty(),
        java.util.Optional.empty());
  }

  private static Path stagePath(Path parent) {
    return parent.resolve(
        ".fingrind-stage-" + HexFormat.of().formatHex(CryptographicPrimitives.secureBytes(16)));
  }

  private static Path parentDirectory(PublicationTransactionMember member) {
    return Objects.requireNonNull(member.finalPath().getParent(), "final artifact parent");
  }
}

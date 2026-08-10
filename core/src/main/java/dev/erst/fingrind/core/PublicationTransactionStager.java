package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Materializes each caller-owned secret only at the durable stage path predeclared by its journal.
 */
final class PublicationTransactionStager {
  private PublicationTransactionStager() {}

  static PublicationTransactionJournal stageAll(
      PublicationTransactionJournal journal,
      PublicationTransactionRequest request,
      PublicationTransactionRuntime runtime)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    List<PublicationTransactionMemberRequest> requests =
        Objects.requireNonNull(request, "request").members();
    if (current.members().size() != requests.size()) {
      throw new IllegalArgumentException(
          "Publication transaction request no longer matches its journal plan.");
    }
    for (int index = 0; index < requests.size(); index++) {
      PublicationTransactionMember member = current.members().get(index);
      PublicationTransactionMemberRequest requested = requests.get(index);
      requireSamePlanMember(member, requested);
      if (member.progress() != PublicationTransactionMemberProgress.PLANNED) {
        throw new IllegalArgumentException(
            "A fresh publication transaction cannot restage a journal member.");
      }
      PublicationTransactionPlan.requireCurrentPrivateDirectories(current);
      PublicationTransactionStagedArtifact staged = stage(member, requested);
      Path parent = Objects.requireNonNull(member.stagePath().getParent(), "stage artifact parent");
      runtime.forceDirectory(parent, PublicationTransactionFaultPoint.STAGE_DIRECTORY_FORCED);
      current =
          runtime.updateMembers(
              current,
              PublicationTransactionMemberUpdates.staged(current, index, staged),
              PublicationTransactionFaultPoint.MEMBER_STAGED);
    }
    return current;
  }

  private static PublicationTransactionStagedArtifact stage(
      PublicationTransactionMember member, PublicationTransactionMemberRequest request)
      throws IOException {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionMemberRequest checkedRequest = Objects.requireNonNull(request, "request");
    if (checkedRequest.hasPrivateSource()) {
      return PublicationTransactionArtifactFiles.createStage(
          checkedMember.stagePath(), checkedRequest.privateSourcePathForStaging());
    }
    return PublicationTransactionArtifactFiles.createStage(
        checkedMember.stagePath(), checkedRequest.secretBytesForStaging());
  }

  private static void requireSamePlanMember(
      PublicationTransactionMember member, PublicationTransactionMemberRequest request) {
    PublicationTransactionMember checkedMember = Objects.requireNonNull(member, "member");
    PublicationTransactionMemberRequest checkedRequest = Objects.requireNonNull(request, "request");
    if (!checkedMember.memberId().equals(checkedRequest.memberId())
        || checkedMember.role() != checkedRequest.role()
        || !checkedMember.finalPath().equals(checkedRequest.finalPath())
        || checkedMember.publicationMode() != checkedRequest.publicationMode()) {
      throw new IllegalArgumentException(
          "Publication transaction request no longer matches its journal plan.");
    }
  }
}

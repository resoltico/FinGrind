package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels.PairPublicationRetentionPayload;
import org.jspecify.annotations.Nullable;

/** Rejection details that still carry a durably published protected-book pair. */
public interface CliBookLifecycleRejectionJsonModels {
  /**
   * A backup pair was published or recovered, but the subsequent acknowledgement authorization was
   * refused. The pair facts remain visible because the failed acknowledgement did not undo either
   * final member.
   */
  record BackupAcknowledgementAuthorizationRejectedDetails(
      String bookFile,
      String backupFile,
      String backupKeyFile,
      String backupId,
      CliBookPairPublicationJsonModels.PairPublicationCompletionPayload pairPublicationCompletion,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @Nullable PairPublicationRetentionPayload pairPublicationRetention)
      implements CliRejectionJsonModels.RejectionDetails {
    public BackupAcknowledgementAuthorizationRejectedDetails {
      bookFile = requireText(bookFile, "bookFile");
      backupFile = requireText(backupFile, "backupFile");
      backupKeyFile = requireText(backupKeyFile, "backupKeyFile");
      backupId = requireText(backupId, "backupId");
      pairPublicationRetention =
          CliBookPairPublicationJsonModels.requirePairPublicationRetention(
              java.util.Objects.requireNonNull(
                  pairPublicationCompletion, "pairPublicationCompletion"),
              pairPublicationRetention);
      CliPairPublicationRetentionTargetBinding.requireExactTargets(
          backupFile, backupKeyFile, pairPublicationRetention);
    }
  }
}

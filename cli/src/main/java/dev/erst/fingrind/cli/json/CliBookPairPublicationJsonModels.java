package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import org.jspecify.annotations.Nullable;

/** Protected-book pair-publication and acknowledgement JSON records emitted by the CLI. */
public interface CliBookPairPublicationJsonModels {
  /** Closed wire vocabulary for this invocation's final protected-book pair disposition. */
  enum PairPublicationCompletionPayload implements dev.erst.fingrind.core.WireValue {
    PUBLISHED("published"),
    RECOVERED("recovered"),
    ALREADY_PUBLISHED("already-published");

    private final String wireValue;

    PairPublicationCompletionPayload(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
      return wireValue;
    }

    /** Maps the public maintenance result without exposing Java enum identifiers on the wire. */
    public static PairPublicationCompletionPayload from(
        dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion completion) {
      return switch (java.util.Objects.requireNonNull(completion, "completion")) {
        case PUBLISHED -> PUBLISHED;
        case RECOVERED -> RECOVERED;
        case ALREADY_PUBLISHED -> ALREADY_PUBLISHED;
      };
    }

    /** Maps the closed wire vocabulary back to its public maintenance-contract value. */
    public dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
        toContract() {
      return switch (this) {
        case PUBLISHED ->
            dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion.PUBLISHED;
        case RECOVERED ->
            dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion.RECOVERED;
        case ALREADY_PUBLISHED ->
            dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
                .ALREADY_PUBLISHED;
      };
    }
  }

  /**
   * Authoritative final-and-stage facts for both members of one protected-book pair publication.
   */
  record PairPublicationRetentionPayload(
      PairPublicationMemberPublicationPayload bookPublication,
      PairPublicationMemberPublicationPayload generatedSecretPublication) {
    public PairPublicationRetentionPayload {
      java.util.Objects.requireNonNull(bookPublication, "bookPublication");
      java.util.Objects.requireNonNull(generatedSecretPublication, "generatedSecretPublication");
      if (bookPublication.path().equals(generatedSecretPublication.path())
          || bookPublication.retainedStage().equals(generatedSecretPublication.retainedStage())
          || bookPublication.path().equals(generatedSecretPublication.retainedStage())
          || generatedSecretPublication.path().equals(bookPublication.retainedStage())) {
        throw new IllegalArgumentException(
            "Protected-book pair publication retention requires four distinct final and stage paths.");
      }
    }
  }

  /** One authoritative final artifact path and its exact retained private publication stage. */
  record PairPublicationMemberPublicationPayload(String path, String retainedStage) {
    public PairPublicationMemberPublicationPayload {
      path = requireText(path, "path");
      retainedStage = requireText(retainedStage, "retainedStage");
      if (path.equals(retainedStage)) {
        throw new IllegalArgumentException(
            "A protected-book publication fact requires distinct final and retained-stage paths.");
      }
    }
  }

  /**
   * Enforces the exact final-and-stage facts required by one public pair-completion disposition.
   */
  static @Nullable PairPublicationRetentionPayload requirePairPublicationRetention(
      PairPublicationCompletionPayload completion,
      @Nullable PairPublicationRetentionPayload retention) {
    PairPublicationCompletionPayload checkedCompletion =
        java.util.Objects.requireNonNull(completion, "pairPublicationCompletion");
    return switch (checkedCompletion) {
      case PUBLISHED, RECOVERED ->
          java.util.Objects.requireNonNull(
              retention,
              "A completed FinGrind protected-book pair publication must report its final-and-stage facts.");
      case ALREADY_PUBLISHED -> {
        if (retention != null) {
          throw new IllegalArgumentException(
              "An externally already-published protected-book pair must not claim FinGrind retained stages.");
        }
        yield null;
      }
    };
  }

  record RekeyBookPayload(
      String bookFile,
      String newBookKeyFile,
      PairPublicationCompletionPayload pairPublicationCompletion,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @Nullable PairPublicationRetentionPayload pairPublicationRetention,
      AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public RekeyBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      newBookKeyFile = requireText(newBookKeyFile, "newBookKeyFile");
      PairPublicationCompletionPayload checkedCompletion =
          java.util.Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
          .requireRestoreOrRekeyCompletion(checkedCompletion.toContract());
      PairPublicationRetentionPayload requiredRetention =
          java.util.Objects.requireNonNull(
              requirePairPublicationRetention(checkedCompletion, pairPublicationRetention),
              "pairPublicationRetention");
      CliPairPublicationRetentionTargetBinding.requireExactTargets(
          bookFile, newBookKeyFile, requiredRetention);
      pairPublicationRetention = requiredRetention;
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /**
   * JSON vocabulary for whether a backup acknowledgement was appended, resumed, or already live.
   */
  enum BackupAcknowledgementStatePayload implements dev.erst.fingrind.core.WireValue {
    ACKNOWLEDGED("acknowledged"),
    RESUMED("resumed"),
    ALREADY_PRESENT("already-present"),
    PENDING("pending");

    private final String wireValue;

    BackupAcknowledgementStatePayload(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public String wireValue() {
      return wireValue;
    }

    /** Maps the contract acknowledgement state to its closed JSON wire vocabulary. */
    public static BackupAcknowledgementStatePayload from(
        dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState state) {
      return switch (java.util.Objects.requireNonNull(state, "state")) {
        case ACKNOWLEDGED -> ACKNOWLEDGED;
        case RESUMED -> RESUMED;
        case ALREADY_PRESENT -> ALREADY_PRESENT;
      };
    }

    /** Maps the closed wire vocabulary back to its public maintenance-contract value. */
    public dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState toContract() {
      return switch (this) {
        case ACKNOWLEDGED ->
            dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState.ACKNOWLEDGED;
        case RESUMED -> dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState.RESUMED;
        case ALREADY_PRESENT ->
            dev.erst.fingrind.contract.bookkeeping.BackupAcknowledgementState.ALREADY_PRESENT;
        case PENDING ->
            throw new IllegalStateException(
                "A pending backup acknowledgement has no completed contract state.");
      };
    }
  }

  record BackupBookPayload(
      String bookFile,
      String backupId,
      PairPublicationCompletionPayload pairPublicationCompletion,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @Nullable PairPublicationRetentionPayload pairPublicationRetention,
      BackupAcknowledgementStatePayload acknowledgementState,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @Nullable AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public BackupBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      backupId = requireText(backupId, "backupId");
      PairPublicationCompletionPayload checkedCompletion =
          java.util.Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      pairPublicationRetention =
          requirePairPublicationRetention(checkedCompletion, pairPublicationRetention);
      BackupAcknowledgementStatePayload checkedAcknowledgementState =
          java.util.Objects.requireNonNull(acknowledgementState, "acknowledgementState");
      if (checkedAcknowledgementState != BackupAcknowledgementStatePayload.PENDING) {
        dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
            .requireBackupCompletion(
                checkedCompletion.toContract(), checkedAcknowledgementState.toContract());
      }
      switch (checkedAcknowledgementState) {
        case ACKNOWLEDGED -> {
          if (attestationCommit == null) {
            throw new IllegalArgumentException(
                "An acknowledged backup must report its attestation operation.");
          }
        }
        case ALREADY_PRESENT, PENDING ->
            requireNoNewAttestationCommit(checkedAcknowledgementState, attestationCommit);
        case RESUMED -> {
          // A resumed invocation may append, or may find the exact acknowledgement already present.
        }
      }
    }
  }

  record RestoreBookPayload(
      String bookFile,
      String bookKeyFilePath,
      PairPublicationCompletionPayload pairPublicationCompletion,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
          @Nullable PairPublicationRetentionPayload pairPublicationRetention,
      AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public RestoreBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      bookKeyFilePath = requireText(bookKeyFilePath, "bookKeyFilePath");
      PairPublicationCompletionPayload checkedCompletion =
          java.util.Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
          .requireRestoreOrRekeyCompletion(checkedCompletion.toContract());
      PairPublicationRetentionPayload requiredRetention =
          java.util.Objects.requireNonNull(
              requirePairPublicationRetention(checkedCompletion, pairPublicationRetention),
              "pairPublicationRetention");
      CliPairPublicationRetentionTargetBinding.requireExactTargets(
          bookFile, bookKeyFilePath, requiredRetention);
      pairPublicationRetention = requiredRetention;
      java.util.Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  private static void requireNoNewAttestationCommit(
      BackupAcknowledgementStatePayload acknowledgementState,
      @Nullable AttestationCommitPayload attestationCommit) {
    if (attestationCommit != null) {
      throw new IllegalArgumentException(
          acknowledgementState
              + " backup acknowledgement must not report a newly appended operation.");
    }
  }
}

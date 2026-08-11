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

  /** Authoritative final-only facts for both members of one protected-book pair publication. */
  record PairPublicationPayload(
      PairPublicationMemberPayload bookPublication,
      PairPublicationMemberPayload generatedSecretPublication,
      CliEnvelopeJsonModels.PublicationTransaction publicationTransaction) {
    public PairPublicationPayload {
      java.util.Objects.requireNonNull(bookPublication, "bookPublication");
      java.util.Objects.requireNonNull(generatedSecretPublication, "generatedSecretPublication");
      if (bookPublication.path().equals(generatedSecretPublication.path())) {
        throw new IllegalArgumentException(
            "Protected-book pair publication requires distinct final artifact paths.");
      }
      java.util.Objects.requireNonNull(publicationTransaction, "publicationTransaction");
    }
  }

  /** One authoritative final artifact path from a completed transaction-owned pair publication. */
  record PairPublicationMemberPayload(String path) {
    public PairPublicationMemberPayload {
      path = requireText(path, "path");
    }
  }

  /** Enforces the final-only facts required by one public pair-completion disposition. */
  static @Nullable PairPublicationPayload requirePairPublication(
      PairPublicationCompletionPayload completion, @Nullable PairPublicationPayload publication) {
    PairPublicationCompletionPayload checkedCompletion =
        java.util.Objects.requireNonNull(completion, "pairPublicationCompletion");
    return switch (checkedCompletion) {
      case PUBLISHED, RECOVERED ->
          java.util.Objects.requireNonNull(
              publication,
              "A completed FinGrind protected-book pair publication must report its final artifacts.");
      case ALREADY_PUBLISHED -> {
        if (publication != null) {
          throw new IllegalArgumentException(
              "An externally already-published protected-book pair must not claim a FinGrind publication transaction.");
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
          @Nullable PairPublicationPayload pairPublication,
      AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public RekeyBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      newBookKeyFile = requireText(newBookKeyFile, "newBookKeyFile");
      PairPublicationCompletionPayload checkedCompletion =
          java.util.Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
          .requireRestoreOrRekeyCompletion(checkedCompletion.toContract());
      PairPublicationPayload requiredPublication =
          java.util.Objects.requireNonNull(
              requirePairPublication(checkedCompletion, pairPublication), "pairPublication");
      CliPairPublicationTargetBinding.requireExactTargets(
          bookFile, newBookKeyFile, requiredPublication);
      pairPublication = requiredPublication;
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
          @Nullable PairPublicationPayload pairPublication,
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
      pairPublication = requirePairPublication(checkedCompletion, pairPublication);
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
          @Nullable PairPublicationPayload pairPublication,
      AttestationCommitPayload attestationCommit)
      implements CliSuccessPayload {
    public RestoreBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      bookKeyFilePath = requireText(bookKeyFilePath, "bookKeyFilePath");
      PairPublicationCompletionPayload checkedCompletion =
          java.util.Objects.requireNonNull(pairPublicationCompletion, "pairPublicationCompletion");
      dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion
          .requireRestoreOrRekeyCompletion(checkedCompletion.toContract());
      PairPublicationPayload requiredPublication =
          java.util.Objects.requireNonNull(
              requirePairPublication(checkedCompletion, pairPublication), "pairPublication");
      CliPairPublicationTargetBinding.requireExactTargets(
          bookFile, bookKeyFilePath, requiredPublication);
      pairPublication = requiredPublication;
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

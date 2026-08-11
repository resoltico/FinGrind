package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Public disposition of the final protected-book pair publication on this invocation. */
public enum ProtectedBookPairPublicationCompletion implements WireValue {
  /** This invocation durably published the final protected-book pair. */
  PUBLISHED,

  /** This invocation reconciled an earlier completion-uncertain pair without another mutation. */
  RECOVERED,

  /** An acknowledgement retry verified an existing complete backup pair without publishing it. */
  ALREADY_PUBLISHED;

  @Override
  public String wireValue() {
    return switch (this) {
      case PUBLISHED -> "published";
      case RECOVERED -> "recovered";
      case ALREADY_PUBLISHED -> "already-published";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtectedBookPairPublicationCompletion.class);
  }

  /** Requires the current final-only transaction proof for one completed pair disposition. */
  public static @Nullable ProtectedBookPairPublication requirePublication(
      ProtectedBookPairPublicationCompletion completion,
      @Nullable ProtectedBookPairPublication publication) {
    ProtectedBookPairPublicationCompletion checkedCompletion =
        java.util.Objects.requireNonNull(completion, "pairPublicationCompletion");
    return switch (checkedCompletion) {
      case PUBLISHED, RECOVERED ->
          java.util.Objects.requireNonNull(
              publication,
              "A completed FinGrind protected-book pair publication must report its transaction proof.");
      case ALREADY_PUBLISHED -> {
        if (publication != null) {
          throw new IllegalArgumentException(
              "An externally already-published protected-book pair must not claim FinGrind transaction proof.");
        }
        yield null;
      }
    };
  }

  /**
   * Requires a completion value that can truthfully describe a restore or rekey publication.
   *
   * <p>{@link #ALREADY_PUBLISHED} is deliberately reserved for a backup acknowledgement retry:
   * restore and rekey either publish a pair or reconcile an explicit durable recovery record.
   */
  public static ProtectedBookPairPublicationCompletion requireRestoreOrRekeyCompletion(
      ProtectedBookPairPublicationCompletion completion) {
    ProtectedBookPairPublicationCompletion checked =
        java.util.Objects.requireNonNull(completion, "pairPublicationCompletion");
    if (checked == ALREADY_PUBLISHED) {
      throw new IllegalArgumentException(
          "Only a backup acknowledgement retry may report an already-published pair completion.");
    }
    return checked;
  }

  /**
   * Requires the completion/acknowledgement combination emitted for a completed backup command.
   *
   * <p>A newly or already acknowledged normal backup reports {@link #PUBLISHED}; an explicit resume
   * is the only completed acknowledgement that may report {@link #RECOVERED} or {@link
   * #ALREADY_PUBLISHED}.
   */
  public static ProtectedBookPairPublicationCompletion requireBackupCompletion(
      ProtectedBookPairPublicationCompletion completion,
      BackupAcknowledgementState acknowledgementState) {
    ProtectedBookPairPublicationCompletion checkedCompletion =
        java.util.Objects.requireNonNull(completion, "pairPublicationCompletion");
    BackupAcknowledgementState checkedState =
        java.util.Objects.requireNonNull(acknowledgementState, "acknowledgementState");
    return switch (checkedCompletion) {
      case PUBLISHED -> {
        if (checkedState == BackupAcknowledgementState.ACKNOWLEDGED
            || checkedState == BackupAcknowledgementState.ALREADY_PRESENT) {
          yield checkedCompletion;
        }
        throw invalidBackupCompletion(checkedCompletion, checkedState);
      }
      case RECOVERED, ALREADY_PUBLISHED -> {
        if (checkedState == BackupAcknowledgementState.RESUMED) {
          yield checkedCompletion;
        }
        throw invalidBackupCompletion(checkedCompletion, checkedState);
      }
    };
  }

  private static IllegalArgumentException invalidBackupCompletion(
      ProtectedBookPairPublicationCompletion completion,
      BackupAcknowledgementState acknowledgementState) {
    return new IllegalArgumentException(
        "Backup acknowledgement state "
            + acknowledgementState
            + " cannot report pair publication completion "
            + completion
            + ".");
  }
}

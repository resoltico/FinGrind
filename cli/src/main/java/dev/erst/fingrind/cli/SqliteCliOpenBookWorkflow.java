package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.AttestationGenesisPreparation;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqliteOpenBookCompletionUncertainException;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns the SQLite initialization protocol and every post-admission custody outcome. */
final class SqliteCliOpenBookWorkflow {
  /** Builds the complete successful public result from one known-durable opened-book outcome. */
  @FunctionalInterface
  interface OpenedBookResultFactory {
    /** Builds the public result and attaches the founder artifacts created for this opening. */
    OpenBookResult.Opened create(
        BookOpeningOutcome.Opened opened, AttestationGenesisPreparation preparation);
  }

  /**
   * One post-preparation opening result that must remain available until session close finishes.
   */
  sealed interface OpenBookExecution
      permits CompletedOpenBookExecution, CompletionUncertainOpenBookExecution {}

  /** One ordinary completed opening result. */
  record CompletedOpenBookExecution(OpenBookResult result) implements OpenBookExecution {
    CompletedOpenBookExecution {
      Objects.requireNonNull(result, "result");
    }
  }

  /** One completion that needs reconciliation even if the following session close also fails. */
  record CompletionUncertainOpenBookExecution(
      OpenBookResult.Opened opened, RuntimeException completionFailure)
      implements OpenBookExecution {
    CompletionUncertainOpenBookExecution {
      Objects.requireNonNull(opened, "opened");
      Objects.requireNonNull(completionFailure, "completionFailure");
    }
  }

  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;
  private final OpenedBookResultFactory openedBookResultFactory;

  SqliteCliOpenBookWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this(clock, passphraseResolver, SqliteCliOpenBookWorkflow::publishedOpenedBook);
  }

  SqliteCliOpenBookWorkflow(
      Clock clock,
      CliBookPassphraseResolver passphraseResolver,
      OpenedBookResultFactory openedBookResultFactory) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.openedBookResultFactory =
        Objects.requireNonNull(openedBookResultFactory, "openedBookResultFactory");
  }

  ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    ContractFailure destinationFailure =
        CliOpenBookArtifactCustody.occupiedBookDestinationFailure(bookAccess.bookFilePath());
    if (destinationFailure != null) {
      return ContractDecision.rejected(destinationFailure);
    }
    ContractFailure founderInputFailure = founderInputFailure(command);
    if (founderInputFailure != null) {
      return ContractDecision.rejected(founderInputFailure);
    }
    CliOpenBookSessionCloseFailureMapper closeFailureMapper =
        new CliOpenBookSessionCloseFailureMapper(bookAccess.bookFilePath());
    CliNewBookSessionHandler sessionHandler =
        new CliNewBookSessionHandler(this, bookAccess, command, closeFailureMapper);
    ContractDecision<OpenBookExecution> execution =
        SqliteCliWorkflowSessions.withNewBookAdministrationSessionDecision(
            SqliteAdministrationSessions.openNewBookResolved(
                bookAccess, passphraseResolver, SqlitePassphraseIntent.NEW_SECRET),
            sessionHandler::work,
            sessionHandler::rejected,
            sessionHandler::workFailure,
            sessionHandler::opened);
    return execution.fold(
        opened -> publishedExecution(opened, closeFailureMapper), ContractDecision::rejected);
  }

  /**
   * Prepares founders only after a non-mutating founder-input preflight and exclusive new-book
   * admission. The preflight rejects unusable credential inputs before another protected artifact
   * exists; admission then ensures a destination or session refusal cannot create a founder key. If
   * the exclusive session creates a provisional book before a later non-success decision, it
   * deliberately retains that path and any SQLite sidecars rather than unlinking a caller-selected
   * name that may have been replaced. Such retention never means the book was initialized; a later
   * open-book attempt rejects the occupied destination.
   */
  ContractDecision<AttestationGenesisPreparation> genesisPreparation(OpenBookCommand command) {
    OpenBookCommand checkedCommand = Objects.requireNonNull(command, "command");
    try {
      return ContractDecision.accepted(
          AttestationGenesisFactory.prepare(
              checkedCommand.bookIdentity(),
              clock.instant(),
              checkedCommand.attestationFounders()));
    } catch (RuntimeException exception) {
      return ContractDecision.rejected(CliOpenBookGenesisFailureMapper.failureFor(exception));
    }
  }

  /** Returns the precise founder-input rejection before opening a new SQLite book session. */
  private @Nullable ContractFailure founderInputFailure(OpenBookCommand command) {
    OpenBookCommand checkedCommand = Objects.requireNonNull(command, "command");
    try {
      AttestationGenesisFactory.validateFounderInputs(checkedCommand.attestationFounders());
      return null;
    } catch (RuntimeException exception) {
      return CliOpenBookGenesisFailureMapper.failureFor(exception);
    }
  }

  /**
   * Completes the only post-preparation opening path.
   *
   * <p>An exclusive newly opened SQLite session has no preexisting initialized state, so the
   * bookkeeping rejection alternative is unreachable in ordinary execution. The defensive branch
   * still retains freshly generated founders and every possible new-book artifact before returning
   * it, which keeps that invariant truthful if a storage seam ever violates its exclusive-session
   * contract.
   */
  ContractDecision<OpenBookResult> completeOpenedBook(
      Path bookFilePath,
      SqliteAdministrationSession bookSession,
      OpenBookCommand command,
      AttestationGenesisPreparation preparation) {
    CliOpenBookSessionCloseFailureMapper closeFailureMapper =
        new CliOpenBookSessionCloseFailureMapper(bookFilePath);
    return completeOpenedBookExecution(bookFilePath, bookSession, command, preparation)
        .fold(opened -> publishedExecution(opened, closeFailureMapper), ContractDecision::rejected);
  }

  ContractDecision<OpenBookExecution> completeOpenedBookExecution(
      Path bookFilePath,
      SqliteAdministrationSession bookSession,
      OpenBookCommand command,
      AttestationGenesisPreparation preparation) {
    BookOpeningOutcome outcome;
    try {
      outcome =
          new BookAdministrationService(bookSession, bookSession, bookSession, clock)
              .openAttestedBook(
                  BookkeepingRequestPublishedLanguageTranslator.fromPublished(command),
                  preparation.evidence());
    } catch (SqliteOpenBookCompletionUncertainException exception) {
      return ContractDecision.accepted(
          new CompletionUncertainOpenBookExecution(
              reconciliationOpenedBook(exception.openedBook(), preparation), exception));
    } catch (RuntimeException exception) {
      return ContractDecision.rejected(
          CliOpenBookArtifactCustody.preparationArtifactsRetainedFailure(
              CliOpenBookArtifactCustody.retainedOpeningArtifacts(bookFilePath, preparation)));
    }
    return publishReturnedBookOpening(bookFilePath, outcome, preparation);
  }

  /**
   * Projects one storage-returned opening outcome into the CLI contract.
   *
   * <p>An {@link BookOpeningOutcome.Opened} outcome has crossed the durable SQLite success
   * boundary. Projection and response-construction failures here must retain founder custody for
   * that persisted genesis.
   */
  private ContractDecision<OpenBookExecution> publishReturnedBookOpening(
      Path bookFilePath, BookOpeningOutcome outcome, AttestationGenesisPreparation preparation) {
    BookOpeningOutcome checkedOutcome = Objects.requireNonNull(outcome, "outcome");
    AttestationGenesisPreparation checkedPreparation =
        Objects.requireNonNull(preparation, "preparation");
    return switch (checkedOutcome) {
      case BookOpeningOutcome.Opened opened ->
          knownDurableOpenedExecution(opened, checkedPreparation);
      case BookOpeningOutcome.Rejected _ ->
          ContractDecision.rejected(
              CliOpenBookArtifactCustody.preparationArtifactsRetainedFailure(
                  CliOpenBookArtifactCustody.retainedOpeningArtifacts(
                      bookFilePath, checkedPreparation)));
    };
  }

  /**
   * Keeps known-durable opening facts available when response translation or augmentation fails.
   *
   * <p>The fallback deliberately rebuilds from the durable bookkeeping outcome rather than the
   * failed presentation path, so the public completion-uncertain contract remains reconcilable.
   */
  private ContractDecision<OpenBookExecution> knownDurableOpenedExecution(
      BookOpeningOutcome.Opened opened, AttestationGenesisPreparation preparation) {
    try {
      return ContractDecision.accepted(
          new CompletedOpenBookExecution(openedBookResultFactory.create(opened, preparation)));
    } catch (RuntimeException responseFailure) {
      return ContractDecision.accepted(
          new CompletionUncertainOpenBookExecution(
              reconciliationOpenedBook(opened, preparation), responseFailure));
    }
  }

  static OpenBookResult.Opened publishedOpenedBook(
      BookOpeningOutcome.Opened opened, AttestationGenesisPreparation preparation) {
    return new OpenBookResult.Opened(
        opened.initializedAt(),
        opened.bookIdentity(),
        opened.attestationTrustRoot(),
        opened.attestationCommit(),
        preparation.publishedFounderKeyArtifacts());
  }

  private static OpenBookResult.Opened reconciliationOpenedBook(
      BookOpeningOutcome.Opened opened, AttestationGenesisPreparation preparation) {
    return publishedOpenedBook(opened, preparation);
  }

  private static ContractDecision<OpenBookResult> publishedExecution(
      OpenBookExecution execution, CliOpenBookSessionCloseFailureMapper closeFailureMapper) {
    return switch (Objects.requireNonNull(execution, "execution")) {
      case CompletedOpenBookExecution completed -> ContractDecision.accepted(completed.result());
      case CompletionUncertainOpenBookExecution uncertain ->
          closeFailureMapper.acceptedSessionCloseFailure(
              uncertain.opened(), uncertain.completionFailure());
    };
  }

  static ContractDecision<OpenBookExecution> sessionCloseFailureExecution(
      OpenBookExecution execution,
      CliOpenBookSessionCloseFailureMapper closeFailureMapper,
      RuntimeException closeFailure) {
    return switch (Objects.requireNonNull(execution, "execution")) {
      case CompletedOpenBookExecution completed ->
          executionAfterCloseFailure(
              closeFailureMapper.acceptedSessionCloseFailure(completed.result(), closeFailure));
      case CompletionUncertainOpenBookExecution uncertain -> {
        uncertain.completionFailure().addSuppressed(closeFailure);
        yield ContractDecision.accepted(uncertain);
      }
    };
  }

  /** Preserves an accepted opening result if a close-failure mapper can establish one. */
  static ContractDecision<OpenBookExecution> executionAfterCloseFailure(
      ContractDecision<OpenBookResult> decision) {
    return Objects.requireNonNull(decision, "decision")
        .fold(
            result -> ContractDecision.accepted(new CompletedOpenBookExecution(result)),
            ContractDecision::rejected);
  }

  static ContractDecision<OpenBookExecution> rejectedExecution(
      ContractDecision<OpenBookResult> decision) {
    return ContractDecision.rejected(
        Objects.requireNonNull(decision, "decision").requireRejected());
  }
}

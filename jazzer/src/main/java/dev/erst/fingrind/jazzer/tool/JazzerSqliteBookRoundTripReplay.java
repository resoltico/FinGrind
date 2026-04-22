package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.sqlite.SqliteFuzzAssertions;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Replays posting-command workflows against the SQLite-backed round-trip harness. */
final class JazzerSqliteBookRoundTripReplay {
  private JazzerSqliteBookRoundTripReplay() {}

  static ReplayOutcome replay(byte[] input) {
    PostEntryCommand command = null;
    String uninitializedCommitStatus = "NOT_RUN";
    String undeclaredCommitStatus = "NOT_RUN";
    String inactiveCommitStatus = "NOT_RUN";
    String finalCommitStatus = "NOT_RUN";
    String reloadStatus = "NOT_RUN";
    String duplicateStatus = "NOT_RUN";
    boolean storedFactPresent = false;
    try {
      command = CliFuzzFixtures.readPostEntryCommand(input);
      try (JazzerReplayScratchDirectory scratchDirectory =
              JazzerReplayScratchDirectory.create("fingrind-jazzer-replay-")) {
        Path bookPath = scratchDirectory.resolve(Path.of("nested", "entity-book.sqlite"));

        try (SqlitePostingFactStore postingFactStore = SqliteFuzzAssertions.openStore(bookPath)) {
          BookAdministrationService administrationService =
              CliFuzzFixtures.administrationService(postingFactStore.administrationSession());
          PostingApplicationService applicationService =
              new PostingApplicationService(
                  postingFactStore.postingSession(),
                  CliFuzzFixtures.postingIdGenerator(input),
                  CliFuzzFixtures.fixedClock());

          uninitializedCommitStatus =
              JazzerReplayDetailsMapper.rejectionStatus(
                  JazzerReplayDetailsMapper
                      .requiredCommitRejected(applicationService.commit(command))
                      .rejection());

          CliFuzzFixtures.openBook(administrationService);

          undeclaredCommitStatus =
              JazzerReplayDetailsMapper.rejectionStatus(
                  JazzerReplayDetailsMapper
                      .requiredCommitRejected(applicationService.commit(command))
                      .rejection());

          List<DeclaredAccount> declaredAccounts =
              CliFuzzFixtures.declarePostingAccounts(administrationService, command);
          if (CliFuzzFixtures.listAccounts(postingFactStore.readSession()).size()
              != declaredAccounts.size()) {
            throw new IllegalStateException(
                "Declared-account listing drifted from setup declarations.");
          }
          DeclaredAccount primaryAccount = declaredAccounts.getFirst();
          SqliteFuzzAssertions.deactivateAccount(bookPath, primaryAccount.accountCode().value());
          inactiveCommitStatus =
              JazzerReplayDetailsMapper.rejectionStatus(
                  JazzerReplayDetailsMapper
                      .requiredCommitRejected(applicationService.commit(command))
                      .rejection());

          CliFuzzFixtures.reactivateAccount(administrationService, primaryAccount);

          CommitEntryResult committedResult = applicationService.commit(command);
          if (committedResult instanceof Committed committed) {
            finalCommitStatus = "COMMITTED";
            try (SqlitePostingFactStore reloadedStore = SqliteFuzzAssertions.openStore(bookPath)) {
              Optional<PostingFact> storedPosting =
                  reloadedStore.findExistingPosting(command.requestProvenance().idempotencyKey());
              if (storedPosting.isEmpty()) {
                throw new IllegalStateException(
                    "Committed posting fact was not persisted to SQLite.");
              }
              PostingFact postingFact = storedPosting.orElseThrow();
              if (!postingFact.postingId().equals(committed.postingId())) {
                throw new IllegalStateException(
                    "Reloaded posting id differs from the commit result.");
              }
              if (!postingFact.journalEntry().equals(command.journalEntry())) {
                throw new IllegalStateException(
                    "Reloaded journal entry differs from the parsed command.");
              }
              if (!postingFact.reversalReference().equals(command.reversalReference())) {
                throw new IllegalStateException(
                    "Reloaded reversal differs from the parsed command.");
              }
              if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
                throw new IllegalStateException(
                    "Reloaded request provenance differs from the parsed command.");
              }
              if (!postingFact
                  .provenance()
                  .recordedAt()
                  .equals(CliFuzzFixtures.fixedClock().instant())) {
                throw new IllegalStateException(
                    "Reloaded recorded-at differs from the deterministic clock.");
              }
              if (postingFact.provenance().sourceChannel() != command.sourceChannel()) {
                throw new IllegalStateException(
                    "Reloaded source channel differs from the parsed command.");
              }
              storedFactPresent = true;
              reloadStatus = "RELOADED";

              PostingApplicationService duplicateService =
                  new PostingApplicationService(
                      reloadedStore.postingSession(),
                      CliFuzzFixtures.postingIdGenerator(input),
                      CliFuzzFixtures.fixedClock());
              CommitEntryResult duplicateResult = duplicateService.commit(command);
              if (!(duplicateResult instanceof CommitRejected rejected)) {
                throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
              }
              if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
                throw new IllegalStateException(
                    "Duplicate SQLite commit returned the wrong rejection code.");
              }
              duplicateStatus = JazzerReplayDetailsMapper.rejectionStatus(rejected.rejection());
            }
          } else if (committedResult instanceof CommitRejected rejected) {
            finalCommitStatus = JazzerReplayDetailsMapper.rejectionStatus(rejected.rejection());
            CommitEntryResult repeatedResult = applicationService.commit(command);
            if (!(repeatedResult instanceof CommitRejected repeatedRejected)) {
              throw new IllegalStateException("Rejected SQLite command should remain rejected.");
            }
            if (!repeatedRejected.rejection().equals(rejected.rejection())) {
              throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
            }
            duplicateStatus =
                JazzerReplayDetailsMapper.rejectionStatus(repeatedRejected.rejection());
          } else {
            throw new IllegalStateException("Unexpected SQLite commit result type.");
          }
        }
      }

      return new ReplayOutcome.Success(
          JazzerHarness.sqliteBookRoundTrip().key(),
          JazzerReplayDetailsMapper.sqliteBookRoundTripDetails(
              command,
              "PARSED",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsMapper.NONE));
    } catch (IllegalArgumentException expected) {
      return new ReplayOutcome.ExpectedInvalid(
          JazzerHarness.sqliteBookRoundTrip().key(),
          expected.getClass().getSimpleName(),
          JazzerReplayDetailsMapper.normalizedMessage(expected),
          JazzerReplayDetailsMapper.sqliteBookRoundTripDetails(
              command,
              "INVALID_REQUEST",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsMapper.normalizedMessage(expected)));
    } catch (IOException unexpected) {
      return JazzerReplayDetailsMapper.unexpectedFailure(
          JazzerHarness.sqliteBookRoundTrip(),
          unexpected,
          JazzerReplayDetailsMapper.sqliteBookRoundTripDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsMapper.normalizedMessage(unexpected)));
    } catch (RuntimeException unexpected) {
      return JazzerReplayDetailsMapper.unexpectedFailure(
          JazzerHarness.sqliteBookRoundTrip(),
          unexpected,
          JazzerReplayDetailsMapper.sqliteBookRoundTripDetails(
              command,
              command == null ? "UNEXPECTED_FAILURE" : "PARSED",
              uninitializedCommitStatus,
              undeclaredCommitStatus,
              inactiveCommitStatus,
              finalCommitStatus,
              reloadStatus,
              duplicateStatus,
              storedFactPresent,
              JazzerReplayDetailsMapper.normalizedMessage(unexpected)));
    }
  }
}

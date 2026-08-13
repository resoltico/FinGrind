package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Verifies end-to-end durable, recoverable publication for every journal-owned secret member. */
class PublicationTransactionPublisherTest {
  private static final Instant NOW = Instant.parse("2026-08-10T12:34:56Z");

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void publishesOneNoReplaceMemberAndCleansItsStage(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("report.pdf");

    PublicationTransactionResult result =
        publication
            .publisher()
            .publish(request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf"));
    PublicationTransactionJournal journal = publication.repository().read(result.transactionId());

    assertTrue(result.successful());
    assertEquals(PublicationTransactionState.COMPLETE, journal.state());
    assertEquals(
        PublicationTransactionMemberProgress.CLEANED, journal.members().getFirst().progress());
    assertEquals("pdf", Files.readString(finalPath));
    assertTrue(Files.notExists(journal.members().getFirst().stagePath()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void publishesEveryMemberInOnePairBeforeReportingSuccess(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path book = publication.outputDirectory().resolve("book.fgb");
    Path key = publication.outputDirectory().resolve("book.key");
    PublicationTransactionRequest request =
        new PublicationTransactionRequest(
            List.of(
                member(
                    "protected-book",
                    PublicationTransactionMemberRole.PROTECTED_BOOK,
                    book,
                    "book"),
                member(
                    "encrypted-book-key",
                    PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                    key,
                    "key")));

    PublicationTransactionResult result = publication.publisher().publish(request);
    PublicationTransactionJournal journal = publication.repository().read(result.transactionId());

    assertTrue(result.successful());
    assertEquals("book", Files.readString(book));
    assertEquals("key", Files.readString(key));
    assertTrue(
        journal.members().stream()
            .allMatch(member -> member.progress() == PublicationTransactionMemberProgress.CLEANED));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recoversACompletedPairWithOnlyItsAuthenticatedFinalArtifacts(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path book = publication.outputDirectory().resolve("book.fgb");
    Path key = publication.outputDirectory().resolve("book.key");
    PublicationTransactionResult published =
        publication
            .publisher()
            .publish(
                new PublicationTransactionRequest(
                    List.of(
                        member(
                            "protected-book",
                            PublicationTransactionMemberRole.PROTECTED_BOOK,
                            book,
                            "book"),
                        member(
                            "encrypted-book-key",
                            PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                            key,
                            "key"))));

    PublicationTransactionRecoveryReceipt receipt =
        publication.publisher().recoverWithReceipt(published.transactionId());
    PublicationTransactionJournal journal =
        publication.repository().read(published.transactionId());

    assertEquals(published, receipt.transactionResult());
    assertEquals(
        List.of(
            new PublicationTransactionMemberArtifact(
                "protected-book",
                PublicationTransactionMemberRole.PROTECTED_BOOK,
                new PublicationTransactionArtifact(book, published)),
            new PublicationTransactionMemberArtifact(
                "encrypted-book-key",
                PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                new PublicationTransactionArtifact(key, published))),
        receipt.publishedArtifacts());
    assertFalse(receipt.toString().contains(journal.members().getFirst().stagePath().toString()));
    assertFalse(receipt.toString().contains(journal.members().getLast().stagePath().toString()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void publishesAProducerWrittenPairOnlyThroughItsReservedTransactionStages(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path book = publication.outputDirectory().resolve("book.fgb");
    Path key = publication.outputDirectory().resolve("book.key");
    PublicationTransactionStageReservation reservation =
        publication
            .publisher()
            .reserveStages(
                new PublicationTransactionRequest(
                    List.of(
                        PublicationTransactionMemberRequest.reserveStage(
                            "protected-book",
                            PublicationTransactionMemberRole.PROTECTED_BOOK,
                            book,
                            PublicationMode.NO_REPLACE_LINK),
                        PublicationTransactionMemberRequest.reserveStage(
                            "encrypted-book-key",
                            PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                            key,
                            PublicationMode.NO_REPLACE_LINK))));

    assertThrows(IllegalArgumentException.class, () -> reservation.stagePath("missing-member"));

    writePrivateFile(reservation.stagePath("protected-book"), "book");
    writePrivateFile(reservation.stagePath("encrypted-book-key"), "key");
    PublicationTransactionResult result =
        publication.publisher().publishReservedStages(reservation);
    PublicationTransactionJournal journal = publication.repository().read(result.transactionId());

    assertTrue(result.successful());
    assertEquals("book", Files.readString(book));
    assertEquals("key", Files.readString(key));
    assertTrue(
        journal.members().stream()
            .allMatch(member -> member.progress() == PublicationTransactionMemberProgress.CLEANED));
    assertTrue(journal.members().stream().allMatch(member -> Files.notExists(member.stagePath())));
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionStager.admitReservedStages(journal, publication.runtime()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void blocksAnInterruptedProducerReservationWithoutPublishingItsUnauthenticatedStage(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("report.pdf");
    PublicationTransactionStageReservation reservation =
        publication
            .publisher()
            .reserveStages(
                new PublicationTransactionRequest(
                    List.of(
                        PublicationTransactionMemberRequest.reserveStage(
                            "pdf-report",
                            PublicationTransactionMemberRole.PDF_REPORT,
                            finalPath,
                            PublicationMode.NO_REPLACE_LINK))));
    Path stagePath = reservation.stagePath("pdf-report");
    writePrivateFile(stagePath, "incomplete-producer-output");

    PublicationTransactionResult recovered =
        publication.publisher().recover(reservation.transactionId());

    assertEquals(PublicationTransactionState.BLOCKED, recovered.state());
    assertEquals(PublicationCommitOutcome.NONE_COMMITTED, recovered.outcome().commit());
    assertEquals(PublicationCleanupOutcome.INCOMPLETE, recovered.outcome().cleanup());
    assertFalse(Files.exists(finalPath));
    assertTrue(Files.exists(stagePath));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void reportsNoFinalArtifactForAnIncompleteRecoveredProducerReservation(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("report.pdf");
    PublicationTransactionStageReservation reservation =
        publication.publisher().reserveStages(reservedRequest(finalPath));
    writePrivateFile(reservation.stagePath("pdf-report"), "unadmitted-producer-output");

    PublicationTransactionRecoveryReceipt receipt =
        publication.publisher().recoverWithReceipt(reservation.transactionId());

    assertEquals(PublicationTransactionState.BLOCKED, receipt.transactionResult().state());
    assertTrue(receipt.publishedArtifacts().isEmpty());
    assertFalse(Files.exists(finalPath));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void rejectsInlineInputsFromTheProducerReservationBoundary(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            publication
                .publisher()
                .reserveStages(
                    request(
                        "pdf-report",
                        PublicationMode.NO_REPLACE_LINK,
                        publication.outputDirectory().resolve("report.pdf"),
                        "inline")));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void preservesInjectedReservationsAndRecordsOrdinaryReservationFailures(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication interrupted =
        publication(
            temporaryDirectory.resolve("interrupted"),
            point -> {
              if (point == PublicationTransactionFaultPoint.JOURNAL_PREPARED) {
                throw new PublicationTransactionInjectedFault(point);
              }
            });
    Path interruptedFinal = interrupted.outputDirectory().resolve("report.pdf");

    assertThrows(
        PublicationTransactionInjectedFault.class,
        () -> interrupted.publisher().reserveStages(reservedRequest(interruptedFinal)));
    assertEquals(
        PublicationTransactionState.PREPARED,
        interrupted.repository().read(onlyTransactionId(interrupted.repository())).state());

    AtomicBoolean failed = new AtomicBoolean();
    TestPublication ordinaryFailure =
        publication(
            temporaryDirectory.resolve("ordinary-failure"),
            point -> {
              if (point == PublicationTransactionFaultPoint.JOURNAL_PREPARED
                  && failed.compareAndSet(false, true)) {
                throw new IOException("ordinary reservation failure");
              }
            });

    PublicationTransactionExecutionException recorded =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                ordinaryFailure
                    .publisher()
                    .reserveStages(
                        reservedRequest(ordinaryFailure.outputDirectory().resolve("report.pdf"))));

    assertEquals(PublicationTransactionState.BLOCKED, recorded.result().state());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void publishesIndependentDirectoriesConcurrentlyWithoutLeasingTheSharedJournalStore(
      @TempDir Path temporaryDirectory) throws Exception {
    CountDownLatch firstTransactionPrepared = new CountDownLatch(1);
    CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
    AtomicBoolean holdFirstTransaction = new AtomicBoolean(true);
    TestPublication publication =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.JOURNAL_PREPARED
                  && holdFirstTransaction.compareAndSet(true, false)) {
                firstTransactionPrepared.countDown();
                awaitRelease(releaseFirstTransaction);
              }
            });
    Path firstDirectory = privateDirectory(temporaryDirectory.resolve("first-output"));
    Path secondDirectory = privateDirectory(temporaryDirectory.resolve("second-output"));
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<PublicationTransactionResult> first =
          executor.submit(
              () ->
                  publication
                      .publisher()
                      .publish(
                          request(
                              "first-report",
                              PublicationMode.NO_REPLACE_LINK,
                              firstDirectory.resolve("first.pdf"),
                              "first")));
      firstTransactionPrepared.await();
      Future<PublicationTransactionResult> second =
          executor.submit(
              () ->
                  publication
                      .publisher()
                      .publish(
                          request(
                              "second-report",
                              PublicationMode.NO_REPLACE_LINK,
                              secondDirectory.resolve("second.pdf"),
                              "second")));

      assertTrue(second.get().successful());
      releaseFirstTransaction.countDown();
      assertTrue(first.get().successful());
    } finally {
      releaseFirstTransaction.countDown();
    }
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void atomicallyReplacesOneFinalWithoutLeavingAStage(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("replaceable.fg");
    writePrivateFile(finalPath, "old");

    PublicationTransactionResult result =
        publication
            .publisher()
            .publish(request("pdf-report", PublicationMode.REPLACE, finalPath, "new"));
    PublicationTransactionJournal journal = publication.repository().read(result.transactionId());

    assertTrue(result.successful());
    assertEquals("new", Files.readString(finalPath));
    assertTrue(Files.notExists(journal.members().getFirst().stagePath()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void publishesASelectedReplacementMemberWhenItsTargetWasAbsentAtReservation(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("initially-absent.fg");

    PublicationTransactionResult result =
        publication
            .publisher()
            .publish(request("pdf-report", PublicationMode.REPLACE, finalPath, "new"));
    PublicationTransactionJournal journal = publication.repository().read(result.transactionId());

    assertTrue(result.successful());
    assertEquals("new", Files.readString(finalPath));
    assertTrue(journal.members().getFirst().replacementTarget().isEmpty());
    assertTrue(Files.notExists(journal.members().getFirst().stagePath()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void blocksAReplacementWhenItsSelectedTargetChangesBeforeTheAtomicMove(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("replaceable.fg");
    writePrivateFile(finalPath, "selected-target");
    PublicationTransactionStageReservation reservation =
        publication
            .publisher()
            .reserveStages(
                new PublicationTransactionRequest(
                    List.of(
                        PublicationTransactionMemberRequest.reserveStage(
                            "pdf-report",
                            PublicationTransactionMemberRole.PDF_REPORT,
                            finalPath,
                            PublicationMode.REPLACE))));
    writePrivateFile(reservation.stagePath("pdf-report"), "replacement");
    Files.delete(finalPath);
    writePrivateFile(finalPath, "substituted-target");

    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () -> publication.publisher().publishReservedStages(reservation));
    PublicationTransactionJournal journal =
        publication.repository().read(reservation.transactionId());

    assertEquals(PublicationTransactionState.BLOCKED, exception.result().state());
    assertEquals("substituted-target", Files.readString(finalPath));
    assertEquals(
        CryptographicPrimitives.sha256Hex("selected-target".getBytes(StandardCharsets.UTF_8)),
        journal.members().getFirst().replacementTarget().orElseThrow().sha256Hex(),
        "The authenticated preimage must be retained in the journal rather than overwritten.");
    assertTrue(Files.exists(reservation.stagePath("pdf-report")));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recoversOrSafelyBlocksEveryInjectedDurabilityInterruption(@TempDir Path temporaryDirectory)
      throws Exception {
    for (PublicationTransactionFaultPoint point : PublicationTransactionFaultPoint.values()) {
      if (point == PublicationTransactionFaultPoint.MEMBER_ABORTED
          || point == PublicationTransactionFaultPoint.JOURNAL_BLOCKED) {
        continue;
      }
      Path caseDirectory =
          privateDirectory(temporaryDirectory.resolve(point.name().toLowerCase(Locale.ROOT)));
      TestPublication interrupted =
          publication(
              caseDirectory,
              observed -> {
                if (observed == point) {
                  throw new PublicationTransactionInjectedFault(point);
                }
              });
      Path finalPath = interrupted.outputDirectory().resolve("report.pdf");

      assertThrows(
          PublicationTransactionInjectedFault.class,
          () ->
              interrupted
                  .publisher()
                  .publish(
                      request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf")));
      PublicationTransactionId transactionId = onlyTransactionId(interrupted.repository());
      PublicationTransactionResult recovered =
          publication(caseDirectory, PublicationTransactionFaultInjector.NONE)
              .publisher()
              .recover(transactionId);

      boolean stagedBeforeMemberEvidence =
          point == PublicationTransactionFaultPoint.JOURNAL_PREPARED
              || point == PublicationTransactionFaultPoint.STAGE_DIRECTORY_FORCED;
      assertEquals(!stagedBeforeMemberEvidence, recovered.successful(), point.name());
      if (stagedBeforeMemberEvidence) {
        assertEquals(PublicationTransactionState.BLOCKED, recovered.state(), point.name());
        assertFalse(Files.exists(finalPath), point.name());
      } else {
        assertEquals("pdf", Files.readString(finalPath), point.name());
      }
    }
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recoversEveryInterruptedKnownCollisionAbortWithoutRetainingAStage(
      @TempDir Path temporaryDirectory) throws Exception {
    for (PublicationTransactionFaultPoint point :
        List.of(
            PublicationTransactionFaultPoint.JOURNAL_CLEANING,
            PublicationTransactionFaultPoint.STAGE_UNLINKED,
            PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED,
            PublicationTransactionFaultPoint.MEMBER_ABORTED,
            PublicationTransactionFaultPoint.JOURNAL_BLOCKED)) {
      Path caseDirectory =
          privateDirectory(
              temporaryDirectory.resolve("collision-" + point.name().toLowerCase(Locale.ROOT)));
      TestPublication interrupted =
          publication(
              caseDirectory,
              observed -> {
                if (observed == point) {
                  throw new PublicationTransactionInjectedFault(point);
                }
              });
      Path finalPath = interrupted.outputDirectory().resolve("occupied.pdf");
      writePrivateFile(finalPath, "unrelated");

      assertThrows(
          PublicationTransactionInjectedFault.class,
          () ->
              interrupted
                  .publisher()
                  .publish(
                      request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf")));
      PublicationTransactionJournal beforeRecovery =
          interrupted.repository().read(onlyTransactionId(interrupted.repository()));
      PublicationTransactionResult recovered =
          publication(caseDirectory, PublicationTransactionFaultInjector.NONE)
              .publisher()
              .recover(beforeRecovery.transactionId());
      PublicationTransactionJournal afterRecovery =
          interrupted.repository().read(beforeRecovery.transactionId());

      assertEquals(PublicationTransactionState.BLOCKED, recovered.state(), point.name());
      assertEquals(
          PublicationCommitOutcome.NONE_COMMITTED, recovered.outcome().commit(), point.name());
      assertEquals(PublicationCleanupOutcome.COMPLETE, recovered.outcome().cleanup(), point.name());
      assertEquals(
          PublicationTransactionMemberProgress.ABORTED,
          afterRecovery.members().getFirst().progress());
      assertTrue(Files.notExists(afterRecovery.members().getFirst().stagePath()), point.name());
      assertEquals("unrelated", Files.readString(finalPath), point.name());
    }
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void safelyAbortsAKnownNoReplaceCollisionWithoutRetainingItsStage(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("occupied.pdf");
    writePrivateFile(finalPath, "unrelated");

    FileAlreadyExistsException exception =
        assertThrows(
            FileAlreadyExistsException.class,
            () ->
                publication
                    .publisher()
                    .publish(
                        request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf")));

    PublicationTransactionJournal journal =
        publication.repository().read(onlyTransactionId(publication.repository()));

    assertEquals(finalPath.toString(), exception.getFile());
    assertNotNull(exception.getMessage());
    String message = Objects.requireNonNull(exception.getMessage());
    assertFalse(message.contains("stage"));
    assertEquals(PublicationTransactionState.BLOCKED, journal.state());
    assertEquals(
        PublicationCommitOutcome.NONE_COMMITTED,
        journal.transitions().getLast().outcome().commit());
    assertEquals(
        PublicationCleanupOutcome.COMPLETE, journal.transitions().getLast().outcome().cleanup());
    assertEquals(
        PublicationTransactionMemberProgress.ABORTED, journal.members().getFirst().progress());
    assertTrue(Files.notExists(journal.members().getFirst().stagePath()));
    assertEquals("unrelated", Files.readString(finalPath));
    assertEquals(
        PublicationTransactionState.BLOCKED,
        publication.publisher().recover(journal.transactionId()).state());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void preservesTheCollisionCauseWhenItsAbortCleanupHasAnOrdinaryFailure(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication publication =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.STAGE_UNLINKED) {
                throw new IOException("injected collision cleanup failure");
              }
            });
    Path finalPath = publication.outputDirectory().resolve("occupied.pdf");
    writePrivateFile(finalPath, "unrelated");

    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                publication
                    .publisher()
                    .publish(
                        request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf")));

    assertEquals(PublicationTransactionState.CLEANUP_UNCERTAIN, exception.result().state());
    Throwable cleanupFailure = Objects.requireNonNull(exception.getCause());
    assertEquals(1, cleanupFailure.getSuppressed().length);
    assertTrue(
        cleanupFailure.getSuppressed()[0]
            instanceof PublicationTransactionFinalTargetOccupiedException);
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recoversAReplacementAfterItsFinalMovePrecedesTheDurabilityInterruption(
      @TempDir Path temporaryDirectory) throws Exception {
    TestPublication interrupted =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.FINAL_DIRECTORY_FORCED) {
                throw new PublicationTransactionInjectedFault(point);
              }
            });
    Path finalPath = interrupted.outputDirectory().resolve("replaceable.fg");
    writePrivateFile(finalPath, "old");

    assertThrows(
        PublicationTransactionInjectedFault.class,
        () ->
            interrupted
                .publisher()
                .publish(request("pdf-report", PublicationMode.REPLACE, finalPath, "new")));
    PublicationTransactionResult recovered =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE)
            .publisher()
            .recover(onlyTransactionId(interrupted.repository()));

    assertTrue(recovered.successful());
    assertEquals("new", Files.readString(finalPath));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recoversAnUncertainCleanupAfterItsOneInjectedFailure(@TempDir Path temporaryDirectory)
      throws Exception {
    AtomicBoolean failed = new AtomicBoolean();
    TestPublication interrupted =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED
                  && failed.compareAndSet(false, true)) {
                throw new IOException("injected cleanup directory failure");
              }
            });
    Path finalPath = interrupted.outputDirectory().resolve("report.pdf");

    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                interrupted
                    .publisher()
                    .publish(
                        request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf")));
    PublicationTransactionResult recovered =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE)
            .publisher()
            .recover(exception.result().transactionId());

    assertEquals(PublicationTransactionState.CLEANUP_UNCERTAIN, exception.result().state());
    assertTrue(recovered.successful());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recordsTheDurableFailureOutcomeForEveryOrdinaryFailurePhase(@TempDir Path temporaryDirectory)
      throws Exception {
    assertOrdinaryFailureOutcome(
        temporaryDirectory.resolve("prepared"),
        PublicationTransactionFaultPoint.JOURNAL_PREPARED,
        PublicationTransactionState.BLOCKED);
    assertOrdinaryFailureOutcome(
        temporaryDirectory.resolve("staged"),
        PublicationTransactionFaultPoint.JOURNAL_STAGED,
        PublicationTransactionState.BLOCKED);
    Path committedDirectory = temporaryDirectory.resolve("committed");
    PublicationTransactionExecutionException committed =
        assertOrdinaryFailureOutcome(
            committedDirectory,
            PublicationTransactionFaultPoint.JOURNAL_COMMITTED,
            PublicationTransactionState.CLEANUP_INCOMPLETE);

    assertTrue(
        publication(committedDirectory, PublicationTransactionFaultInjector.NONE)
            .publisher()
            .recover(committed.result().transactionId())
            .successful());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void preservesTheOriginalSafeHandleWhenFailureRecordingAlsoInterrupts(
      @TempDir Path temporaryDirectory) throws Exception {
    AtomicBoolean stagedFailure = new AtomicBoolean();
    TestPublication publication =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.JOURNAL_STAGED) {
                stagedFailure.set(true);
                throw new IOException("operation failure");
              }
              if (stagedFailure.get()
                  && point == PublicationTransactionFaultPoint.JOURNAL_PREPARED) {
                throw new IOException("failure-recording interruption");
              }
            });

    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                publication
                    .publisher()
                    .publish(
                        request(
                            "pdf-report",
                            PublicationMode.NO_REPLACE_LINK,
                            publication.outputDirectory().resolve("report.pdf"),
                            "pdf")));

    assertEquals(PublicationTransactionState.PREPARED, exception.result().state());
    assertEquals(1, Objects.requireNonNull(exception.getCause()).getSuppressed().length);
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void detectsDirectoryReplacementBeforeAnyPublicationOperation(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("report.pdf");
    PublicationTransactionJournal planned =
        PublicationTransactionPlan.prepare(
            request("pdf-report", PublicationMode.NO_REPLACE_LINK, finalPath, "pdf"),
            publication.runtime());
    Path originalDirectory = publication.outputDirectory();
    Files.move(originalDirectory, originalDirectory.resolveSibling("replaced-output"));
    privateDirectory(originalDirectory);

    assertThrows(
        IOException.class,
        () -> PublicationTransactionPlan.requireCurrentPrivateDirectories(planned));
  }

  @Test
  void requestValuesDefensivelyCopySecretsAndRejectAmbiguousMembers(
      @TempDir Path temporaryDirectory) throws Exception {
    Path finalPath = temporaryDirectory.resolve("report.pdf");
    byte[] bytes = new byte[] {1};
    PublicationTransactionMemberRequest member =
        member("pdf-report", PublicationTransactionMemberRole.PDF_REPORT, finalPath, bytes);
    bytes[0] = 9;
    assertEquals(1, member.secretBytesForStaging()[0]);
    byte[] returned = member.secretBytesForStaging();
    returned[0] = 8;
    assertEquals(1, member.secretBytesForStaging()[0]);
    assertFalse(member.hasPrivateSource());
    assertThrows(IllegalStateException.class, member::privateSourcePathForStaging);
    assertTrue(member.toString().contains("<redacted>"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionRequest(List.of(member, member)));
    assertThrows(
        IllegalArgumentException.class, () -> new PublicationTransactionRequest(List.of()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void publishesAnAdmittedPrivateSourceWithoutJournalingItsPath(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path sourcePath = publication.outputDirectory().resolve("private-source.bin");
    Path finalPath = publication.outputDirectory().resolve("report.pdf");
    writePrivateFile(sourcePath, "source-backed-report");
    PublicationTransactionMemberRequest request =
        PublicationTransactionMemberRequest.fromPrivateSource(
            "pdf-report",
            PublicationTransactionMemberRole.PDF_REPORT,
            finalPath,
            PublicationMode.NO_REPLACE_LINK,
            sourcePath);

    PublicationTransactionResult result =
        publication.publisher().publish(new PublicationTransactionRequest(List.of(request)));

    assertTrue(result.successful());
    assertEquals("source-backed-report", Files.readString(finalPath));
    assertFalse(request.toString().contains(sourcePath.toString()));
    PublicationTransactionJournal journal = publication.repository().read(result.transactionId());
    assertFalse(journal.toString().contains(sourcePath.toString()));
  }

  private static PublicationTransactionExecutionException assertOrdinaryFailureOutcome(
      Path temporaryDirectory,
      PublicationTransactionFaultPoint faultPoint,
      PublicationTransactionState expectedState)
      throws IOException {
    AtomicBoolean failed = new AtomicBoolean();
    TestPublication publication =
        publication(
            temporaryDirectory,
            point -> {
              if (point == faultPoint && failed.compareAndSet(false, true)) {
                throw new IOException("ordinary failure at " + point);
              }
            });
    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                publication
                    .publisher()
                    .publish(
                        request(
                            "pdf-report",
                            PublicationMode.NO_REPLACE_LINK,
                            publication.outputDirectory().resolve("report.pdf"),
                            "pdf")));

    assertEquals(expectedState, exception.result().state());
    return exception;
  }

  private static void awaitRelease(CountDownLatch release) throws IOException {
    try {
      release.await();
    } catch (InterruptedException interruption) {
      Thread.currentThread().interrupt();
      throw new IOException(
          "Interrupted while holding the publication-transaction test boundary.", interruption);
    }
  }

  static PublicationTransactionRequest request(
      String memberId, PublicationMode mode, Path finalPath, String content) {
    return new PublicationTransactionRequest(
        List.of(
            new PublicationTransactionMemberRequest(
                memberId,
                PublicationTransactionMemberRole.PDF_REPORT,
                finalPath,
                mode,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  private static PublicationTransactionRequest reservedRequest(Path finalPath) {
    return new PublicationTransactionRequest(
        List.of(
            PublicationTransactionMemberRequest.reserveStage(
                "pdf-report",
                PublicationTransactionMemberRole.PDF_REPORT,
                finalPath,
                PublicationMode.NO_REPLACE_LINK)));
  }

  private static PublicationTransactionMemberRequest member(
      String memberId, PublicationTransactionMemberRole role, Path finalPath, String content) {
    return member(
        memberId, role, finalPath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static PublicationTransactionMemberRequest member(
      String memberId, PublicationTransactionMemberRole role, Path finalPath, byte[] content) {
    return new PublicationTransactionMemberRequest(
        memberId, role, finalPath, PublicationMode.NO_REPLACE_LINK, content);
  }

  static TestPublication publication(
      Path temporaryDirectory, PublicationTransactionFaultInjector faultInjector)
      throws IOException {
    Path root = privateDirectory(temporaryDirectory);
    Path outputDirectory = privateDirectory(root.resolve("output"));
    Path storeDirectory = root.resolve("journal-store");
    PublicationTransactionJournalRepository repository =
        PublicationTransactionJournalRepository.open(storeDirectory, ignored -> {});
    PublicationTransactionRuntime runtime =
        new PublicationTransactionRuntime(
            repository, ignored -> {}, InstantSource.fixed(NOW), faultInjector);
    return new TestPublication(
        PublicationTransactionPublisher.open(
            repository, ignored -> {}, InstantSource.fixed(NOW), faultInjector),
        repository,
        outputDirectory,
        runtime);
  }

  private static PublicationTransactionId onlyTransactionId(
      PublicationTransactionJournalRepository repository) throws IOException {
    try (Stream<Path> paths = Files.list(repository.storeRoot())) {
      Path journal =
          paths
              .filter(path -> path.getFileName().toString().startsWith("txn-"))
              .findFirst()
              .orElseThrow();
      String name = journal.getFileName().toString();
      return new PublicationTransactionId(name.substring(4, name.length() - 5));
    }
  }

  private static Path privateDirectory(Path directory) throws IOException {
    Set<PosixFilePermission> ownerOnly =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    Files.createDirectories(directory);
    Files.setPosixFilePermissions(directory, ownerOnly);
    return directory;
  }

  static void writePrivateFile(Path path, String content) throws IOException {
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(path)) {
      PublicationTransactionArtifactChannels.writeExactly(
          opened, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      opened.force();
    }
  }

  record TestPublication(
      PublicationTransactionPublisher publisher,
      PublicationTransactionJournalRepository repository,
      Path outputDirectory,
      PublicationTransactionRuntime runtime) {}
}

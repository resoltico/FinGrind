package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
  void recoversOrSafelyBlocksEveryInjectedDurabilityInterruption(@TempDir Path temporaryDirectory)
      throws Exception {
    for (PublicationTransactionFaultPoint point : PublicationTransactionFaultPoint.values()) {
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
  void recordsAnUncertainCommitWithoutExposingItsStageForRetry(@TempDir Path temporaryDirectory)
      throws Exception {
    TestPublication publication =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
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

    assertEquals(PublicationTransactionState.COMMIT_UNCERTAIN, exception.result().state());
    assertFalse(exception.result().successful());
    assertNotNull(exception.getCause());
    assertNotNull(exception.getMessage());
    String message = Objects.requireNonNull(exception.getMessage());
    assertFalse(message.contains("stage"));
    assertEquals(
        PublicationTransactionState.COMMIT_UNCERTAIN,
        assertThrows(
                PublicationTransactionExecutionException.class,
                () -> publication.publisher().recover(exception.result().transactionId()))
            .result()
            .state());
    assertThrows(
        IOException.class,
        () ->
            new PublicationTransactionRunner(publication.runtime())
                .recover(publication.repository().read(exception.result().transactionId())));
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
    assertEquals(1, member.secretBytes()[0]);
    byte[] returned = member.secretBytes();
    returned[0] = 8;
    assertEquals(1, member.secretBytes()[0]);
    assertTrue(member.toString().contains("<redacted>"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionRequest(List.of(member, member)));
    assertThrows(
        IllegalArgumentException.class, () -> new PublicationTransactionRequest(List.of()));
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

  private static PublicationTransactionRequest request(
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

  private static TestPublication publication(
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

  private static void writePrivateFile(Path path, String content) throws IOException {
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(path)) {
      PublicationTransactionArtifactFiles.writeExactly(
          opened, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      opened.force();
    }
  }

  private record TestPublication(
      PublicationTransactionPublisher publisher,
      PublicationTransactionJournalRepository repository,
      Path outputDirectory,
      PublicationTransactionRuntime runtime) {}
}

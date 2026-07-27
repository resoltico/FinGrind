package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Direct boundaries for final pair-member publication primitives. */
class SqliteProtectedBookPublicationSupportTest extends SqliteNativeBridgeTestSupport {
  @Test
  void replacementBridgeRetainsTheStageAndRejectsAnExhaustedFreshNameBudget() throws Exception {
    Path stagedPath = Files.writeString(tempDirectory.resolve("book.stage"), "staged book");
    Path finalPath = tempDirectory.resolve("book.sqlite");
    AtomicInteger attempts = new AtomicInteger();

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                SqliteProtectedBookPublicationSupport.createReplacementBridgeRetainingStage(
                    stagedPath,
                    finalPath,
                    (bridge, ignoredStage) -> {
                      attempts.incrementAndGet();
                      throw new FileAlreadyExistsException(bridge.toString());
                    }));

    assertEquals(8, attempts.get());
    assertTrue(Files.exists(stagedPath));
    assertTrue(
        Objects.requireNonNull(failure.getMessage())
            .contains("Unable to reserve a unique retained-stage replacement bridge"));
  }

  @Test
  void publicationObserversMarkTheExactFinalPrimitiveBoundaryForBothMemberKinds() throws Exception {
    Path stagedPath = Files.writeString(tempDirectory.resolve("book.stage"), "staged book");
    Path finalPath = tempDirectory.resolve("book.sqlite");
    AtomicInteger linkAttempts = new AtomicInteger();
    AtomicInteger moveAttempts = new AtomicInteger();

    SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator linkCreator =
        (publishedPath, observedStage) -> {
          assertEquals(finalPath, publishedPath);
          assertEquals(stagedPath, observedStage);
        };
    SqliteProtectedBookPublicationSupport.AtomicBookMover mover =
        (observedStage, publishedPath) -> {
          assertEquals(stagedPath, observedStage);
          assertEquals(finalPath, publishedPath);
        };

    SqliteProtectedBookPublicationSupport.observingAttempt(
            linkCreator, () -> linkAttempts.incrementAndGet())
        .create(finalPath, stagedPath);
    SqliteProtectedBookPublicationSupport.observingAttempt(
            mover, () -> moveAttempts.incrementAndGet())
        .move(stagedPath, finalPath);

    assertEquals(1, linkAttempts.get());
    assertEquals(1, moveAttempts.get());
  }

  @Test
  void defaultNoReplacePublicationCreatesTheFinalLinkWithoutConsumingTheStage() throws Exception {
    Path stagedPath = Files.writeString(tempDirectory.resolve("secret.stage"), "generated secret");
    Path finalPath = tempDirectory.resolve("book.key");

    SqliteProtectedBookPublicationSupport.publishRetainingStage(stagedPath, finalPath);

    assertTrue(Files.isSameFile(stagedPath, finalPath));
    assertTrue(Files.exists(stagedPath));
  }

  @Test
  void guardedLinkCreatorReportsTheRejectedMemberWithoutMarkingAnAttempt() {
    IOException guardFailure = new IOException("witness changed");
    AtomicInteger attempts = new AtomicInteger();

    SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuardRejectedException failure =
        assertThrows(
            SqliteProtectedBookPublicationSupport.FinalMemberPublicationGuardRejectedException
                .class,
            () ->
                SqliteProtectedBookPublicationSupport.guardedLinkCreator(
                        SqliteProtectedBookPublicationSupport.FinalMember.SECRET,
                        () -> {
                          throw guardFailure;
                        },
                        () -> attempts.incrementAndGet(),
                        Files::createLink)
                    .create(
                        tempDirectory.resolve("book.key"), tempDirectory.resolve("secret.stage")));

    assertEquals(SqliteProtectedBookPublicationSupport.FinalMember.SECRET, failure.member());
    assertSame(guardFailure, failure.getCause());
    assertEquals(0, attempts.get());
  }
}

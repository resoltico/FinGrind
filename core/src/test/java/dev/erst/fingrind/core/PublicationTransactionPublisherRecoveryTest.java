package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.PublicationTransactionPublisherTest.publication;
import static dev.erst.fingrind.core.PublicationTransactionPublisherTest.request;
import static dev.erst.fingrind.core.PublicationTransactionPublisherTest.writePrivateFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Verifies recovery after replacement publication has lost its transaction-owned stage. */
class PublicationTransactionPublisherRecoveryTest {
  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recoversAnUncertainReplacementCleanupAfterAnEquivalentFinalIsRehomed(
      @TempDir Path temporaryDirectory) throws Exception {
    AtomicBoolean failed = new AtomicBoolean();
    PublicationTransactionPublisherTest.TestPublication interrupted =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED
                  && failed.compareAndSet(false, true)) {
                throw new IOException("injected cleanup directory failure");
              }
            });
    Path finalPath = interrupted.outputDirectory().resolve("rekeyed-book.fg");
    writePrivateFile(finalPath, "before-rekey");

    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                interrupted
                    .publisher()
                    .publish(
                        request(
                            "protected-book", PublicationMode.REPLACE, finalPath, "after-rekey")));

    Path equivalentReplacement = finalPath.resolveSibling("equivalent-replacement.fg");
    writePrivateFile(equivalentReplacement, "after-rekey");
    Files.move(
        equivalentReplacement,
        finalPath,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    PublicationTransactionResult recovered =
        publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE)
            .publisher()
            .recover(exception.result().transactionId());

    assertEquals(PublicationTransactionState.CLEANUP_UNCERTAIN, exception.result().state());
    assertTrue(recovered.successful());
    assertEquals("after-rekey", Files.readString(finalPath));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void refusesAnUncertainReplacementCleanupAfterItsFinalContentChanges(
      @TempDir Path temporaryDirectory) throws Exception {
    AtomicBoolean failed = new AtomicBoolean();
    PublicationTransactionPublisherTest.TestPublication interrupted =
        publication(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.CLEANUP_DIRECTORY_FORCED
                  && failed.compareAndSet(false, true)) {
                throw new IOException("injected cleanup directory failure");
              }
            });
    Path finalPath = interrupted.outputDirectory().resolve("rekeyed-book.fg");
    writePrivateFile(finalPath, "before-rekey");

    PublicationTransactionExecutionException interruptedException =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                interrupted
                    .publisher()
                    .publish(
                        request(
                            "protected-book", PublicationMode.REPLACE, finalPath, "after-rekey")));

    Path changedReplacement = finalPath.resolveSibling("changed-replacement.fg");
    writePrivateFile(changedReplacement, "tampered-after-rekey");
    Files.move(
        changedReplacement,
        finalPath,
        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    PublicationTransactionExecutionException recoveryException =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                publication(temporaryDirectory, PublicationTransactionFaultInjector.NONE)
                    .publisher()
                    .recover(interruptedException.result().transactionId()));

    assertEquals(PublicationTransactionState.CLEANUP_UNCERTAIN, recoveryException.result().state());
    assertEquals("tampered-after-rekey", Files.readString(finalPath));
  }
}

package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Verifies owner-context lookup selects only its one authenticated transaction. */
class PublicationTransactionOwnerContextRecoveryTest {

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void findsAndRecoversOnlyTheJournalAuthenticatedForItsExactOperationContext(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionPublisherTest.TestPublication publication =
        PublicationTransactionPublisherTest.publication(
            temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path finalPath = publication.outputDirectory().resolve("report.pdf");
    PublicationTransactionOwnerContext context =
        PublicationTransactionOwnerContext.fromCanonicalDescription(
            "publication-owner-context-test-v1\\u0000report.pdf");

    PublicationTransactionResult published =
        publication
            .publisher()
            .publish(
                new PublicationTransactionRequest(
                    List.of(
                        new PublicationTransactionMemberRequest(
                            "pdf-report",
                            PublicationTransactionMemberRole.PDF_REPORT,
                            finalPath,
                            PublicationMode.NO_REPLACE_LINK,
                            "pdf".getBytes(StandardCharsets.UTF_8))),
                    Optional.of(context)));
    PublicationTransactionJournal journal =
        publication.repository().read(published.transactionId());
    Optional<PublicationTransactionRecoveryReceipt> recovered =
        publication.publisher().recoverMatchingOwnerContext(context);

    assertEquals(
        Optional.of(published),
        recovered.map(PublicationTransactionRecoveryReceipt::transactionResult));
    assertEquals(Optional.of(context), journal.ownerContext());
    assertTrue(
        publication
            .publisher()
            .recoverMatchingOwnerContext(
                PublicationTransactionOwnerContext.fromCanonicalDescription(
                    "publication-owner-context-test-v1\\u0000other.pdf"))
            .isEmpty());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void rejectsAmbiguousOperationContextInsteadOfSelectingOneJournal(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionPublisherTest.TestPublication publication =
        PublicationTransactionPublisherTest.publication(
            temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    PublicationTransactionOwnerContext context =
        PublicationTransactionOwnerContext.fromCanonicalDescription(
            "publication-owner-context-test-v1\\u0000ambiguous");
    PublicationTransactionRequest first =
        reservedRequest("first", publication.outputDirectory().resolve("first.pdf"), context);
    PublicationTransactionRequest second =
        reservedRequest("second", publication.outputDirectory().resolve("second.pdf"), context);

    publication.publisher().reserveStages(first);
    publication.publisher().reserveStages(second);

    assertThrows(
        IOException.class, () -> publication.publisher().recoverMatchingOwnerContext(context));
  }

  private static PublicationTransactionRequest reservedRequest(
      String memberId, Path finalPath, PublicationTransactionOwnerContext context) {
    return new PublicationTransactionRequest(
        List.of(
            PublicationTransactionMemberRequest.reserveStage(
                memberId,
                PublicationTransactionMemberRole.PDF_REPORT,
                finalPath,
                PublicationMode.NO_REPLACE_LINK)),
        Optional.of(context));
  }
}

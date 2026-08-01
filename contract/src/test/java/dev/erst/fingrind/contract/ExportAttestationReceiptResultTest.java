package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for receipt-export publication facts exposed to callers. */
class ExportAttestationReceiptResultTest {
  private static final UUID BOOK_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final String OPERATION_HEAD =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @TempDir Path temporaryDirectory;

  @Test
  void exported_exposesTheCanonicalReceiptAndMandatoryRetainedPrivateStageFact() {
    Path receiptFile = temporaryDirectory.resolve("receipt.fgar");
    Path residualStage = temporaryDirectory.resolve(".receipt.fgar-stage");
    ArtifactPublicationRetention retainedStage = new ArtifactPublicationRetention(residualStage);
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(receiptFile, retainedStage);
    ExportAttestationReceiptResult.Exported exported =
        new ExportAttestationReceiptResult.Exported(
            publication, BOOK_ID, BigInteger.ZERO, OPERATION_HEAD, List.of());

    assertEquals(publication.publishedArtifactPath(), exported.receiptFilePath());
    assertEquals(publication.retention(), exported.retainedStage());
  }
}

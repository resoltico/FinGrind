package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused coverage for protocol-catalog artifact output declarations. */
class ProtocolCatalogArtifactOutputsTest {
  @Test
  void taxObligationOperation_publishesPdfArtifactSupport() {
    ProtocolOperation operation = ProtocolCatalog.operation(OperationId.TAX_OBLIGATION);

    assertEquals(
        List.of(OutputMode.JSON, OutputMode.TEXT, OutputMode.CSV), operation.outputModes());
    assertEquals(1, operation.artifactOutputs().size());
    assertEquals("pdf", operation.artifactOutputs().getFirst().format());
    assertEquals("--pdf-out <path>", operation.artifactOutputs().getFirst().option());
  }
}

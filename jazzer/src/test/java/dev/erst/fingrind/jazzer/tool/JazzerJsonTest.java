package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins the local Jazzer JSON contract that Jackson 3 still honors the annotation namespace. */
class JazzerJsonTest {
  @TempDir Path tempDirectory;

  @Test
  void replayExpectation_roundTripsPolymorphicReplayDetails() throws IOException {
    ReplayExpectation expectation =
        new ReplayExpectation(
            "EXPECTED_INVALID",
            new CliRequestReplayDetails(
                "INVALID_REQUEST",
                "NOT_PARSED",
                "idem-1",
                2,
                false,
                "AGENT",
                "CLI",
                "Missing required field: provenance"));
    Path jsonPath = tempDirectory.resolve("replay-expectation.json");

    JazzerJson.write(jsonPath, expectation);
    ReplayExpectation reloaded = JazzerJson.read(jsonPath, ReplayExpectation.class);

    assertEquals(expectation, reloaded);
    assertInstanceOf(CliRequestReplayDetails.class, reloaded.details());
  }
}

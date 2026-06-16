package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.JazzerTestProjectRoot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Guards the committed regression floor against malformed raw JSON seed bodies. */
class CommittedRegressionSeedInputsTest {
  private static final Path PROJECT_DIRECTORY = JazzerTestProjectRoot.projectDirectory();
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  @Test
  void committedJsonSeedInputs_are_syntactically_valid_json_documents() throws Exception {
    List<String> invalidInputs = new ArrayList<>();
    List<Path> checkedInputs = new ArrayList<>();

    for (var harness : dev.erst.fingrind.jazzer.support.JazzerHarness.values()) {
      for (Path metadataPath : RegressionSeedPaths.metadataPaths(PROJECT_DIRECTORY, harness)) {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        Path inputPath = metadata.inputPath(PROJECT_DIRECTORY).toAbsolutePath().normalize();
        if (!inputPath.getFileName().toString().endsWith(".json")) {
          continue;
        }
        checkedInputs.add(inputPath);
        try {
          JSON_MAPPER.readTree(inputPath.toFile());
        } catch (Exception exception) {
          invalidInputs.add(
              inputPath
                  + " via "
                  + metadataPath.getFileName()
                  + " -> "
                  + exception.getClass().getSimpleName()
                  + ": "
                  + exception.getMessage());
        }
      }
    }

    assertFalse(checkedInputs.isEmpty(), "Expected committed JSON seed inputs to exist.");
    assertTrue(
        invalidInputs.isEmpty(),
        () -> "Malformed committed JSON seed inputs:\n" + String.join("\n", invalidInputs));
  }

  @Test
  void committedRegressionMetadata_matches_currentReplayExpectations() throws Exception {
    List<String> mismatches = new ArrayList<>();

    for (JazzerHarness harness : JazzerHarness.values()) {
      for (Path metadataPath : RegressionSeedPaths.metadataPaths(PROJECT_DIRECTORY, harness)) {
        RegressionSeedMetadata metadata =
            JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
        ReplayExpectation actualExpectation =
            JazzerReplayRunner.expectationFor(
                JazzerReplayRunner.replay(
                    harness, Files.readAllBytes(metadata.inputPath(PROJECT_DIRECTORY))));
        if (!metadata.expectation().equals(actualExpectation)) {
          mismatches.add(
              metadataPath.getFileName()
                  + " for "
                  + harness.key()
                  + " expected "
                  + JazzerJson.toJson(metadata.expectation())
                  + " but got "
                  + JazzerJson.toJson(actualExpectation));
        }
      }
    }

    assertEquals(
        List.of(),
        mismatches,
        () -> "Committed regression metadata drift:\n" + String.join("\n", mismatches));
  }
}

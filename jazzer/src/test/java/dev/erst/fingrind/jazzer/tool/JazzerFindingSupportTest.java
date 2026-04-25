package dev.erst.fingrind.jazzer.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers raw artifact discovery and replay classification for Jazzer local findings. */
class JazzerFindingSupportTest {
  @TempDir Path projectDirectory;

  @Test
  void findingArtifacts_returnsEmptyForMissingRunDirectory_andRejectsAggregateTargets()
      throws Exception {
    assertEquals(
        List.of(),
        JazzerFindingSupport.findingArtifacts(projectDirectory, JazzerRunTarget.cliRequest()));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerFindingSupport.findingArtifacts(
                    projectDirectory, JazzerRunTarget.regression()));
    assertTrue(String.valueOf(exception.getMessage()).contains("requires a single-harness target"));
  }

  @Test
  void findingArtifacts_sorts_supported_raw_artifacts_and_ignores_non_findings() throws Exception {
    Path runDirectory = projectDirectory.resolve(".local/runs/cli-request");
    Files.createDirectories(runDirectory);
    Files.writeString(runDirectory.resolve("timeout-b"), "{sideevr:0}dee", UTF_8);
    Files.writeString(runDirectory.resolve("crash-a"), "{sideevr:0}dee", UTF_8);
    Files.writeString(runDirectory.resolve("notes.txt"), "not-a-finding", UTF_8);

    List<FindingArtifact> artifacts =
        JazzerFindingSupport.findingArtifacts(projectDirectory, JazzerRunTarget.cliRequest());

    assertEquals(2, artifacts.size());
    assertEquals("crash", artifacts.getFirst().rawArtifactKind());
    assertEquals("crash-a", artifacts.getFirst().rawArtifactName());
    assertEquals("timeout", artifacts.getLast().rawArtifactKind());
    assertEquals(
        ReplayFindingClassification.EXPECTED_INVALID, artifacts.getFirst().replayClassification());
    assertEquals(
        "Failed to read request JSON.",
        JazzerFindingSupport.replayMessage(
            new ReplayOutcome.ExpectedInvalid(
                "cli-request",
                "CliRequestException",
                "Failed to read request JSON.",
                new UnparsedCliRequestReplayDetails())));
    assertEquals(
        ReplayFindingClassification.UNEXPECTED_FAILURE,
        JazzerFindingSupport.replayClassification(
            new ReplayOutcome.UnexpectedFailure(
                "cli-request",
                "IllegalStateException",
                "boom",
                "stack",
                new UnparsedCliRequestReplayDetails())));
    assertThrows(
        NullPointerException.class,
        JazzerFindingSupportTest::invokeReplayMessageReflectivelyWithNull);
  }

  @Test
  void private_helpers_report_unreadable_artifacts_and_preserve_names_without_separators()
      throws Exception {
    IllegalStateException unreadableArtifact =
        assertThrows(
            IllegalStateException.class,
            () ->
                invokePrivate(
                    "findingArtifact",
                    new Class<?>[] {JazzerRunTarget.class, Path.class},
                    JazzerRunTarget.cliRequest(),
                    projectDirectory.resolve("missing-artifact")));
    assertTrue(
        String.valueOf(unreadableArtifact.getMessage())
            .contains("Unable to read raw finding artifact"));

    assertEquals(
        "artifact",
        invokePrivate("rawArtifactKind", new Class<?>[] {Path.class}, Path.of("artifact")));
  }

  private static String invokeReplayMessageReflectivelyWithNull() throws Exception {
    Method method =
        JazzerFindingSupport.class.getDeclaredMethod("replayMessage", ReplayOutcome.class);
    try {
      return (String) method.invoke(null, (Object) null);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }

  private static Object invokePrivate(
      String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
    Method method = JazzerFindingSupport.class.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    try {
      return method.invoke(null, arguments);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception checkedException) {
        throw checkedException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw exception;
    }
  }
}

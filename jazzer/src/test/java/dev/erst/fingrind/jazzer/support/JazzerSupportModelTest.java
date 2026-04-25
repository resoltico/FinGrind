package dev.erst.fingrind.jazzer.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Covers constructor invariants and topology validation for Jazzer support models. */
class JazzerSupportModelTest {
  @Test
  void harnessKindAndHarnessHelpersResolveStablePaths() {
    JazzerHarness harness =
        new JazzerHarness(
            JazzerHarnessKind.CLI_REQUEST, " CLI Request ", "dev.example.CustomHarness", " fuzz ");

    assertEquals(JazzerHarnessKind.CLI_REQUEST, JazzerHarnessKind.fromKey("cli-request"));
    assertEquals(JazzerHarness.cliRequest(), JazzerHarness.fromKind(JazzerHarnessKind.CLI_REQUEST));
    assertEquals("cli-request", harness.key());
    assertEquals("dev/example/CustomHarnessInputs/fuzz", harness.inputResourceDirectory());
    assertEquals(
        Path.of("/tmp/project/src/fuzz/resources/dev/example/CustomHarnessInputs/fuzz"),
        harness.inputDirectory(Path.of("/tmp/project")));
    assertEquals(
        Path.of(
            "/tmp/project/src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata/cli-request"),
        harness.regressionMetadataDirectory(Path.of("/tmp/project")));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> JazzerHarnessKind.fromKey("missing"));
    assertEquals("Unknown Jazzer harness: missing", exception.getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () -> new JazzerHarness(JazzerHarnessKind.CLI_REQUEST, " ", "dev.example.Harness", "fuzz"));
  }

  @Test
  void runTargetHelpersRejectAggregateReplayRequestsAndNormalizeValidation() {
    JazzerHarness cliHarness = JazzerHarness.cliRequest();
    JazzerRunTarget aggregateTarget =
        new JazzerRunTarget(
            " aggregate ",
            " Aggregate ",
            " fuzzAggregate ",
            ".local/runs/aggregate ",
            false,
            List.of(cliHarness, JazzerHarness.postingWorkflow()));

    assertFalse(aggregateTarget.replayable());
    assertEquals(
        Path.of("/tmp/project/.local/runs/aggregate"),
        aggregateTarget.workingDirectory(Path.of("/tmp/project")));
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, aggregateTarget::replayHarness);
    assertEquals("Target is not replayable: aggregate", exception.getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () -> new JazzerRunTarget("key", "display", "task", "directory", false, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new JazzerRunTarget(" ", "display", "task", "directory", false, List.of(cliHarness)));
  }

  @Test
  void topologyDocumentModelsNormalizeNullCollectionsAndRejectBlankShapes() throws Exception {
    JazzerTopology.Registry registry = registryWithNullCollections();

    assertEquals(List.of(), registry.harnesses());
    assertEquals(Map.of(), registry.harnessesByKey());
    assertEquals(List.of(), registry.runTargets());
    assertEquals(Map.of(), registry.runTargetsByKey());
    assertEquals(Map.of(), registry.runTargetsByTaskName());

    assertThrows(
        IllegalArgumentException.class,
        () -> new JazzerTopology.TopologyDocument(List.of(), List.of(validRunTargetDocument())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JazzerTopology.TopologyDocument(
                List.of(validHarnessDocument("cli-request")), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JazzerTopology.HarnessDocument(
                "cli-request", "CLI Request", "dev.example.Harness", " "));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JazzerTopology.RunTargetDocument(
                "cli-request",
                "CLI Request",
                "fuzzCliRequest",
                ".local/runs/cli-request",
                true,
                List.of(" ")));
    assertThrows(
        IllegalArgumentException.class,
        () -> newTopologyDocumentWithNullHarnesses(List.of(validRunTargetDocument())));
    assertThrows(
        IllegalArgumentException.class,
        () -> newTopologyDocumentWithNullRunTargets(List.of(validHarnessDocument("cli-request"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            newRunTargetDocumentWithNullHarnessKeys(
                "cli-request", "CLI Request", "fuzzCliRequest", ".local/runs/cli-request", true));
    assertThrows(
        IllegalArgumentException.class,
        () -> newRunTargetWithNullHarnesses("key", "display", "task", "directory", false));
  }

  @Test
  void load_rejects_duplicate_and_drifted_topology_documents() {
    JazzerTopology.HarnessDocument cliHarness = validHarnessDocument("cli-request");
    JazzerTopology.HarnessDocument postingHarness = validHarnessDocument("posting-workflow");

    assertEquals(
        1,
        JazzerTopology.load(
                new JazzerTopology.TopologyDocument(
                    List.of(cliHarness), List.of(validRunTargetDocument())))
            .harnesses()
            .size());

    IllegalArgumentException duplicateHarness =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerTopology.load(
                    new JazzerTopology.TopologyDocument(
                        List.of(cliHarness, validHarnessDocument("cli-request")),
                        List.of(validRunTargetDocument()))));
    assertTrue(
        String.valueOf(duplicateHarness.getMessage()).contains("Duplicate Jazzer harness key"));

    IllegalArgumentException unknownHarness =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerTopology.load(
                    new JazzerTopology.TopologyDocument(
                        List.of(cliHarness),
                        List.of(
                            new JazzerTopology.RunTargetDocument(
                                "cli-request",
                                "CLI Request",
                                "fuzzCliRequest",
                                ".local/runs/cli-request",
                                true,
                                List.of("missing"))))));
    assertTrue(
        String.valueOf(unknownHarness.getMessage())
            .contains("Unknown Jazzer harness key in topology"));

    IllegalArgumentException duplicateTargetKey =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerTopology.load(
                    new JazzerTopology.TopologyDocument(
                        List.of(cliHarness, postingHarness),
                        List.of(
                            validRunTargetDocument(),
                            new JazzerTopology.RunTargetDocument(
                                "cli-request",
                                "Posting Workflow",
                                "fuzzPostingWorkflow",
                                ".local/runs/posting-workflow",
                                true,
                                List.of("posting-workflow"))))));
    assertTrue(
        String.valueOf(duplicateTargetKey.getMessage())
            .contains("Duplicate Jazzer run target key"));

    IllegalArgumentException duplicateTaskName =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerTopology.load(
                    new JazzerTopology.TopologyDocument(
                        List.of(cliHarness, postingHarness),
                        List.of(
                            validRunTargetDocument(),
                            new JazzerTopology.RunTargetDocument(
                                "posting-workflow",
                                "Posting Workflow",
                                "fuzzCliRequest",
                                ".local/runs/posting-workflow",
                                true,
                                List.of("posting-workflow"))))));
    assertTrue(
        String.valueOf(duplicateTaskName.getMessage()).contains("Duplicate Jazzer task name"));

    IllegalArgumentException aggregateActiveTarget =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerTopology.load(
                    new JazzerTopology.TopologyDocument(
                        List.of(cliHarness, postingHarness),
                        List.of(
                            new JazzerTopology.RunTargetDocument(
                                "cli-request",
                                "CLI Request",
                                "fuzzCliRequest",
                                ".local/runs/cli-request",
                                true,
                                List.of("cli-request", "posting-workflow"))))));
    assertTrue(
        String.valueOf(aggregateActiveTarget.getMessage())
            .contains("Active Jazzer run target must reference exactly one harness"));

    IllegalArgumentException mismatchedActiveTarget =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JazzerTopology.load(
                    new JazzerTopology.TopologyDocument(
                        List.of(cliHarness, postingHarness),
                        List.of(
                            new JazzerTopology.RunTargetDocument(
                                "cli-request",
                                "CLI Request",
                                "fuzzCliRequest",
                                ".local/runs/cli-request",
                                true,
                                List.of("posting-workflow"))))));
    assertTrue(
        String.valueOf(mismatchedActiveTarget.getMessage())
            .contains("Active Jazzer run target key must match its harness key"));
  }

  @Test
  void topology_resource_loading_wraps_missing_resource_failures() {
    UncheckedIOException missingResource =
        assertThrows(
            UncheckedIOException.class,
            () -> JazzerTopology.loadResource("/dev/erst/fingrind/jazzer/support/missing.json"));

    assertTrue(
        String.valueOf(missingResource.getMessage()).contains("Failed to load Jazzer topology"));
  }

  private static JazzerTopology.HarnessDocument validHarnessDocument(String key) {
    return new JazzerTopology.HarnessDocument(
        key, key + " display", "dev.example." + key.replace('-', '_'), "fuzz");
  }

  private static JazzerTopology.RunTargetDocument validRunTargetDocument() {
    return new JazzerTopology.RunTargetDocument(
        "cli-request",
        "CLI Request",
        "fuzzCliRequest",
        ".local/runs/cli-request",
        true,
        List.of("cli-request"));
  }

  private static JazzerTopology.Registry registryWithNullCollections() throws Exception {
    Constructor<JazzerTopology.Registry> constructor =
        JazzerTopology.Registry.class.getDeclaredConstructor(
            List.class, Map.class, List.class, Map.class, Map.class);
    constructor.setAccessible(true);
    return constructor.newInstance(null, null, null, null, null);
  }

  private static JazzerTopology.TopologyDocument newTopologyDocumentWithNullHarnesses(
      List<JazzerTopology.RunTargetDocument> runTargets) throws Exception {
    return newTopologyDocumentReflectively((Object) null, runTargets);
  }

  private static JazzerTopology.TopologyDocument newTopologyDocumentWithNullRunTargets(
      List<JazzerTopology.HarnessDocument> harnesses) throws Exception {
    return newTopologyDocumentReflectively(harnesses, (Object) null);
  }

  private static JazzerTopology.RunTargetDocument newRunTargetDocumentWithNullHarnessKeys(
      String key,
      String displayName,
      String taskName,
      String workingDirectory,
      boolean activeFuzzing)
      throws Exception {
    Constructor<JazzerTopology.RunTargetDocument> constructor =
        JazzerTopology.RunTargetDocument.class.getDeclaredConstructor(
            String.class, String.class, String.class, String.class, boolean.class, List.class);
    constructor.setAccessible(true);
    try {
      return constructor.newInstance(
          key, displayName, taskName, workingDirectory, activeFuzzing, null);
    } catch (java.lang.reflect.InvocationTargetException exception) {
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

  private static JazzerRunTarget newRunTargetWithNullHarnesses(
      String key,
      String displayName,
      String taskName,
      String workingDirectory,
      boolean activeFuzzing)
      throws Exception {
    Constructor<JazzerRunTarget> constructor =
        JazzerRunTarget.class.getDeclaredConstructor(
            String.class, String.class, String.class, String.class, boolean.class, List.class);
    constructor.setAccessible(true);
    try {
      return constructor.newInstance(
          key, displayName, taskName, workingDirectory, activeFuzzing, null);
    } catch (java.lang.reflect.InvocationTargetException exception) {
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

  private static JazzerTopology.TopologyDocument newTopologyDocumentReflectively(
      @Nullable Object... arguments) throws Exception {
    Constructor<JazzerTopology.TopologyDocument> constructor =
        JazzerTopology.TopologyDocument.class.getDeclaredConstructor(List.class, List.class);
    constructor.setAccessible(true);
    try {
      return constructor.newInstance(arguments);
    } catch (java.lang.reflect.InvocationTargetException exception) {
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

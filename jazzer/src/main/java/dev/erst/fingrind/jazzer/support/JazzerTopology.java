package dev.erst.fingrind.jazzer.support;

import dev.erst.fingrind.jazzer.tool.JazzerJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Loads and validates the committed Jazzer topology model used by local tooling and wrappers. */
final class JazzerTopology {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/jazzer/support/jazzer-topology.json";
  private static final Registry REGISTRY = load();

  private JazzerTopology() {}

  static Registry registry() {
    return REGISTRY;
  }

  private static Registry load() {
    return loadResource(RESOURCE_PATH);
  }

  static Registry loadResource(String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath must not be null");
    TopologyDocument document;
    try {
      document = JazzerJson.readResource(resourcePath, TopologyDocument.class);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to load Jazzer topology resource", exception);
    }
    return load(document);
  }

  static Registry load(TopologyDocument document) {
    List<JazzerHarness> harnesses = new ArrayList<>();
    Map<String, JazzerHarness> harnessesByKey = new ConcurrentHashMap<>();
    for (HarnessDocument harnessDocument : document.harnesses()) {
      JazzerHarness harness = toHarness(harnessDocument);
      if (harnessesByKey.putIfAbsent(harness.key(), harness) != null) {
        throw new IllegalArgumentException("Duplicate Jazzer harness key: " + harness.key());
      }
      harnesses.add(harness);
    }

    List<JazzerRunTarget> runTargets = new ArrayList<>();
    Map<String, JazzerRunTarget> runTargetsByKey = new ConcurrentHashMap<>();
    Map<String, JazzerRunTarget> runTargetsByTaskName = new ConcurrentHashMap<>();
    for (RunTargetDocument runTargetDocument : document.runTargets()) {
      JazzerRunTarget runTarget = toRunTarget(runTargetDocument, harnessesByKey);
      if (runTargetsByKey.putIfAbsent(runTarget.key(), runTarget) != null) {
        throw new IllegalArgumentException("Duplicate Jazzer run target key: " + runTarget.key());
      }
      if (runTargetsByTaskName.putIfAbsent(runTarget.taskName(), runTarget) != null) {
        throw new IllegalArgumentException("Duplicate Jazzer task name: " + runTarget.taskName());
      }
      validateActiveRunTarget(runTarget);
      runTargets.add(runTarget);
    }

    return new Registry(
        List.copyOf(harnesses),
        Map.copyOf(harnessesByKey),
        List.copyOf(runTargets),
        Map.copyOf(runTargetsByKey),
        Map.copyOf(runTargetsByTaskName));
  }

  private static JazzerHarness toHarness(HarnessDocument harnessDocument) {
    return new JazzerHarness(
        JazzerHarnessKind.fromKey(harnessDocument.key()),
        harnessDocument.displayName(),
        harnessDocument.className(),
        harnessDocument.methodName());
  }

  private static JazzerRunTarget toRunTarget(
      RunTargetDocument runTargetDocument, Map<String, JazzerHarness> harnessesByKey) {
    List<JazzerHarness> harnesses =
        runTargetDocument.harnessKeys().stream()
            .map(key -> requireHarness(harnessesByKey, key))
            .toList();
    return new JazzerRunTarget(
        runTargetDocument.key(),
        runTargetDocument.displayName(),
        runTargetDocument.taskName(),
        runTargetDocument.workingDirectory(),
        runTargetDocument.activeFuzzing(),
        harnesses);
  }

  private static JazzerHarness requireHarness(
      Map<String, JazzerHarness> harnessesByKey, String harnessKey) {
    JazzerHarness harness = harnessesByKey.get(harnessKey);
    if (harness == null) {
      throw new IllegalArgumentException("Unknown Jazzer harness key in topology: " + harnessKey);
    }
    return harness;
  }

  private static void validateActiveRunTarget(JazzerRunTarget runTarget) {
    if (!runTarget.activeFuzzing()) {
      return;
    }
    if (runTarget.harnesses().size() != 1) {
      throw new IllegalArgumentException(
          "Active Jazzer run target must reference exactly one harness: " + runTarget.key());
    }
    if (!runTarget.harnesses().getFirst().key().equals(runTarget.key())) {
      throw new IllegalArgumentException(
          "Active Jazzer run target key must match its harness key: " + runTarget.key());
    }
  }

  record Registry(
      List<JazzerHarness> harnesses,
      Map<String, JazzerHarness> harnessesByKey,
      List<JazzerRunTarget> runTargets,
      Map<String, JazzerRunTarget> runTargetsByKey,
      Map<String, JazzerRunTarget> runTargetsByTaskName) {
    Registry {
      harnesses = harnesses == null ? List.of() : List.copyOf(harnesses);
      harnessesByKey = harnessesByKey == null ? Map.of() : Map.copyOf(harnessesByKey);
      runTargets = runTargets == null ? List.of() : List.copyOf(runTargets);
      runTargetsByKey = runTargetsByKey == null ? Map.of() : Map.copyOf(runTargetsByKey);
      runTargetsByTaskName =
          runTargetsByTaskName == null ? Map.of() : Map.copyOf(runTargetsByTaskName);
    }
  }

  record TopologyDocument(List<HarnessDocument> harnesses, List<RunTargetDocument> runTargets) {
    TopologyDocument {
      harnesses = harnesses == null ? List.of() : List.copyOf(harnesses);
      runTargets = runTargets == null ? List.of() : List.copyOf(runTargets);
      if (harnesses.isEmpty()) {
        throw new IllegalArgumentException("Jazzer topology must declare at least one harness");
      }
      if (runTargets.isEmpty()) {
        throw new IllegalArgumentException("Jazzer topology must declare at least one run target");
      }
    }
  }

  record HarnessDocument(String key, String displayName, String className, String methodName) {
    HarnessDocument {
      key = requireNonBlank(key, "key");
      displayName = requireNonBlank(displayName, "displayName");
      className = requireNonBlank(className, "className");
      methodName = requireNonBlank(methodName, "methodName");
    }
  }

  record RunTargetDocument(
      String key,
      String displayName,
      String taskName,
      String workingDirectory,
      boolean activeFuzzing,
      List<String> harnessKeys) {
    RunTargetDocument {
      key = requireNonBlank(key, "key");
      displayName = requireNonBlank(displayName, "displayName");
      taskName = requireNonBlank(taskName, "taskName");
      workingDirectory = requireNonBlank(workingDirectory, "workingDirectory");
      harnessKeys = harnessKeys == null ? List.of() : List.copyOf(harnessKeys);
      if (harnessKeys.isEmpty()) {
        throw new IllegalArgumentException("harnessKeys must not be empty");
      }
      harnessKeys.forEach(harnessKey -> requireNonBlank(harnessKey, "harnessKey"));
    }
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return trimmed;
  }
}

package dev.erst.fingrind.jazzer.support;

import dev.erst.fingrind.jazzer.tool.JazzerJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class JazzerTopology {
  private static final String RESOURCE_PATH = "/dev/erst/fingrind/jazzer/support/jazzer-topology.json";
  private static final Registry REGISTRY = load();

  private JazzerTopology() {}

  static Registry registry() {
    return REGISTRY;
  }

  private static Registry load() {
    TopologyDocument document;
    try {
      document = JazzerJson.readResource(RESOURCE_PATH, TopologyDocument.class);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to load Jazzer topology resource", exception);
    }

    Map<String, JazzerHarness> harnessesByKey = new LinkedHashMap<>();
    for (HarnessDocument harnessDocument : document.harnesses()) {
      JazzerHarness harness =
          new JazzerHarness(
              harnessDocument.key(),
              harnessDocument.displayName(),
              harnessDocument.className(),
              harnessDocument.methodName());
      JazzerHarness previous = harnessesByKey.put(harness.key(), harness);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicate Jazzer harness key: " + harness.key());
      }
    }

    Map<String, JazzerRunTarget> runTargetsByKey = new LinkedHashMap<>();
    Map<String, JazzerRunTarget> runTargetsByTaskName = new LinkedHashMap<>();
    for (RunTargetDocument runTargetDocument : document.runTargets()) {
      List<JazzerHarness> harnesses =
          runTargetDocument.harnessKeys().stream()
              .map(
                  key -> {
                    JazzerHarness harness = harnessesByKey.get(key);
                    if (harness == null) {
                      throw new IllegalArgumentException("Unknown Jazzer harness key in topology: " + key);
                    }
                    return harness;
                  })
              .toList();
      JazzerRunTarget runTarget =
          new JazzerRunTarget(
              runTargetDocument.key(),
              runTargetDocument.displayName(),
              runTargetDocument.taskName(),
              runTargetDocument.workingDirectory(),
              runTargetDocument.activeFuzzing(),
              harnesses);
      JazzerRunTarget previousTarget = runTargetsByKey.put(runTarget.key(), runTarget);
      if (previousTarget != null) {
        throw new IllegalArgumentException("Duplicate Jazzer run target key: " + runTarget.key());
      }
      JazzerRunTarget previousTask = runTargetsByTaskName.put(runTarget.taskName(), runTarget);
      if (previousTask != null) {
        throw new IllegalArgumentException("Duplicate Jazzer task name: " + runTarget.taskName());
      }
      if (runTarget.activeFuzzing()) {
        if (runTarget.harnesses().size() != 1) {
          throw new IllegalArgumentException(
              "Active Jazzer run target must reference exactly one harness: " + runTarget.key());
        }
        if (!runTarget.harnesses().getFirst().key().equals(runTarget.key())) {
          throw new IllegalArgumentException(
              "Active Jazzer run target key must match its harness key: " + runTarget.key());
        }
      }
    }

    return new Registry(
        List.copyOf(harnessesByKey.values()),
        Map.copyOf(harnessesByKey),
        List.copyOf(runTargetsByKey.values()),
        Map.copyOf(runTargetsByKey),
        Map.copyOf(runTargetsByTaskName));
  }

  record Registry(
      List<JazzerHarness> harnesses,
      Map<String, JazzerHarness> harnessesByKey,
      List<JazzerRunTarget> runTargets,
      Map<String, JazzerRunTarget> runTargetsByKey,
      Map<String, JazzerRunTarget> runTargetsByTaskName) {
    Registry {
      harnesses = List.copyOf(Objects.requireNonNull(harnesses, "harnesses must not be null"));
      harnessesByKey = Map.copyOf(Objects.requireNonNull(harnessesByKey, "harnessesByKey must not be null"));
      runTargets = List.copyOf(Objects.requireNonNull(runTargets, "runTargets must not be null"));
      runTargetsByKey =
          Map.copyOf(Objects.requireNonNull(runTargetsByKey, "runTargetsByKey must not be null"));
      runTargetsByTaskName =
          Map.copyOf(
              Objects.requireNonNull(runTargetsByTaskName, "runTargetsByTaskName must not be null"));
    }
  }

  private record TopologyDocument(List<HarnessDocument> harnesses, List<RunTargetDocument> runTargets) {
    TopologyDocument {
      harnesses = List.copyOf(Objects.requireNonNull(harnesses, "harnesses must not be null"));
      runTargets = List.copyOf(Objects.requireNonNull(runTargets, "runTargets must not be null"));
      if (harnesses.isEmpty()) {
        throw new IllegalArgumentException("Jazzer topology must declare at least one harness");
      }
      if (runTargets.isEmpty()) {
        throw new IllegalArgumentException("Jazzer topology must declare at least one run target");
      }
    }
  }

  private record HarnessDocument(String key, String displayName, String className, String methodName) {
    HarnessDocument {
      key = requireNonBlank(key, "key");
      displayName = requireNonBlank(displayName, "displayName");
      className = requireNonBlank(className, "className");
      methodName = requireNonBlank(methodName, "methodName");
    }
  }

  private record RunTargetDocument(
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
      harnessKeys = List.copyOf(Objects.requireNonNull(harnessKeys, "harnessKeys must not be null"));
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

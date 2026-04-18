package dev.erst.fingrind.jazzer.support;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Describes one runnable Jazzer target, including the aggregate regression target. */
public record JazzerRunTarget(
    String key,
    String displayName,
    String taskName,
    String workingDirectory,
    boolean activeFuzzing,
    List<JazzerHarness> harnesses) {
  public JazzerRunTarget {
    key = requireNonBlank(key, "key");
    displayName = requireNonBlank(displayName, "displayName");
    taskName = requireNonBlank(taskName, "taskName");
    workingDirectory = requireNonBlank(workingDirectory, "workingDirectory");
    harnesses = List.copyOf(Objects.requireNonNull(harnesses, "harnesses must not be null"));
    if (harnesses.isEmpty()) {
      throw new IllegalArgumentException("harnesses must not be empty");
    }
  }

  /** Returns the absolute working directory for this target under the Jazzer project root. */
  public Path workingDirectory(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.resolve(workingDirectory);
  }

  /** Returns whether this target corresponds to exactly one replayable harness. */
  public boolean replayable() {
    return harnesses.size() == 1;
  }

  /** Returns the single harness behind this target, rejecting aggregate targets like regression. */
  public JazzerHarness replayHarness() {
    if (!replayable()) {
      throw new IllegalArgumentException("Target is not replayable: " + key);
    }
    return harnesses.getFirst();
  }

  /** Returns all committed Jazzer run targets in stable encounter order. */
  public static JazzerRunTarget[] values() {
    return JazzerTopology.registry().runTargets().toArray(JazzerRunTarget[]::new);
  }

  /** Resolves a run target from its stable external key. */
  public static JazzerRunTarget fromKey(String key) {
    Objects.requireNonNull(key, "key must not be null");
    Map<String, JazzerRunTarget> runTargetsByKey = JazzerTopology.registry().runTargetsByKey();
    JazzerRunTarget runTarget = runTargetsByKey.get(key);
    if (runTarget == null) {
      throw new IllegalArgumentException("Unknown Jazzer run target: " + key);
    }
    return runTarget;
  }

  /** Resolves a run target from its Gradle task name. */
  public static JazzerRunTarget fromTaskName(String taskName) {
    Objects.requireNonNull(taskName, "taskName must not be null");
    Map<String, JazzerRunTarget> runTargetsByTaskName = JazzerTopology.registry().runTargetsByTaskName();
    JazzerRunTarget runTarget = runTargetsByTaskName.get(taskName);
    if (runTarget == null) {
      throw new IllegalArgumentException("Unknown Jazzer task: " + taskName);
    }
    return runTarget;
  }

  /** Returns the aggregate regression replay target. */
  public static JazzerRunTarget regression() {
    return fromKey("regression");
  }

  /** Returns the active CLI request fuzz target. */
  public static JazzerRunTarget cliRequest() {
    return fromKey("cli-request");
  }

  /** Returns the active ledger-plan request fuzz target. */
  public static JazzerRunTarget ledgerPlanRequest() {
    return fromKey("ledger-plan-request");
  }

  /** Returns the active posting workflow fuzz target. */
  public static JazzerRunTarget postingWorkflow() {
    return fromKey("posting-workflow");
  }

  /** Returns the active SQLite book round-trip fuzz target. */
  public static JazzerRunTarget sqliteBookRoundTrip() {
    return fromKey("sqlite-book-roundtrip");
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

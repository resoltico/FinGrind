package dev.erst.fingrind.jazzer.support;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Enumerates the FinGrind Jazzer harnesses exposed for local fuzzing and regression replay. */
public enum JazzerHarness {
  CLI_REQUEST(
      "cli-request",
      "CLI Request",
      "dev.erst.fingrind.cli.CliRequestFuzzTest",
      "readPostEntryCommand"),
  POSTING_WORKFLOW(
      "posting-workflow",
      "Posting Workflow",
      "dev.erst.fingrind.cli.PostingWorkflowFuzzTest",
      "exercisePostingWorkflow"),
  SQLITE_BOOK_ROUND_TRIP(
      "sqlite-book-roundtrip",
      "SQLite Book Round Trip",
      "dev.erst.fingrind.cli.SqliteBookRoundTripFuzzTest",
      "roundTripSingleBook");

  private final String key;
  private final String displayName;
  private final String className;
  private final String methodName;

  JazzerHarness(String key, String displayName, String className, String methodName) {
    this.key = Objects.requireNonNull(key, "key must not be null");
    this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
    this.className = Objects.requireNonNull(className, "className must not be null");
    this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
  }

  /** Returns the stable external key used in tasks, run directories, and local corpora. */
  public String key() {
    return key;
  }

  /** Returns the human-readable harness name for operator output. */
  public String displayName() {
    return displayName;
  }

  /** Returns the fully qualified fuzz test class name. */
  public String className() {
    return className;
  }

  /** Returns the fuzz test method that owns this harness's input directory. */
  public String methodName() {
    return methodName;
  }

  /** Returns the committed regression-input directory for this harness. */
  public Path inputDirectory(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.resolve("src/fuzz/resources").resolve(inputResourceDirectory());
  }

  /** Returns the classpath resource suffix used by Jazzer regression-input discovery. */
  public String inputResourceDirectory() {
    String packagePath =
        className.substring(0, className.lastIndexOf('.')).replace('.', '/');
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    return packagePath + "/" + simpleName + "Inputs/" + methodName;
  }

  /** Resolves a harness from its stable external key. */
  public static JazzerHarness fromKey(String key) {
    Objects.requireNonNull(key, "key must not be null");
    return Arrays.stream(values())
        .filter(harness -> harness.key.equals(key))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown Jazzer harness: " + key));
  }
}

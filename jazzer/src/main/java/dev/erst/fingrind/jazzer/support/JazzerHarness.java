package dev.erst.fingrind.jazzer.support;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Describes one individual Jazzer harness that FinGrind exposes for local fuzzing. */
public record JazzerHarness(String key, String displayName, String className, String methodName) {
  public JazzerHarness {
    key = requireNonBlank(key, "key");
    displayName = requireNonBlank(displayName, "displayName");
    className = requireNonBlank(className, "className");
    methodName = requireNonBlank(methodName, "methodName");
  }

  /** Returns the resource directory where committed regression inputs for this harness live. */
  public Path inputDirectory(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.resolve("src/fuzz/resources").resolve(inputResourceDirectory());
  }

  /** Returns the directory where committed regression metadata entries for this harness live. */
  public Path regressionMetadataDirectory(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return regressionMetadataRoot(projectDirectory).resolve(key);
  }

  /** Returns the classpath resource suffix used by Jazzer regression-input discovery. */
  public String inputResourceDirectory() {
    String packagePath = className.substring(0, className.lastIndexOf('.')).replace('.', '/');
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    return packagePath + "/" + simpleName + "Inputs/" + methodName;
  }

  /** Returns all committed Jazzer harnesses in stable encounter order. */
  public static JazzerHarness[] values() {
    return JazzerTopology.registry().harnesses().toArray(JazzerHarness[]::new);
  }

  /** Resolves a harness from its stable external key. */
  public static JazzerHarness fromKey(String key) {
    Objects.requireNonNull(key, "key must not be null");
    Map<String, JazzerHarness> harnessesByKey = JazzerTopology.registry().harnessesByKey();
    JazzerHarness harness = harnessesByKey.get(key);
    if (harness == null) {
      throw new IllegalArgumentException("Unknown Jazzer harness: " + key);
    }
    return harness;
  }

  /** Returns the project-relative root directory that owns all committed regression metadata. */
  public static Path regressionMetadataRoot(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.resolve("src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");
  }

  /** Returns the canonical CLI request harness. */
  public static JazzerHarness cliRequest() {
    return fromKey("cli-request");
  }

  /** Returns the canonical ledger-plan request harness. */
  public static JazzerHarness ledgerPlanRequest() {
    return fromKey("ledger-plan-request");
  }

  /** Returns the canonical posting workflow harness. */
  public static JazzerHarness postingWorkflow() {
    return fromKey("posting-workflow");
  }

  /** Returns the canonical SQLite book round-trip harness. */
  public static JazzerHarness sqliteBookRoundTrip() {
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

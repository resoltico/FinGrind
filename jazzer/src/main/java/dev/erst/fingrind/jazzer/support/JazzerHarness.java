package dev.erst.fingrind.jazzer.support;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Describes one individual Jazzer harness that FinGrind exposes for local fuzzing. */
public record JazzerHarness(
    JazzerHarnessKind kind, String displayName, String className, String methodName) {
  public JazzerHarness {
    Objects.requireNonNull(kind, "kind must not be null");
    displayName = requireNonBlank(displayName, "displayName");
    className = requireNonBlank(className, "className");
    methodName = requireNonBlank(methodName, "methodName");
  }

  /** Returns the stable external key for this harness. */
  public String key() {
    return kind.key();
  }

  /** Returns the resource directory where committed regression inputs for this harness live. */
  public Path inputDirectory(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.resolve("src/fuzz/resources").resolve(inputResourceDirectory());
  }

  /** Returns the directory where committed regression metadata entries for this harness live. */
  public Path regressionMetadataDirectory(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return regressionMetadataRoot(projectDirectory).resolve(key());
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

  /** Resolves a harness from its closed harness vocabulary. */
  public static JazzerHarness fromKind(JazzerHarnessKind kind) {
    Objects.requireNonNull(kind, "kind must not be null");
    return fromKey(kind.key());
  }

  /** Returns the project-relative root directory that owns all committed regression metadata. */
  public static Path regressionMetadataRoot(Path projectDirectory) {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    return projectDirectory.resolve(
        "src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata");
  }

  /** Returns the canonical CLI request harness. */
  public static JazzerHarness cliRequest() {
    return fromKind(JazzerHarnessKind.CLI_REQUEST);
  }

  /** Returns the canonical ledger-plan request harness. */
  public static JazzerHarness ledgerPlanRequest() {
    return fromKind(JazzerHarnessKind.LEDGER_PLAN_REQUEST);
  }

  /** Returns the canonical posting workflow harness. */
  public static JazzerHarness postingWorkflow() {
    return fromKind(JazzerHarnessKind.POSTING_WORKFLOW);
  }

  /** Returns the canonical SQLite book round-trip harness. */
  public static JazzerHarness sqliteBookRoundTrip() {
    return fromKind(JazzerHarnessKind.SQLITE_BOOK_ROUND_TRIP);
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

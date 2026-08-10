package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Resolves and admits the canonical owner-only store for publication transaction journals. */
public final class PublicationTransactionStore {
  private static final String XDG_STATE_HOME = "XDG_STATE_HOME";
  private static final String LOCAL_APP_DATA = "LOCALAPPDATA";

  private PublicationTransactionStore() {}

  /** Returns the existing or freshly created owner-only canonical publication-transaction store. */
  public static Path openCanonicalStore() throws PrivateOutputDirectory.Violation {
    Path root =
        canonicalStoreRoot(
            System.getProperty("os.name"), System.getenv(), System.getProperty("user.home"));
    return open(root);
  }

  static Path open(Path root) throws PrivateOutputDirectory.Violation {
    Path checkedRoot = Objects.requireNonNull(root, "root");
    if (Files.exists(checkedRoot, LinkOption.NOFOLLOW_LINKS)) {
      PrivateOutputDirectory.requireExistingOwnerOnly(checkedRoot);
    } else {
      PrivateOutputDirectory.createNewOwnerOnlyDirectories(checkedRoot);
    }
    PrivateOutputDirectory.requireExistingOwnerOnly(checkedRoot);
    try {
      return checkedRoot.toRealPath();
    } catch (IOException exception) {
      throw new PrivateOutputDirectory.Violation(
          "FinGrind could not resolve the canonical publication transaction store.", exception);
    }
  }

  static Path canonicalStoreRoot(
      String operatingSystemName, Map<String, String> environment, String userHome) {
    String checkedOperatingSystemName =
        Objects.requireNonNull(operatingSystemName, "operatingSystemName");
    Map<String, String> checkedEnvironment = Objects.requireNonNull(environment, "environment");
    if (PrivateOutputFile.isWindows(checkedOperatingSystemName)) {
      return Path.of(requireEnvironmentPath(checkedEnvironment, LOCAL_APP_DATA))
          .resolve("FinGrind")
          .resolve("publication-transactions");
    }
    String stateHome = checkedEnvironment.get(XDG_STATE_HOME);
    Path base =
        stateHome == null || stateHome.isBlank()
            ? Path.of(Objects.requireNonNull(userHome, "userHome"), ".local", "state")
            : Path.of(stateHome);
    return base.resolve("fingrind").resolve("publication-transactions");
  }

  private static String requireEnvironmentPath(
      Map<String, String> environment, String variableName) {
    String value = environment.get(variableName);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          variableName + " must name the canonical publication state root.");
    }
    return value;
  }
}

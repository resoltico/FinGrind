package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Owns lifecycle, namespace admission, and isolated test selection for object-coordination roots.
 *
 * <p>Production processes always coordinate beneath one private per-user root. Test scopes may
 * temporarily select an isolated root, but cannot change the production namespace.
 */
final class SqliteObjectCoordinationRoot {
  private static final String DEFAULT_ROOT_DIRECTORY = ".fingrind-coordination-v4";
  private static final String RETIRED_V3_ROOT_DIRECTORY = ".fingrind-coordination-v3";
  private static final String RETIRED_V2_ROOT_DIRECTORY = ".fingrind-coordination-v2";
  private static final String OBJECT_PREFIX = "object-v4-";
  private static final String CONTROL_SUFFIX = ".control";
  private static final String RETIRED_V3_OBJECT_PREFIX = "object-v3-";
  private static final String RETIRED_V2_OBJECT_PREFIX = "object-v2-";
  private static final String RETIRED_V4_REGISTRY_FILE = ".fingrind-object-registry-v4.control";
  private static final ReentrantLock TEST_ROOT_LOCK = new ReentrantLock();
  private static final Deque<Path> TEST_ROOT_STACK = new ArrayDeque<>();

  private SqliteObjectCoordinationRoot() {}

  /** Resolves and validates the private root that may contain retained object controls. */
  static Path requirePrivateRoot() throws IOException {
    Path root = configuredRoot();
    requireNoRetiredNamespace(root);
    try {
      Path canonicalRoot = createOrValidatePrivateRoot(root);
      requireNoRetiredObjectResidue(canonicalRoot);
      return canonicalRoot;
    } catch (PrivateOutputDirectory.Violation violation) {
      throw new IOException(
          "FinGrind could not establish its private object-coordination root at " + root + ".",
          violation);
    } catch (IOException exception) {
      throw new IOException(
          "FinGrind could not create or validate its private object-coordination root at "
              + root
              + ": "
              + Objects.requireNonNullElse(exception.getMessage(), "unspecified I/O failure"),
          exception);
    }
  }

  /** Returns the current-version control path for one explicit physical object identity. */
  static Path objectControlPath(Path privateRoot, String objectIdentity) {
    return Objects.requireNonNull(privateRoot, "privateRoot")
        .resolve(
            OBJECT_PREFIX
                + SqliteCoordinationControlProtocol.sha256Hex(
                    Objects.requireNonNull(objectIdentity, "objectIdentity"))
                + CONTROL_SUFFIX);
  }

  /**
   * Installs an isolated coordination root for one in-process test scope.
   *
   * <p>Production processes always coordinate beneath the single owner-home root and cannot select
   * a divergent namespace through configuration.
   */
  static AutoCloseable installTestRoot(Path root) {
    Path checkedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    TEST_ROOT_LOCK.lock();
    try {
      TEST_ROOT_STACK.addLast(checkedRoot);
    } finally {
      TEST_ROOT_LOCK.unlock();
    }
    return () -> {
      TEST_ROOT_LOCK.lock();
      try {
        if (!checkedRoot.equals(TEST_ROOT_STACK.peekLast())) {
          throw new IllegalStateException(
              "The FinGrind object-coordination test root changed before its scope closed.");
        }
        TEST_ROOT_STACK.removeLast();
      } finally {
        TEST_ROOT_LOCK.unlock();
      }
    };
  }

  /** Creates a fresh private root or validates an existing private root without adopting races. */
  static Path createOrValidatePrivateRoot(Path root) throws IOException {
    Path checkedRoot = Objects.requireNonNull(root, "root");
    if (Files.exists(checkedRoot, LinkOption.NOFOLLOW_LINKS)) {
      PrivateOutputDirectory.requireExistingOwnerOnly(checkedRoot);
    } else {
      PrivateOutputDirectory.createNewOwnerOnlyDirectories(checkedRoot);
    }
    return checkedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS).toAbsolutePath().normalize();
  }

  /** Resolves the process-wide root, with test scopes taking precedence over the user-home root. */
  static Path configuredRoot() throws IOException {
    TEST_ROOT_LOCK.lock();
    try {
      @Nullable Path testRoot = TEST_ROOT_STACK.peekLast();
      if (testRoot != null) {
        return testRoot;
      }
    } finally {
      TEST_ROOT_LOCK.unlock();
    }
    return userHomeRoot(System.getProperty("user.home"));
  }

  /** Derives the production root from one supplied user-home value. */
  static Path userHomeRoot(@Nullable String userHome) throws IOException {
    if (userHome == null || userHome.isBlank()) {
      throw new IOException("FinGrind cannot resolve a per-user object-coordination root.");
    }
    return Path.of(userHome).toAbsolutePath().normalize().resolve(DEFAULT_ROOT_DIRECTORY);
  }

  private static void requireNoRetiredNamespace(Path v4Root) throws IOException {
    for (String retiredRootName : List.of(RETIRED_V3_ROOT_DIRECTORY, RETIRED_V2_ROOT_DIRECTORY)) {
      Path retiredRoot = siblingNamespace(v4Root, retiredRootName);
      if (Files.exists(retiredRoot, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException(
            "Retired FinGrind object-coordination namespace blocks the v4 protocol: "
                + retiredRoot
                + ".");
      }
    }
  }

  private static Path siblingNamespace(Path v4Root, String defaultRetiredName) {
    Path checkedRoot = Objects.requireNonNull(v4Root, "v4Root");
    Path parent = Objects.requireNonNull(checkedRoot.getParent(), "v4Root parent");
    String leaf = Objects.requireNonNull(checkedRoot.getFileName(), "v4Root fileName").toString();
    if (leaf.endsWith("v4")) {
      return parent.resolve(
          leaf.substring(0, leaf.length() - 2)
              + defaultRetiredName.substring(defaultRetiredName.length() - 2));
    }
    return parent.resolve(defaultRetiredName);
  }

  private static void requireNoRetiredObjectResidue(Path root) throws IOException {
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        String name = Objects.requireNonNull(entry.getFileName(), "entry fileName").toString();
        if (name.startsWith(RETIRED_V3_OBJECT_PREFIX)
            || name.startsWith(RETIRED_V2_OBJECT_PREFIX)
            || RETIRED_V4_REGISTRY_FILE.equals(name)) {
          throw new IOException(
              "Retired FinGrind object-coordination state blocks the v4 protocol.");
        }
        if (!isCurrentObjectControlName(name)) {
          throw new IOException(
              "Unexpected state exists in the private FinGrind object-coordination root: "
                  + entry
                  + ".");
        }
      }
    }
  }

  private static boolean isCurrentObjectControlName(String fileName) {
    if (!fileName.startsWith(OBJECT_PREFIX) || !fileName.endsWith(CONTROL_SUFFIX)) {
      return false;
    }
    String digest =
        fileName.substring(OBJECT_PREFIX.length(), fileName.length() - CONTROL_SUFFIX.length());
    if (digest.length() != 64) {
      return false;
    }
    for (int index = 0; index < digest.length(); index++) {
      char character = digest.charAt(index);
      if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) {
        return false;
      }
    }
    return true;
  }
}

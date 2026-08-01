package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Exact creation and fresh-child allocation for private output directories. */
final class PrivateOutputDirectoryCreation {
  private static final int MAXIMUM_FRESH_CHILD_ATTEMPTS = 64;
  private static final Operations PRODUCTION_OPERATIONS =
      new ProductionOperations(
          PrivateOutputDirectory.filesystemAccess(),
          System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"),
          WindowsPrivateOutputFilePlatformAdapter.PRODUCTION);

  private PrivateOutputDirectoryCreation() {}

  static void createNewOwnerOnlyDirectories(Path plannedDirectory)
      throws PrivateOutputDirectory.Violation {
    createNewOwnerOnlyDirectories(plannedDirectory, PRODUCTION_OPERATIONS);
  }

  static void createNewOwnerOnlyDirectories(Path plannedDirectory, Operations operations)
      throws PrivateOutputDirectory.Violation {
    Path checkedPlannedDirectory = Objects.requireNonNull(plannedDirectory, "plannedDirectory");
    Operations checkedOperations = Objects.requireNonNull(operations, "operations");
    createNewOwnerOnlyDirectories(
        checkedPlannedDirectory,
        checkedOperations,
        selectCreationMode(checkedPlannedDirectory, checkedOperations));
  }

  static CreationMode selectCreationMode(Path plannedDirectory, PlatformSelector platform)
      throws PrivateOutputDirectory.Violation {
    Path checkedPath = Objects.requireNonNull(plannedDirectory, "plannedDirectory");
    PlatformSelector checkedPlatform = Objects.requireNonNull(platform, "platform");
    if (checkedPlatform.supportsPosix(checkedPath)) {
      return CreationMode.POSIX;
    }
    if (checkedPlatform.supportsAcl(checkedPath) && checkedPlatform.isWindows()) {
      return CreationMode.WINDOWS;
    }
    throw new PrivateOutputDirectory.Violation(
        "FinGrind cannot atomically create a private output directory at "
            + PrivateOutputDirectoryFailures.absolutePath(checkedPath)
            + ".",
        new UnsupportedOperationException(
            "The selected filesystem lacks an atomic POSIX or Windows owner-only directory creator."));
  }

  static Path createNewOwnerOnlyChild(Path parentDirectory, String namePrefix)
      throws PrivateOutputDirectory.Violation {
    return createNewOwnerOnlyChild(
        parentDirectory,
        namePrefix,
        () -> UUID.randomUUID().toString(),
        PrivateOutputDirectoryCreation::createNewOwnerOnlyDirectories,
        PrivateOutputDirectory.filesystemAccess());
  }

  static Path createNewOwnerOnlyChild(
      Path parentDirectory,
      String namePrefix,
      ChildTokenSource tokenSource,
      ChildDirectoryCreator directoryCreator,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws PrivateOutputDirectory.Violation {
    Path parent =
        Objects.requireNonNull(parentDirectory, "parentDirectory").toAbsolutePath().normalize();
    String prefix = Objects.requireNonNull(namePrefix, "namePrefix");
    ChildTokenSource checkedTokenSource = Objects.requireNonNull(tokenSource, "tokenSource");
    ChildDirectoryCreator checkedCreator =
        Objects.requireNonNull(directoryCreator, "directoryCreator");
    PrivateOutputDirectory.FilesystemAccess checkedAccess =
        Objects.requireNonNull(filesystemAccess, "filesystemAccess");
    if (prefix.isEmpty()) {
      throw new IllegalArgumentException("namePrefix must be nonempty.");
    }
    requireExistingCreationParent(parent, checkedAccess);
    for (int attempt = 0; attempt < MAXIMUM_FRESH_CHILD_ATTEMPTS; attempt++) {
      String token = Objects.requireNonNull(checkedTokenSource.nextToken(), "child token");
      if (token.isEmpty()) {
        throw new IllegalArgumentException("child token must be nonempty.");
      }
      Path candidate = parent.resolve(ArtifactStageFileName.requireValidLeaf(prefix + token));
      try {
        checkedCreator.create(candidate);
        return candidate;
      } catch (PrivateOutputDirectory.Violation collision) {
        if (collision.kind() != PrivateOutputDirectory.Violation.Kind.PATH_COLLISION
            || !candidateExists(candidate, checkedAccess)) {
          throw collision;
        }
      }
    }
    throw new PrivateOutputDirectory.Violation(
        PrivateOutputDirectory.Violation.Kind.PATH_COLLISION,
        "FinGrind could not allocate a fresh private output directory beneath "
            + PrivateOutputDirectoryFailures.absolutePath(parent)
            + ".");
  }

  private static void createNewOwnerOnlyDirectories(
      Path plannedDirectory, Operations operations, CreationMode mode)
      throws PrivateOutputDirectory.Violation {
    try {
      PrivateOutputDirectory.FilesystemAccess filesystemAccess = operations.filesystemAccess();
      List<Path> missingDirectories =
          PrivateOutputDirectoryPathTopology.missingDirectoryChain(
              plannedDirectory, filesystemAccess);
      if (missingDirectories.isEmpty()) {
        throw PrivateOutputDirectoryFailures.pathCollision(
            plannedDirectory,
            "must remain absent when FinGrind atomically creates an owner-only directory");
      }
      for (Path missingDirectory : missingDirectories) {
        createOneNewOwnerOnlyDirectory(missingDirectory, operations, mode, filesystemAccess);
      }
    } catch (PrivateOutputDirectory.Violation exception) {
      throw exception;
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new PrivateOutputDirectory.Violation(
          "FinGrind could not atomically create private owner-only output directories beneath "
              + PrivateOutputDirectoryFailures.absolutePath(plannedDirectory)
              + ".",
          exception);
    }
  }

  private static void createOneNewOwnerOnlyDirectory(
      Path missingDirectory,
      Operations operations,
      CreationMode mode,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    @Nullable Path parent = missingDirectory.getParent();
    PrivateOutputDirectory.requireCreationAncestry(
        Objects.requireNonNull(parent, "missing-directory chain must retain an existing parent"),
        filesystemAccess);
    try {
      mode.createDirectory(missingDirectory, operations);
    } catch (FileAlreadyExistsException collision) {
      throw PrivateOutputDirectoryFailures.pathCollision(
          missingDirectory,
          "must remain absent when FinGrind atomically creates an owner-only directory",
          collision);
    }
    PrivateOutputDirectory.requireExistingOwnerOnly(missingDirectory, filesystemAccess);
  }

  private static void requireExistingCreationParent(
      Path parent, PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws PrivateOutputDirectory.Violation {
    try {
      if (filesystemAccess.noFollowEntryKind(parent)
          != PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY) {
        throw PrivateOutputDirectoryFailures.requirement(
            parent, "must be an existing real directory for fresh child allocation");
      }
      PrivateOutputDirectoryPathTopology.requireLexicalRealDirectoryPath(parent, filesystemAccess);
      PrivateOutputDirectory.requireCreationAncestry(parent, filesystemAccess);
    } catch (PrivateOutputDirectory.Violation exception) {
      throw exception;
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new PrivateOutputDirectory.Violation(
          "FinGrind could not establish private output creation ancestry for "
              + PrivateOutputDirectoryFailures.absolutePath(parent)
              + ".",
          exception);
    }
  }

  private static boolean candidateExists(
      Path candidate, PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws PrivateOutputDirectory.Violation {
    try {
      return filesystemAccess.noFollowEntryKind(candidate)
          != PrivateOutputDirectory.NoFollowEntryKind.MISSING;
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new PrivateOutputDirectory.Violation(
          "FinGrind could not establish whether a private output directory candidate exists at "
              + PrivateOutputDirectoryFailures.absolutePath(candidate)
              + ".",
          exception);
    }
  }

  /** The atomic owner-only creation mechanism selected for one filesystem. */
  enum CreationMode {
    POSIX {
      @Override
      void createDirectory(Path directory, Operations operations) throws IOException {
        operations.createPosixDirectory(
            directory,
            PosixFilePermissions.asFileAttribute(
                PrivateOutputDirectorySecurity.privatePosixDirectoryPermissions()));
      }
    },
    WINDOWS {
      @Override
      void createDirectory(Path directory, Operations operations) throws IOException {
        operations.createWindowsDirectory(directory);
      }
    };

    /** Creates one fresh owner-only directory through this exact platform mechanism. */
    abstract void createDirectory(Path directory, Operations operations) throws IOException;
  }

  /** Injectable filesystem-capability facts used to select the creation mechanism. */
  interface PlatformSelector {
    /** Reports whether the path's filesystem supports POSIX attributes. */
    boolean supportsPosix(Path path);

    /** Reports whether the path's filesystem supports ACL attributes. */
    boolean supportsAcl(Path path);

    /** Reports whether the running platform is Windows. */
    boolean isWindows();
  }

  /** Injectable creation operations used to prove the atomic directory contract in tests. */
  interface Operations extends PlatformSelector {
    /** Returns the filesystem observation boundary for this operation set. */
    PrivateOutputDirectory.FilesystemAccess filesystemAccess();

    /** Atomically creates one POSIX directory with the supplied creation attributes. */
    void createPosixDirectory(Path directory, FileAttribute<?>... attributes) throws IOException;

    /** Atomically creates one protected Windows directory. */
    void createWindowsDirectory(Path directory) throws IOException;
  }

  /** Supplies a collision-resistant suffix for one fresh private child directory. */
  @FunctionalInterface
  interface ChildTokenSource {
    /** Returns the next nonempty candidate suffix. */
    String nextToken();
  }

  /** Creates exactly one candidate private child directory. */
  @FunctionalInterface
  interface ChildDirectoryCreator {
    /** Creates the supplied candidate or reports its deterministic creation failure. */
    void create(Path candidate) throws PrivateOutputDirectory.Violation;
  }

  /** Creates one protected Windows directory through the exact selected native transport. */
  @FunctionalInterface
  interface WindowsDirectoryCreator {
    /** Creates the supplied directory with the protected Windows owner-only descriptor. */
    void createDirectory(Path directory) throws IOException;
  }

  /** Production implementation of the filesystem capability and exact-creation boundary. */
  static final class ProductionOperations implements Operations {
    private final PrivateOutputDirectory.FilesystemAccess filesystemAccess;
    private final boolean windows;
    private final WindowsDirectoryCreator windowsDirectoryCreator;

    /** Binds filesystem-capability facts and the exact protected Windows directory creator. */
    ProductionOperations(
        PrivateOutputDirectory.FilesystemAccess filesystemAccess,
        boolean windows,
        WindowsDirectoryCreator windowsDirectoryCreator) {
      this.filesystemAccess = Objects.requireNonNull(filesystemAccess, "filesystemAccess");
      this.windows = windows;
      this.windowsDirectoryCreator =
          Objects.requireNonNull(windowsDirectoryCreator, "windowsDirectoryCreator");
    }

    @Override
    public boolean supportsPosix(Path path) {
      return filesystemAccess.supportsPosix(path);
    }

    @Override
    public boolean supportsAcl(Path path) {
      return filesystemAccess.supportsAcl(path);
    }

    @Override
    public boolean isWindows() {
      return windows;
    }

    @Override
    public PrivateOutputDirectory.FilesystemAccess filesystemAccess() {
      return filesystemAccess;
    }

    @Override
    public void createPosixDirectory(Path directory, FileAttribute<?>... attributes)
        throws IOException {
      Files.createDirectory(directory, attributes);
    }

    @Override
    public void createWindowsDirectory(Path directory) throws IOException {
      windowsDirectoryCreator.createDirectory(directory);
    }
  }
}

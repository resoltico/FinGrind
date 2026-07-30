package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Admits an existing output directory only when another principal cannot substitute a staged
 * artifact before publication.
 */
public final class PrivateOutputDirectory {
  private static final FilesystemAccess FILE_SYSTEM =
      new NioPrivateOutputDirectoryFilesystemAccess();

  private PrivateOutputDirectory() {}

  /**
   * Requires an existing real directory whose staging namespace is accessible only to its owner.
   *
   * <p>The caller must not create or widen the directory here: changing a caller-selected output
   * directory's permissions would silently alter unrelated files and collaborators. Callers must
   * instead choose an already-private directory.
   */
  public static void requireExistingOwnerOnly(Path directory) throws Violation {
    requireExistingOwnerOnly(directory, FILE_SYSTEM);
  }

  /**
   * Requires the existing portion of one planned output-directory ancestry to deny non-owner
   * mutation before a caller creates any missing descendant.
   *
   * <p>A sticky POSIX ancestor is admitted provisionally because the later exact-directory
   * admission verifies the newly created child's owner and completes the sticky-directory proof.
   */
  public static void requireCreationAncestry(Path plannedDirectory) throws Violation {
    requireCreationAncestry(plannedDirectory, FILE_SYSTEM);
  }

  /**
   * Creates every missing component of one output-directory path as a fresh owner-only directory
   * without adopting an entry that appears during creation.
   *
   * <p>Existing directories are deliberately not reused here. Callers that selected an existing
   * directory must use {@link #requireExistingOwnerOnly(Path)} instead.
   */
  public static void createNewOwnerOnlyDirectories(Path plannedDirectory) throws Violation {
    PrivateOutputDirectoryCreation.createNewOwnerOnlyDirectories(plannedDirectory);
  }

  /**
   * Allocates one fresh random owner-only child beneath an existing creation-safe directory.
   *
   * <p>The supplied parent is never repaired or adopted as private output. It is admitted only as
   * creation ancestry, which permits a sticky POSIX temporary directory but rejects a mutable ACL
   * parent that could delete and replace a child after its protected creation.
   */
  public static Path createNewOwnerOnlyChild(Path parentDirectory, String namePrefix)
      throws Violation {
    return PrivateOutputDirectoryCreation.createNewOwnerOnlyChild(parentDirectory, namePrefix);
  }

  static void requireExistingOwnerOnly(Path directory, FilesystemAccess filesystemAccess)
      throws Violation {
    Path checkedDirectory = Objects.requireNonNull(directory, "directory");
    FilesystemAccess checkedAccess = Objects.requireNonNull(filesystemAccess, "filesystemAccess");
    try {
      NoFollowEntryKind kind = checkedAccess.noFollowEntryKind(checkedDirectory);
      if (kind == NoFollowEntryKind.MISSING) {
        throw PrivateOutputDirectoryFailures.requirement(
            checkedDirectory, "must be an existing real directory");
      }
      if (kind != NoFollowEntryKind.DIRECTORY) {
        throw PrivateOutputDirectoryFailures.pathCollision(
            checkedDirectory,
            "must not name a symbolic link or non-directory output-directory entry");
      }
      PrivateOutputDirectoryPathTopology.requireLexicalRealDirectoryPath(
          checkedDirectory, checkedAccess);
      Path canonicalDirectory = checkedAccess.toRealPath(checkedDirectory);
      PrivateOutputDirectorySecurity.OutputDirectorySecurityIdentity outputIdentity =
          PrivateOutputDirectorySecurity.requirePrivateDirectory(canonicalDirectory, checkedAccess);
      PrivateOutputDirectorySecurity.requireProtectedAncestry(
          canonicalDirectory, outputIdentity, checkedAccess);
    } catch (Violation exception) {
      throw exception;
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new Violation(
          "FinGrind could not establish private owner-only access for output directory "
              + PrivateOutputDirectoryFailures.absolutePath(checkedDirectory)
              + ".",
          exception);
    }
  }

  static void requireCreationAncestry(Path plannedDirectory, FilesystemAccess filesystemAccess)
      throws Violation {
    Path checkedPlannedDirectory = Objects.requireNonNull(plannedDirectory, "plannedDirectory");
    FilesystemAccess checkedAccess = Objects.requireNonNull(filesystemAccess, "filesystemAccess");
    try {
      Path existingAncestor =
          PrivateOutputDirectoryPathTopology.nearestExistingDirectory(
              checkedPlannedDirectory, checkedAccess);
      PrivateOutputDirectorySecurity.requireExistingCreationAncestry(
          existingAncestor, checkedAccess);
    } catch (Violation exception) {
      throw exception;
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new Violation(
          "FinGrind could not establish protected creation ancestry for output directory "
              + PrivateOutputDirectoryFailures.absolutePath(checkedPlannedDirectory)
              + ".",
          exception);
    }
  }

  static FilesystemAccess filesystemAccess() {
    return FILE_SYSTEM;
  }

  /** Identifies a caller-selected output directory that cannot safely host a staged artifact. */
  public static final class Violation extends IOException {
    private static final long serialVersionUID = 1L;
    private final Kind kind;

    Violation(Kind kind, String message) {
      super(message);
      this.kind = Objects.requireNonNull(kind, "kind");
    }

    Violation(String message, Throwable cause) {
      this(Kind.OWNER_ONLY_REQUIRED, message, cause);
    }

    Violation(Kind kind, String message, Throwable cause) {
      super(message, cause);
      this.kind = Objects.requireNonNull(kind, "kind");
    }

    /**
     * Classifies whether the selected path collides with a non-directory or lacks private access.
     */
    public Kind kind() {
      return kind;
    }

    /** Closed public category for a private-output admission refusal. */
    public enum Kind {
      PATH_COLLISION,
      OWNER_ONLY_REQUIRED
    }
  }

  /** Filesystem facts needed to evaluate private directory admission. */
  interface FilesystemAccess {
    /** Reports whether the path is a directory without following a symbolic link. */
    boolean isDirectoryNoFollow(Path path);

    /** Reads the selected entry without following a symbolic link. */
    NoFollowEntryKind noFollowEntryKind(Path path) throws IOException;

    /** Reports whether the path's filesystem exposes POSIX permission facts. */
    boolean supportsPosix(Path path);

    /** Reports whether the path's filesystem exposes ACL facts. */
    boolean supportsAcl(Path path);

    /** Resolves the path to its canonical filesystem location. */
    Path toRealPath(Path path) throws IOException;

    /** Returns the immediate parent, or {@code null} when the path is a filesystem root. */
    @Nullable Path parent(Path path);

    /** Reads the POSIX permissions without following a symbolic link. */
    Set<PosixFilePermission> readPosixPermissions(Path path) throws IOException;

    /** Reads the owner, numeric identity, and sticky-bit state for a POSIX directory. */
    PosixDirectoryIdentity readPosixDirectoryIdentity(Path path) throws IOException;

    /** Reads the owning principal and complete ACL for a directory. */
    AclState readAcl(Path path) throws IOException;

    /**
     * Reports whether one Windows operating-system principal is part of the trusted computing base
     * for private-output ancestry.
     *
     * <p>Implementations must return {@code false} unless they can identify the principal from the
     * platform's stable security identity rather than from a display name.
     */
    boolean isTrustedAclMutationPrincipal(Path path, UserPrincipal principal) throws IOException;
  }

  /** Closed nofollow state for one lexical output-path component. */
  enum NoFollowEntryKind {
    MISSING,
    DIRECTORY,
    OTHER
  }

  /** Immutable POSIX owner facts used to evaluate output-directory ancestry. */
  record PosixDirectoryIdentity(UserPrincipal owner, long unixUserId, boolean sticky) {
    PosixDirectoryIdentity {
      Objects.requireNonNull(owner, "owner");
      if (unixUserId < 0L) {
        throw new IllegalArgumentException("unixUserId must be non-negative.");
      }
    }
  }

  /** Immutable ACL snapshot used for one private-directory admission decision. */
  record AclState(UserPrincipal owner, List<AclEntry> entries) {
    AclState {
      Objects.requireNonNull(owner, "owner");
      entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
  }
}

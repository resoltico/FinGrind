package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Reads the nofollow topology of one output-directory path without making security decisions. */
final class PrivateOutputDirectoryPathTopology {
  private PrivateOutputDirectoryPathTopology() {}

  static Path nearestExistingDirectory(
      Path plannedDirectory, PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    Path candidate = plannedDirectory;
    while (true) {
      PrivateOutputDirectory.NoFollowEntryKind kind = filesystemAccess.noFollowEntryKind(candidate);
      if (kind == PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY) {
        requireLexicalRealDirectoryPath(candidate, filesystemAccess);
        return filesystemAccess.toRealPath(candidate);
      }
      if (kind != PrivateOutputDirectory.NoFollowEntryKind.MISSING) {
        throw PrivateOutputDirectoryFailures.pathCollision(
            candidate,
            "must not contain a symbolic link or non-directory entry in the output path");
      }
      @Nullable Path parent = filesystemAccess.parent(candidate);
      if (parent == null) {
        throw PrivateOutputDirectoryFailures.requirement(
            candidate, "must resolve beneath one existing real directory");
      }
      candidate = parent;
    }
  }

  static List<Path> missingDirectoryChain(
      Path plannedDirectory, PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    List<Path> missingDirectories = new ArrayList<>();
    Path candidate = plannedDirectory;
    while (true) {
      PrivateOutputDirectory.NoFollowEntryKind kind = filesystemAccess.noFollowEntryKind(candidate);
      if (kind == PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY) {
        requireLexicalRealDirectoryPath(candidate, filesystemAccess);
        Collections.reverse(missingDirectories);
        return List.copyOf(missingDirectories);
      }
      if (kind != PrivateOutputDirectory.NoFollowEntryKind.MISSING) {
        throw PrivateOutputDirectoryFailures.pathCollision(
            candidate,
            "must not contain a symbolic link or non-directory entry in the output path");
      }
      missingDirectories.add(candidate);
      @Nullable Path parent = filesystemAccess.parent(candidate);
      if (parent == null) {
        throw PrivateOutputDirectoryFailures.requirement(
            candidate, "must resolve beneath one existing real directory");
      }
      candidate = parent;
    }
  }

  static void requireLexicalRealDirectoryPath(
      Path directory, PrivateOutputDirectory.FilesystemAccess filesystemAccess) throws IOException {
    requireLexicalRealDirectoryPath(directory, filesystemAccess, System.getProperty("os.name", ""));
  }

  static void requireLexicalRealDirectoryPath(
      Path directory,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess,
      String operatingSystemName)
      throws IOException {
    Path checkedDirectory = directory.toAbsolutePath();
    @Nullable Path root = checkedDirectory.getRoot();
    if (root == null) {
      throw PrivateOutputDirectoryFailures.requirement(
          checkedDirectory, "must resolve to an absolute real directory path");
    }
    requireLexicalDirectoryComponent(root, filesystemAccess, operatingSystemName);
    Path component = root;
    for (Path name : checkedDirectory) {
      component = component.resolve(name);
      requireLexicalDirectoryComponent(component, filesystemAccess, operatingSystemName);
    }
  }

  private static void requireLexicalDirectoryComponent(
      Path component,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess,
      String operatingSystemName)
      throws IOException {
    PrivateOutputDirectory.NoFollowEntryKind kind = filesystemAccess.noFollowEntryKind(component);
    if (kind == PrivateOutputDirectory.NoFollowEntryKind.DIRECTORY) {
      return;
    }
    if (kind == PrivateOutputDirectory.NoFollowEntryKind.OTHER
        && isMacOsSystemAlias(component, operatingSystemName)) {
      // macOS publishes /tmp and /var as root-owned aliases for /private/*; continue no-follow
      // admission at the first caller-controlled descendant rather than making normal mktemp paths
      // unusable.
      return;
    }
    if (kind == PrivateOutputDirectory.NoFollowEntryKind.MISSING) {
      throw PrivateOutputDirectoryFailures.requirement(
          component, "must remain an existing real directory");
    }
    throw PrivateOutputDirectoryFailures.pathCollision(
        component, "must not contain a symbolic link or non-directory entry in the output path");
  }

  static boolean isMacOsSystemAlias(Path component, String operatingSystemName) {
    if (!Objects.requireNonNull(operatingSystemName, "operatingSystemName")
        .toLowerCase(java.util.Locale.ROOT)
        .contains("mac")) {
      return false;
    }
    Path normalized = component.toAbsolutePath().normalize();
    return normalized.equals(Path.of("/tmp")) || normalized.equals(Path.of("/var"));
  }
}

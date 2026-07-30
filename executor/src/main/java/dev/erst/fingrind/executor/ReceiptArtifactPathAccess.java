package dev.erst.fingrind.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Restricts receipt-artifact filesystem inspection to no-follow, canonical-path operations. */
interface ReceiptArtifactPathAccess {
  /** The production filesystem implementation for receipt publication and verification. */
  ReceiptArtifactPathAccess FILE_SYSTEM =
      new ReceiptArtifactPathAccess() {
        @Override
        public boolean isDirectoryNoFollow(Path path) {
          return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public BasicFileAttributes readBasicAttributesNoFollow(Path path) throws IOException {
          return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public Path toRealPath(Path path) throws IOException {
          return path.toRealPath();
        }
      };

  /** Returns whether the path is a directory without following a caller-controlled link. */
  boolean isDirectoryNoFollow(Path path);

  /** Reads file attributes without following a caller-controlled link. */
  BasicFileAttributes readBasicAttributesNoFollow(Path path) throws IOException;

  /**
   * Returns whether every lexical parent component is a real directory, without resolving a
   * caller-controlled link or normalizing away an earlier symbolic-link component.
   */
  default boolean hasOnlyRealDirectoryComponents(Path path) throws IOException {
    Path absolutePath = toAbsolutePath(path);
    Path parent = absolutePath.getParent();
    if (parent == null) {
      return false;
    }
    Path root = parent.getRoot();
    if (root == null || !readBasicAttributesNoFollow(root).isDirectory()) {
      return false;
    }
    Path componentPath = root;
    for (Path component : root.relativize(parent)) {
      componentPath = componentPath.resolve(component);
      if (!readBasicAttributesNoFollow(componentPath).isDirectory()) {
        return false;
      }
    }
    return true;
  }

  /** Resolves one caller path to the provider's absolute spelling before lexical admission. */
  default Path toAbsolutePath(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath();
  }

  /** Resolves a filesystem path to its canonical target after no-follow admission. */
  Path toRealPath(Path path) throws IOException;
}

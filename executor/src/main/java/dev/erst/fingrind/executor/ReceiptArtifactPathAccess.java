package dev.erst.fingrind.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

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

  /** Resolves a filesystem path to its canonical target after no-follow admission. */
  Path toRealPath(Path path) throws IOException;
}

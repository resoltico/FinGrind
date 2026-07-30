package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers fail-closed lexical ancestry admission for receipt artifacts. */
class ReceiptArtifactPathAccessTest {
  @TempDir Path temporaryDirectory;

  @Test
  void rejectsAPathWithoutAParentDirectory() throws IOException {
    Path filesystemRoot = Objects.requireNonNull(temporaryDirectory.getRoot(), "filesystem root");

    assertFalse(
        ReceiptArtifactPathAccess.FILE_SYSTEM.hasOnlyRealDirectoryComponents(filesystemRoot));
  }

  @Test
  void rejectsAProviderAbsolutePathWithoutARoot() throws IOException {
    ReceiptArtifactPathAccess pathAccess =
        new ReceiptArtifactPathAccess() {
          @Override
          public boolean isDirectoryNoFollow(Path path) {
            return false;
          }

          @Override
          public BasicFileAttributes readBasicAttributesNoFollow(Path path) {
            throw new AssertionError("Rootless paths must fail before attributes are read.");
          }

          @Override
          public Path toAbsolutePath(Path path) {
            return Path.of("rootless-parent").resolve("receipt.fgar");
          }

          @Override
          public Path toRealPath(Path path) {
            return path;
          }
        };

    assertFalse(
        pathAccess.hasOnlyRealDirectoryComponents(temporaryDirectory.resolve("receipt.fgar")));
  }

  @Test
  void rejectsAnAncestryWhoseRootCannotBeProvedToBeADirectory() throws IOException {
    Path regularFile = temporaryDirectory.resolve("not-a-directory");
    Files.writeString(regularFile, "ordinary caller file");
    BasicFileAttributes regularFileAttributes =
        Files.readAttributes(regularFile, BasicFileAttributes.class);
    ReceiptArtifactPathAccess pathAccess =
        new ReceiptArtifactPathAccess() {
          @Override
          public boolean isDirectoryNoFollow(Path path) {
            return false;
          }

          @Override
          public BasicFileAttributes readBasicAttributesNoFollow(Path path) {
            return regularFileAttributes;
          }

          @Override
          public Path toRealPath(Path path) {
            return path;
          }
        };

    assertFalse(
        pathAccess.hasOnlyRealDirectoryComponents(
            temporaryDirectory.resolve("reports").resolve("receipt.fgar")));
  }
}

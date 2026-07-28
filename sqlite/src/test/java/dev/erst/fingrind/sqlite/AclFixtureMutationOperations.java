package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Directory and entry-mutation helpers for the ACL fixture filesystem provider. */
final class AclFixtureMutationOperations {
  private AclFixtureMutationOperations() {}

  static DirectoryStream<Path> newDirectoryStream(AclFixtureFileSystem fileSystem, Path dir)
      throws IOException {
    AclFixturePath testPath = AclFixtureChannelOperations.fixturePath(dir);
    IOException newDirectoryStreamFailure = testPath.newDirectoryStreamFailure();
    if (newDirectoryStreamFailure != null) {
      throw newDirectoryStreamFailure;
    }
    return new DirectoryStream<>() {
      @Override
      public Iterator<Path> iterator() {
        List<Path> children =
            fileSystem.registeredPaths().values().stream()
                .filter(candidate -> candidate.exists)
                .filter(candidate -> Objects.equals(candidate.getParent(), testPath))
                .map(Path.class::cast)
                .sorted((left, right) -> left.toString().compareTo(right.toString()))
                .toList();
        return children.iterator();
      }

      @Override
      public void close() throws IOException {
        IOException closeFailure = testPath.directoryStreamCloseFailure();
        if (closeFailure != null) {
          throw closeFailure;
        }
      }
    };
  }

  static void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    AclFixturePath testPath = AclFixtureChannelOperations.fixturePath(dir);
    UnsupportedOperationException unsupported = testPath.createDirectoryUnsupported();
    if (unsupported != null) {
      throw unsupported;
    }
    IOException createFailure = testPath.createDirectoryFailure();
    if (createFailure != null) {
      throw createFailure;
    }
    testPath.exists = true;
    testPath.regularFile = false;
    testPath.posixPermissions = AclFixtureChannelOperations.posixPermissions(attrs);
  }

  static boolean deleteIfExists(Path path) throws IOException {
    AclFixturePath testPath = AclFixtureChannelOperations.fixturePath(path);
    IOException deleteFailure = testPath.deleteIfExistsFailure();
    if (deleteFailure != null) {
      throw deleteFailure;
    }
    if (!testPath.exists) {
      return false;
    }
    if (testPath.preserveExistingEntryOnDeleteIfExistsValue()) {
      return true;
    }
    testPath.exists = false;
    return true;
  }

  static void copy(Path source, Path target, CopyOption... options) throws IOException {
    AclFixturePath sourcePath = AclFixtureChannelOperations.fixturePath(source);
    if (!sourcePath.exists) {
      throw new NoSuchFileException(source.toString());
    }
    AclFixturePath targetPath = AclFixtureChannelOperations.fixturePath(target);
    boolean replaceExisting =
        java.util.Arrays.stream(options)
            .anyMatch(option -> option == StandardCopyOption.REPLACE_EXISTING);
    if (targetPath.exists && !replaceExisting) {
      throw new FileAlreadyExistsException(target.toString());
    }
    copyEntryState(sourcePath, targetPath);
  }

  static void move(Path source, Path target, CopyOption... options) throws IOException {
    AclFixturePath sourcePath = AclFixtureChannelOperations.fixturePath(source);
    IOException moveFailure = sourcePath.moveFailure();
    if (moveFailure != null) {
      throw moveFailure;
    }
    if (!sourcePath.exists) {
      throw new NoSuchFileException(source.toString());
    }
    AclFixturePath targetPath = AclFixtureChannelOperations.fixturePath(target);
    boolean replaceExisting =
        java.util.Arrays.stream(options)
            .anyMatch(option -> option == StandardCopyOption.REPLACE_EXISTING);
    if (targetPath.exists && !replaceExisting) {
      throw new FileAlreadyExistsException(target.toString());
    }
    copyEntryState(sourcePath, targetPath);
    sourcePath.exists = false;
  }

  private static void copyEntryState(AclFixturePath sourcePath, AclFixturePath targetPath) {
    targetPath.exists = true;
    targetPath.regularFile = sourcePath.regularFile;
    targetPath.posixPermissions = sourcePath.posixPermissions;
    targetPath.aclView = sourcePath.aclView;
    targetPath.overrideAclView = sourcePath.overrideAclView;
  }
}

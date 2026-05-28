package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.AccessMode;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Attribute and capability helpers for the ACL fixture filesystem provider. */
final class AclFixtureAttributeOperations {
  private AclFixtureAttributeOperations() {}

  static void checkAccess(Path path, AccessMode... modes) throws IOException {
    if (!AclFixtureChannelOperations.fixturePath(path).exists) {
      throw new NoSuchFileException(path.toString());
    }
  }

  static <V extends FileAttributeView> @Nullable V getFileAttributeView(Path path, Class<V> type) {
    AclFixturePath fixturePath = AclFixtureChannelOperations.fixturePath(path);
    if (type == AclFileAttributeView.class || type == FileOwnerAttributeView.class) {
      return type.cast(
          fixturePath.overrideAclView != null ? fixturePath.overrideAclView : fixturePath.aclView);
    }
    if (type == PosixFileAttributeView.class) {
      return type.cast(new AclFixturePosixView(fixturePath));
    }
    return null;
  }

  static <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type)
      throws IOException {
    AclFixturePath fixturePath = AclFixtureChannelOperations.fixturePath(path);
    if (!fixturePath.exists) {
      throw new NoSuchFileException(path.toString());
    }
    if (type == BasicFileAttributes.class) {
      return type.cast(new AclFixtureBasicFileAttributes(fixturePath));
    }
    if (type == PosixFileAttributes.class) {
      return type.cast(new AclFixturePosixFileAttributes(fixturePath));
    }
    throw new UnsupportedOperationException(
        "only basic and POSIX attributes are used by these tests");
  }

  static Map<String, Object> readAttributes(Path path, String attributes) throws IOException {
    AclFixturePath fixturePath = AclFixtureChannelOperations.fixturePath(path);
    if (!fixturePath.exists) {
      throw new NoSuchFileException(path.toString());
    }
    return Map.of(
        "isRegularFile", fixturePath.regularFile, "isDirectory", !fixturePath.regularFile);
  }
}

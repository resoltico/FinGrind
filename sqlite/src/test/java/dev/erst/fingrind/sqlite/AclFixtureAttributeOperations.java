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
import java.nio.file.attribute.PosixFilePermission;
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
    IOException readAttributesFailure = fixturePath.readAttributesFailure();
    if (readAttributesFailure != null) {
      throw readAttributesFailure;
    }
    if (!fixturePath.exists) {
      throw new NoSuchFileException(path.toString());
    }
    if (type == BasicFileAttributes.class) {
      return type.cast(new AclFixtureBasicFileAttributes(fixturePath));
    }
    if (type == PosixFileAttributes.class) {
      IOException posixReadAttributesFailure = fixturePath.posixReadAttributesFailure();
      if (posixReadAttributesFailure != null) {
        throw posixReadAttributesFailure;
      }
      return type.cast(new AclFixturePosixFileAttributes(fixturePath));
    }
    throw new UnsupportedOperationException(
        "only basic and POSIX attributes are used by these tests");
  }

  static Map<String, Object> readAttributes(Path path, String attributes) throws IOException {
    AclFixturePath fixturePath = AclFixtureChannelOperations.fixturePath(path);
    IOException readAttributesFailure = fixturePath.readAttributesFailure();
    if (readAttributesFailure != null) {
      throw readAttributesFailure;
    }
    if (!fixturePath.exists) {
      throw new NoSuchFileException(path.toString());
    }
    return switch (attributes) {
      case "unix:mode" -> Map.of("mode", posixMode(fixturePath));
      case "unix:uid" -> Map.of("uid", 1_000L);
      default ->
          Map.of(
              "isRegularFile", fixturePath.regularFile,
              "isDirectory", !fixturePath.regularFile);
    };
  }

  private static int posixMode(AclFixturePath path) {
    int mode = 0;
    for (PosixFilePermission permission : path.posixPermissions) {
      mode |=
          switch (permission) {
            case OWNER_READ -> 0x100;
            case OWNER_WRITE -> 0x80;
            case OWNER_EXECUTE -> 0x40;
            case GROUP_READ -> 0x20;
            case GROUP_WRITE -> 0x10;
            case GROUP_EXECUTE -> 0x08;
            case OTHERS_READ -> 0x04;
            case OTHERS_WRITE -> 0x02;
            case OTHERS_EXECUTE -> 0x01;
          };
    }
    return mode;
  }
}

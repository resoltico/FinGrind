package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** Channel-opening helpers for the ACL fixture filesystem provider. */
final class AclFixtureChannelOperations {
  private AclFixtureChannelOperations() {}

  static SeekableByteChannel newByteChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    AclFixturePath testPath = fixturePath(path);
    UnsupportedOperationException unsupported = testPath.channelPlan().newByteChannelUnsupported();
    if (unsupported != null) {
      throw unsupported;
    }
    IOException newByteChannelFailure = testPath.channelPlan().newByteChannelFailure();
    if (newByteChannelFailure != null) {
      throw newByteChannelFailure;
    }
    boolean createNew = options.contains(StandardOpenOption.CREATE_NEW);
    boolean createIfMissing = createNew || options.contains(StandardOpenOption.CREATE);
    if (createNew && testPath.exists) {
      throw new FileAlreadyExistsException(testPath.toString());
    }
    if (!testPath.exists) {
      if (!createIfMissing) {
        throw new NoSuchFileException(testPath.toString());
      }
      testPath.exists = true;
      testPath.regularFile = true;
      testPath.posixPermissions = posixPermissions(attrs);
    }
    return new AclFixtureSeekableByteChannel(testPath);
  }

  static FileChannel newFileChannel(
      Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    AclFixturePath testPath = fixturePath(path);
    UnsupportedOperationException unsupported = testPath.channelPlan().newFileChannelUnsupported();
    if (unsupported != null) {
      throw unsupported;
    }
    SeekableByteChannel byteChannel = newByteChannel(testPath, options, attrs);
    return new AclFixtureFileChannel(testPath, (AclFixtureSeekableByteChannel) byteChannel);
  }

  static Set<PosixFilePermission> posixPermissions(FileAttribute<?>... attrs) {
    for (FileAttribute<?> attribute : attrs) {
      if ("posix:permissions".equals(attribute.name())) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        for (Object permission : (Set<?>) attribute.value()) {
          permissions.add((PosixFilePermission) permission);
        }
        return Set.copyOf(permissions);
      }
    }
    return Set.of();
  }

  static AclFixturePath fixturePath(Path path) {
    return (AclFixturePath) path;
  }
}

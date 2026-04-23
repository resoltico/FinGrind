package dev.erst.fingrind.sqlite;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

/** Minimal basic attributes for the test ACL filesystem. */
record AclFixtureBasicFileAttributes(AclFixturePath path) implements BasicFileAttributes {
  @Override
  public FileTime lastModifiedTime() {
    return FileTime.fromMillis(0);
  }

  @Override
  public FileTime lastAccessTime() {
    return FileTime.fromMillis(0);
  }

  @Override
  public FileTime creationTime() {
    return FileTime.fromMillis(0);
  }

  @Override
  public boolean isRegularFile() {
    return path.regularFile;
  }

  @Override
  public boolean isDirectory() {
    return !path.regularFile;
  }

  @Override
  public boolean isSymbolicLink() {
    return false;
  }

  @Override
  public boolean isOther() {
    return false;
  }

  @Override
  public long size() {
    return 0;
  }

  @Override
  public Object fileKey() {
    return path;
  }
}

/** Minimal POSIX attributes for Files.getPosixFilePermissions on the test filesystem. */
record AclFixturePosixFileAttributes(AclFixturePath path) implements PosixFileAttributes {
  @Override
  public FileTime lastModifiedTime() {
    return FileTime.fromMillis(0);
  }

  @Override
  public FileTime lastAccessTime() {
    return FileTime.fromMillis(0);
  }

  @Override
  public FileTime creationTime() {
    return FileTime.fromMillis(0);
  }

  @Override
  public boolean isRegularFile() {
    return path.regularFile;
  }

  @Override
  public boolean isDirectory() {
    return !path.regularFile;
  }

  @Override
  public boolean isSymbolicLink() {
    return false;
  }

  @Override
  public boolean isOther() {
    return false;
  }

  @Override
  public long size() {
    return 0;
  }

  @Override
  public Object fileKey() {
    return path;
  }

  @Override
  public UserPrincipal owner() {
    return path.getFileSystem().owner;
  }

  @Override
  public GroupPrincipal group() {
    return path.getFileSystem().group;
  }

  @Override
  public Set<PosixFilePermission> permissions() {
    return path.posixPermissions;
  }
}

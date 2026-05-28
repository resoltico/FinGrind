package dev.erst.fingrind.sqlite;

import java.nio.file.FileStore;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Minimal file-store descriptor for capability checks against the fixture filesystem. */
final class AclFixtureFileStore extends FileStore {
  private final Set<String> views;

  AclFixtureFileStore(Set<String> views) {
    this.views = Set.copyOf(views);
  }

  @Override
  public String name() {
    return "fingrind-test-acl";
  }

  @Override
  public String type() {
    return "fixture";
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public long getTotalSpace() {
    return 0L;
  }

  @Override
  public long getUsableSpace() {
    return 0L;
  }

  @Override
  public long getUnallocatedSpace() {
    return 0L;
  }

  @Override
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
    if (type == PosixFileAttributeView.class) {
      return views.contains("posix");
    }
    if (type == AclFileAttributeView.class || type == FileOwnerAttributeView.class) {
      return views.contains("acl");
    }
    return false;
  }

  @Override
  public boolean supportsFileAttributeView(String name) {
    return views.contains(name);
  }

  @Override
  public <V extends FileStoreAttributeView> @Nullable V getFileStoreAttributeView(Class<V> type) {
    return null;
  }

  @Override
  public Object getAttribute(String attribute) {
    throw new UnsupportedOperationException(
        "file-store attributes are not used by this test filesystem");
  }
}

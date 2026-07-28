package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Minimal ACL view for exercising owner-only ACL writes and reads. */
public final class AclFixtureView implements AclFileAttributeView {
  private UserPrincipal owner;
  private List<AclEntry> acl = List.of();

  AclFixtureView(UserPrincipal owner) {
    this.owner = owner;
  }

  @Override
  public String name() {
    return "acl";
  }

  @Override
  public List<AclEntry> getAcl() {
    return acl;
  }

  @Override
  public void setAcl(List<AclEntry> acl) {
    this.acl = List.copyOf(acl);
  }

  @Override
  public UserPrincipal getOwner() {
    return owner;
  }

  @Override
  public void setOwner(UserPrincipal owner) {
    this.owner = Objects.requireNonNull(owner, "owner");
  }
}

/** Minimal POSIX view for exercising owner-only mode writes and reads on every host OS. */
final class AclFixturePosixView implements PosixFileAttributeView {
  private final AclFixturePath path;

  AclFixturePosixView(AclFixturePath path) {
    this.path = path;
  }

  @Override
  public String name() {
    return "posix";
  }

  @Override
  public PosixFileAttributes readAttributes() throws IOException {
    IOException failure = path.posixReadAttributesFailure();
    if (failure != null) {
      throw failure;
    }
    return new AclFixturePosixFileAttributes(path);
  }

  @Override
  public void setPermissions(Set<java.nio.file.attribute.PosixFilePermission> permissions) {
    path.posixPermissions = Set.copyOf(permissions);
  }

  @Override
  public void setGroup(GroupPrincipal group) {}

  @Override
  public UserPrincipal getOwner() {
    return path.getFileSystem().owner;
  }

  @Override
  public void setOwner(UserPrincipal owner) {}

  @Override
  public void setTimes(
      java.nio.file.attribute.FileTime lastModifiedTime,
      java.nio.file.attribute.FileTime lastAccessTime,
      java.nio.file.attribute.FileTime createTime) {}
}

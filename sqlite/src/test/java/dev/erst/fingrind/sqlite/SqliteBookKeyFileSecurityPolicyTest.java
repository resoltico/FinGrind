package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Direct coverage for the shared owner-only book-key filesystem policy helpers. */
class SqliteBookKeyFileSecurityPolicyTest {
  @Test
  void requireOwnerOnlyPosixPermissions_acceptsOwnerOnlyModesAndRejectsSharedOrUnreadableModes() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath path = fileSystem.path("\\keys\\book.book-key");

      ContractDecision<java.nio.file.Path> accepted =
          SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyPosixPermissions(
              path,
              Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
              PosixFilePermission.OWNER_READ,
              Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
              "owner-readable: ",
              "owner-only: ");
      assertEquals(path, accepted.requireAccepted());

      ContractDecision<java.nio.file.Path> ownerUnreadable =
          SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyPosixPermissions(
              path,
              Set.of(PosixFilePermission.OWNER_WRITE),
              PosixFilePermission.OWNER_READ,
              Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
              "owner-readable: ",
              "owner-only: ");
      assertTrue(ownerUnreadable.requireRejected().message().contains("owner-readable"));

      ContractDecision<java.nio.file.Path> groupReadable =
          SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyPosixPermissions(
              path,
              Set.of(
                  PosixFilePermission.OWNER_READ,
                  PosixFilePermission.OWNER_WRITE,
                  PosixFilePermission.GROUP_READ),
              PosixFilePermission.OWNER_READ,
              Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
              "owner-readable: ",
              "owner-only: ");
      assertTrue(groupReadable.requireRejected().message().contains("owner-only"));
    }
  }

  @Test
  void requireOwnerOnlyAcl_acceptsOwnerOnlyEntriesAndRejectsMissingOwnerOrSharedAccess() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath path = fileSystem.path("\\keys\\book.book-key");
      SqliteAclKeyFileSecurity acceptedSecurity =
          new SqliteAclKeyFileSecurity(
              fileSystem.owner(),
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.READ_DATA, AclEntryPermission.READ_ACL)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.DENY)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.READ_DATA)
                      .build()));
      ContractDecision<java.nio.file.Path> accepted =
          SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyAcl(
              path,
              acceptedSecurity,
              Set.of(AclEntryPermission.READ_DATA),
              Set.of(AclEntryPermission.READ_DATA),
              "owner-read: ",
              "owner-only-acl: ");
      assertEquals(path, accepted.requireAccepted());

      SqliteAclKeyFileSecurity ownerMissingRead =
          new SqliteAclKeyFileSecurity(
              fileSystem.owner(),
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.READ_ACL)
                      .build()));
      ContractDecision<java.nio.file.Path> ownerMissing =
          SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyAcl(
              path,
              ownerMissingRead,
              Set.of(AclEntryPermission.READ_DATA),
              Set.of(AclEntryPermission.READ_DATA),
              "owner-read: ",
              "owner-only-acl: ");
      assertTrue(ownerMissing.requireRejected().message().contains("owner-read"));

      SqliteAclKeyFileSecurity sharedRead =
          new SqliteAclKeyFileSecurity(
              fileSystem.owner(),
              List.of(
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner())
                      .setPermissions(AclEntryPermission.READ_DATA)
                      .build(),
                  AclEntry.newBuilder()
                      .setType(AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.group())
                      .setPermissions(AclEntryPermission.READ_DATA)
                      .build()));
      ContractDecision<java.nio.file.Path> sharedFailure =
          SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyAcl(
              path,
              sharedRead,
              Set.of(AclEntryPermission.READ_DATA),
              Set.of(AclEntryPermission.READ_DATA),
              "owner-read: ",
              "owner-only-acl: ");
      assertTrue(sharedFailure.requireRejected().message().contains("owner-only-acl"));
      assertFalse(sharedFailure.requireRejected().message().contains(fileSystem.group().getName()));
    }
  }

  @Test
  void applyOwnerOnlyAcl_replacesAclWithOneOwnerAllowEntry() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath path = fileSystem.path("\\keys\\generated.book-key");
      path.exists = true;
      path.regularFile = true;
      AclFixtureView aclView = new AclFixtureView(fileSystem.owner());
      aclView.setAcl(
          List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(fileSystem.group())
                  .setPermissions(AclEntryPermission.READ_DATA)
                  .build()));
      path.aclView = aclView;

      SqliteBookKeyFileSecurityPolicy.applyOwnerOnlyAcl(
          path, Set.of(AclEntryPermission.READ_DATA, AclEntryPermission.DELETE));

      List<AclEntry> acl = Objects.requireNonNull(path.aclViewValue()).getAcl();
      assertEquals(1, acl.size());
      assertEquals(fileSystem.owner(), acl.getFirst().principal());
      assertEquals(AclEntryType.ALLOW, acl.getFirst().type());
      assertTrue(acl.getFirst().permissions().contains(AclEntryPermission.READ_DATA));
      assertTrue(acl.getFirst().permissions().contains(AclEntryPermission.DELETE));
      assertInstanceOf(AclEntry.class, acl.getFirst());
    }
  }
}

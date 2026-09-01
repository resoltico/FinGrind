package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

/** Key-file artifact validation. */
final class SqliteBookKeyFileArtifactSecurity {
  private static final Set<PosixFilePermission> POSIX_KEY_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<AclEntryPermission> ACL_READ_PERMISSIONS =
      Set.of(AclEntryPermission.READ_DATA);
  private static final Set<AclEntryPermission> ACL_SECRET_ACCESS_PERMISSIONS =
      Set.of(
          AclEntryPermission.READ_DATA,
          AclEntryPermission.WRITE_DATA,
          AclEntryPermission.APPEND_DATA,
          AclEntryPermission.EXECUTE,
          AclEntryPermission.DELETE_CHILD,
          AclEntryPermission.READ_NAMED_ATTRS,
          AclEntryPermission.WRITE_NAMED_ATTRS,
          AclEntryPermission.READ_ATTRIBUTES,
          AclEntryPermission.WRITE_ATTRIBUTES,
          AclEntryPermission.DELETE,
          AclEntryPermission.READ_ACL,
          AclEntryPermission.WRITE_ACL,
          AclEntryPermission.WRITE_OWNER,
          AclEntryPermission.SYNCHRONIZE);

  private SqliteBookKeyFileArtifactSecurity() {}

  static ContractDecision<Path> requireSecureKeyFile(Path bookKeyFilePath) {
    return requireSecureKeyFile(
        bookKeyFilePath, SqliteBookKeyFileDirectorySecurity::inspectSecurity);
  }

  static ContractDecision<Path> requireSecureKeyFile(
      Path bookKeyFilePath, SqliteKeyFileSecurityInspector securityInspector) {
    Objects.requireNonNull(securityInspector, "securityInspector");
    try {
      if (Files.notExists(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        return ContractDecision.rejected(
            SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                bookKeyFilePath, "The FinGrind book key file does not exist."));
      }
      if (!Files.isRegularFile(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        return ContractDecision.rejected(
            SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                bookKeyFilePath, "The FinGrind book key file must be a regular non-symlink file."));
      }
      Path parentDirectory =
          SqliteBookKeyFileSecuritySupport.requireKeyFileParentDirectory(bookKeyFilePath);
      ContractDecision<Path> parentDirectoryDecision =
          SqliteBookKeyFileDirectorySecurity.requireSecureParentDirectorySecurity(
              parentDirectory, securityInspector.inspect(parentDirectory));
      return switch (parentDirectoryDecision) {
        case ContractDecision.Accepted<Path> _ -> {
          SqlitePrivateOutputDirectoryAdmission.requireOwnerOnlyNonMutableAncestry(
              bookKeyFilePath, parentDirectory);
          yield requireSecureSecurity(bookKeyFilePath, securityInspector.inspect(bookKeyFilePath));
        }
        case ContractDecision.Rejected<Path>(ContractFailure failure) ->
            ContractDecision.rejected(failure);
      };
    } catch (SqliteCallerPathContractException exception) {
      return ContractDecision.rejected(SqliteCallerPathFailureMapper.invalidBookKeyFile(exception));
    } catch (UnsupportedOperationException exception) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.unsupportedSecureFilesystem(bookKeyFilePath, exception));
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failureAt(
              bookKeyFilePath,
              "Failed to inspect the FinGrind book key file permissions.",
              "Inspect the selected book key file path, permissions, and filesystem accessibility, then rerun the command.",
              null));
    }
  }

  private static ContractDecision<Path> requireSecureSecurity(
      Path bookKeyFilePath, SqliteKeyFileSecurity security) {
    return switch (Objects.requireNonNull(security, "security")) {
      case SqlitePosixKeyFileSecurity posixSecurity ->
          requireSecurePosixPermissions(bookKeyFilePath, posixSecurity.permissions());
      case SqliteAclKeyFileSecurity aclSecurity -> requireSecureAcl(bookKeyFilePath, aclSecurity);
    };
  }

  private static ContractDecision<Path> requireSecurePosixPermissions(
      Path bookKeyFilePath, Set<PosixFilePermission> permissions) {
    return SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyPosixPermissions(
        bookKeyFilePath,
        permissions,
        PosixFilePermission.OWNER_READ,
        POSIX_KEY_FILE_PERMISSIONS,
        "The FinGrind book key file must be owner-readable.",
        "The FinGrind book key file must use owner-only permissions (0400 or 0600).");
  }

  private static ContractDecision<Path> requireSecureAcl(
      Path bookKeyFilePath, SqliteAclKeyFileSecurity security) {
    return SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyAcl(
        bookKeyFilePath,
        security,
        ACL_READ_PERMISSIONS,
        ACL_SECRET_ACCESS_PERMISSIONS,
        "The FinGrind book key file ACL must grant the file owner read access.",
        "The FinGrind book key file ACL must grant secret access only to the file owner.");
  }
}

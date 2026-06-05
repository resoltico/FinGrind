package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.SqliteRuntimeProvenance;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Shared filesystem and helper fixtures for managed SQLite identity tests. */
class SqliteManagedLibraryIdentityTestSupport {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  protected Path writeLibrary(String fileName, String contents) throws IOException {
    Path libraryPath = tempDirectory.resolve(fileName);
    Files.writeString(libraryPath, contents, StandardCharsets.UTF_8);
    return libraryPath;
  }

  protected Path copyHostManagedLibrary() throws IOException {
    Path sourceLibraryPath = hostManagedLibraryPath();
    Path copiedLibraryPath = tempDirectory.resolve(sourceLibraryPath.getFileName().toString());
    Files.copy(sourceLibraryPath, copiedLibraryPath);
    return copiedLibraryPath;
  }

  protected void writeSiblingChecksum(Path libraryPath) throws IOException {
    writeSiblingChecksum(libraryPath, libraryPath);
  }

  protected void writeSiblingChecksum(Path libraryPath, Path checksumSourceLibraryPath)
      throws IOException {
    Files.writeString(
        SqliteManagedLibraryIdentity.checksumPath(libraryPath),
        sha256Line(checksumSourceLibraryPath, libraryPath.getFileName().toString()),
        StandardCharsets.UTF_8);
  }

  protected static String sha256Line(Path libraryPath, String declaredFileName) {
    return SqliteManagedLibraryIdentity.actualSha256(libraryPath) + "  " + declaredFileName + "\n";
  }

  protected static String hostManagedLibraryFileName() {
    return hostManagedLibraryPath().getFileName().toString();
  }

  protected static Path hostManagedLibraryPath() {
    SqliteLibraryTarget libraryTarget;
    try {
      libraryTarget = SqliteManagedLibraryTargetLocator.configuredLibraryTarget(null);
    } catch (IllegalStateException exception) {
      throw new IllegalStateException(
          "Missing source-checkout managed SQLite runtime for managed-library identity tests.",
          exception);
    }
    if (libraryTarget.provenance() != SqliteRuntimeProvenance.SOURCE_CHECKOUT_MANAGED) {
      throw new IllegalStateException(
          "Managed-library identity tests require the source-checkout managed SQLite runtime.");
    }
    return Path.of(libraryTarget.lookupTarget());
  }

  protected static AclFileAttributeView throwingAclView(String message) {
    return new AclFileAttributeView() {
      @Override
      public String name() {
        return "acl";
      }

      @Override
      public List<AclEntry> getAcl() {
        return List.of();
      }

      @Override
      public void setAcl(List<AclEntry> acl) throws IOException {
        throw new IOException(message);
      }

      @Override
      public UserPrincipal getOwner() throws IOException {
        throw new IOException(message);
      }

      @Override
      public void setOwner(UserPrincipal ownerPrincipal) {
        throw new UnsupportedOperationException();
      }
    };
  }
}

package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Exact platform admission for private files beneath an already private output directory. */
final class PrivateOutputFileAdmission {
  private PrivateOutputFileAdmission() {}

  static PrivateOutputFile.OpenedFile createNew(Path file, PrivateOutputFile.Operations operations)
      throws IOException {
    Path checkedFile = normalize(file);
    PrivateOutputFile.Operations checkedOperations = Objects.requireNonNull(operations);
    requireSecureParent(checkedFile, checkedOperations);
    if (checkedOperations.supportsPosix(checkedFile)) {
      return checkedOperations.createNewPosix(checkedFile);
    }
    if (checkedOperations.supportsAcl(checkedFile) && checkedOperations.isWindows()) {
      return checkedOperations.createNewWindows(checkedFile);
    }
    throw unsupportedCreation(checkedFile);
  }

  static PrivateOutputFile.OpenedFile openExisting(
      Path file, PrivateOutputFile.Access access, PrivateOutputFile.Operations operations)
      throws IOException {
    Path checkedFile = normalize(file);
    PrivateOutputFile.Access checkedAccess = Objects.requireNonNull(access);
    PrivateOutputFile.Operations checkedOperations = Objects.requireNonNull(operations);
    requireSecureParent(checkedFile, checkedOperations);
    if (checkedOperations.supportsPosix(checkedFile)) {
      return checkedOperations.openExistingPosix(checkedFile, checkedAccess);
    }
    if (checkedOperations.supportsAcl(checkedFile) && checkedOperations.isWindows()) {
      return checkedOperations.openExistingWindows(checkedFile, checkedAccess);
    }
    throw unsupportedCreation(checkedFile);
  }

  static void requireExistingOwnerOnly(
      Path file, PrivateOutputFile.Access access, PrivateOutputFile.Operations operations)
      throws IOException {
    closeAdmissionProof(openExisting(file, access, operations));
  }

  private static Path normalize(Path file) throws PrivateOutputFile.OwnerOnlyFileViolation {
    Path checkedFile = Objects.requireNonNull(file).toAbsolutePath().normalize();
    if (checkedFile.getParent() == null) {
      throw new PrivateOutputFile.OwnerOnlyFileViolation(
          checkedFile,
          PrivateOutputFile.ViolationKind.MISSING_PARENT,
          "must resolve beneath one parent directory");
    }
    return checkedFile;
  }

  private static void closeAdmissionProof(PrivateOutputFile.OpenedFile opened) throws IOException {
    Objects.requireNonNull(opened).close();
  }

  private static PrivateOutputFile.OwnerOnlyFileViolation unsupportedCreation(Path file) {
    return new PrivateOutputFile.OwnerOnlyFileViolation(
        file,
        PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED,
        "requires POSIX owner-only permissions or a Windows owner-only ACL");
  }

  private static void requireSecureParent(Path file, PrivateOutputFile.Operations operations)
      throws IOException {
    try {
      operations.requireSecureParent(file);
    } catch (PrivateOutputDirectory.Violation violation) {
      throw new PrivateOutputFile.OwnerOnlyFileViolation(
          file,
          PrivateOutputFile.ViolationKind.PARENT_OWNER_ONLY_REQUIRED,
          "must resolve beneath an existing owner-only parent directory",
          violation);
    }
  }
}

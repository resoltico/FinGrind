package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Canonical parent-plus-primitive identity for one retained publication-capability witness. */
record SqlitePublicationCapabilityWitnessKey(
    Path parentDirectory,
    String parentFingerprint,
    SqlitePublicationCapabilityWitness.PrimitiveKind primitiveKind)
    implements Comparable<SqlitePublicationCapabilityWitnessKey> {
  private static final String PROTOCOL = "FinGrind-publication-capability-v2";
  private static final String BASE_PREFIX = ".fingrind-publication-capability-v2-";

  SqlitePublicationCapabilityWitnessKey {
    Objects.requireNonNull(parentDirectory, "parentDirectory");
    Objects.requireNonNull(parentFingerprint, "parentFingerprint");
    Objects.requireNonNull(primitiveKind, "primitiveKind");
  }

  static SqlitePublicationCapabilityWitnessKey forTarget(
      Path targetPath, SqlitePublicationCapabilityWitness.PrimitiveKind primitiveKind)
      throws IOException {
    Path checkedTarget =
        Objects.requireNonNull(targetPath, "targetPath").toAbsolutePath().normalize();
    Path parent = Objects.requireNonNull(checkedTarget.getParent(), "targetPath parent");
    SqliteBookFileSecurity.requireExistingSecureParentDirectory(checkedTarget);
    Path canonicalParent = parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
    return new SqlitePublicationCapabilityWitnessKey(
        canonicalParent,
        SqliteCoordinationControlProtocol.canonicalDirectoryBinding(canonicalParent),
        Objects.requireNonNull(primitiveKind, "primitiveKind"));
  }

  Path controlPath() {
    return parentDirectory.resolve(baseName() + ".control");
  }

  Path statePath(String state) {
    return parentDirectory.resolve(baseName() + "." + state);
  }

  byte[] magic(String state) {
    return SqliteCoordinationControlProtocol.magic(
        PROTOCOL + "-" + primitiveKind.token() + "-" + state, parentFingerprint);
  }

  @Override
  public int compareTo(SqlitePublicationCapabilityWitnessKey other) {
    SqlitePublicationCapabilityWitnessKey checkedOther = Objects.requireNonNull(other, "other");
    int parentOrder = parentFingerprint.compareTo(checkedOther.parentFingerprint);
    return parentOrder != 0
        ? parentOrder
        : primitiveKind.token().compareTo(checkedOther.primitiveKind.token());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SqlitePublicationCapabilityWitnessKey witnessKey
        && parentFingerprint.equals(witnessKey.parentFingerprint)
        && primitiveKind == witnessKey.primitiveKind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(parentFingerprint, primitiveKind);
  }

  private String baseName() {
    return BASE_PREFIX
        + SqliteCoordinationControlProtocol.sha256Hex(
            parentFingerprint + "\u0000" + primitiveKind.token());
  }
}

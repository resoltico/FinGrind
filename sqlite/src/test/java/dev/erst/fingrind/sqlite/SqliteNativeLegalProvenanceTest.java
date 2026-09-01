package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Guards the native runtime's SQLite3MC license and embedded-notice provenance. */
class SqliteNativeLegalProvenanceTest {
  @Test
  void sqlite3mcProjectAndEmbeddedImplementationNoticesMatchVendored251Source() throws Exception {
    Path repositoryRoot = repositoryRoot();
    Path projectLicense = repositoryRoot.resolve("LICENSE-SQLITE3MULTIPLECIPHERS");
    assertEquals(
        "39205ec18e0f25f56c62d3bda9768e7509b63a74ba58beb7442903dfc81c247a", sha256(projectLicense));

    String amalgamation =
        Files.readString(
            repositoryRoot.resolve(
                "third_party/sqlite/sqlite3mc-amalgamation-2.5.1-sqlite-3530400/sqlite3mc_amalgamation.c"));
    String distributedNotices =
        Files.readString(repositoryRoot.resolve("LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY"));
    for (String requiredNotice :
        new String[] {
          "Copyright (C) 2005, 2007 Olivier Gay",
          "Copyright:   (c) 2023-2024 Frank Denis",
          "Daniel Dinu, Dmitry Khovratovich, Jean-Philippe Aumasson, and Samuel Neves",
          "Written in 2015 by Joseph Birr-Pixton",
          "Copyright (c) 2015 Thomas Pornin"
        }) {
      assertTrue(
          amalgamation.contains(requiredNotice), "vendored source omitted " + requiredNotice);
      assertTrue(
          distributedNotices.contains(requiredNotice.replace("Copyright:   ", "Copyright ")),
          "distributed native notice payload omitted " + requiredNotice);
    }
    assertTrue(distributedNotices.contains("LICENSE-CC0-1.0"));
    assertTrue(distributedNotices.contains("LICENSE-APACHE-2.0"));
  }

  private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int count = input.read(buffer);
      while (count != -1) {
        digest.update(buffer, 0, count);
        count = input.read(buffer);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
          && Files.isDirectory(current.resolve("third_party/sqlite"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("could not locate FinGrind repository root");
  }
}

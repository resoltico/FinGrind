package dev.erst.fingrind.report.pdf;

import static java.util.Objects.requireNonNull;
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

/** Guards the exact bundled Noto font bytes and their controlling license provenance. */
class PdfFontLegalProvenanceTest {
  private static final String FONT_RESOURCE_ROOT = "/dev/erst/fingrind/report/pdf/fonts/";

  @Test
  void bundledNotoSansBytesAndExactUpstreamLicenseRemainLocked() throws Exception {
    assertEquals(
        "b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5",
        resourceSha256("NotoSans-Regular.ttf"));
    assertEquals(
        "c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a",
        resourceSha256("NotoSans-Bold.ttf"));

    Path repositoryRoot = repositoryRoot();
    Path fontLicense = repositoryRoot.resolve("LICENSE-SIL-OFL-1.1");
    assertEquals(
        "0dab92d0544f7b233403f14b84a663bdbfa746982eda629e7f4f9ffe1b036feb",
        fileSha256(fontLicense));
    assertTrue(
        Files.readString(fontLicense)
            .startsWith(
                "Copyright 2018 The Noto Project Authors (github.com/googlei18n/noto-fonts)"));
  }

  private static String resourceSha256(String fileName)
      throws IOException, NoSuchAlgorithmException {
    try (InputStream input =
        requireNonNull(
            PdfFontLegalProvenanceTest.class.getResourceAsStream(FONT_RESOURCE_ROOT + fileName),
            "missing bundled font resource " + fileName)) {
      return sha256(input);
    }
  }

  private static String fileSha256(Path path) throws IOException, NoSuchAlgorithmException {
    try (InputStream input = Files.newInputStream(path)) {
      return sha256(input);
    }
  }

  private static String sha256(InputStream input) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[8192];
    int count = input.read(buffer);
    while (count != -1) {
      digest.update(buffer, 0, count);
      count = input.read(buffer);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
          && Files.isRegularFile(current.resolve("LICENSE-SIL-OFL-1.1"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("could not locate FinGrind repository root");
  }
}

package dev.erst.fingrind.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/** Reads the packaged CLI metadata that is expanded during the Gradle resource step. */
final class CliMetadata {
  private static final String UNKNOWN_VERSION = "unknown";

  private final Properties properties = new Properties();

  CliMetadata() {
    this(openMetadataStream());
  }

  CliMetadata(@Nullable InputStream inputStream) {
    try (InputStream metadataStream =
        Objects.requireNonNull(inputStream, "Missing fingrind.properties resource.")) {
      properties.load(metadataStream);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to load CLI metadata.", exception);
    }
  }

  String applicationName() {
    return requiredProperty("name");
  }

  String version() {
    return versionFrom(
        CliMetadata.class.getPackage().getImplementationVersion(), requiredProperty("version"));
  }

  String description() {
    return requiredProperty("description");
  }

  private String requiredProperty(String propertyName) {
    return Objects.requireNonNull(
        properties.getProperty(propertyName), "Missing CLI metadata property: " + propertyName);
  }

  static String versionFrom(
      @Nullable String implementationVersion, @Nullable String fallbackVersion) {
    if (implementationVersion != null && !implementationVersion.isBlank()) {
      return implementationVersion;
    }
    if (fallbackVersion != null && !fallbackVersion.isBlank()) {
      return fallbackVersion;
    }
    return UNKNOWN_VERSION;
  }

  private static @Nullable InputStream openMetadataStream() {
    return CliMetadata.class.getResourceAsStream("/fingrind.properties");
  }
}

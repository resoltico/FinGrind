package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Forces a directory's name changes after an attestation key is atomically published. */
final class AttestationDirectoryDurability {
  private static final String DURABILITY_FAILURE =
      "Failed to make the published attestation key directory durable.";

  private AttestationDirectoryDurability() {}

  static void force(Path directory) throws IOException {
    Objects.requireNonNull(directory, "directory");
    requireNativeAccess(AttestationDirectoryDurability.class.getModule());
    force(
        directory,
        operatingSystem(System.getProperty("os.name", "")),
        AttestationDirectoryFfmTransport.production());
  }

  static void force(
      Path directory, OperatingSystem operatingSystem, DirectoryDurabilityOperations operations)
      throws IOException {
    Objects.requireNonNull(directory, "directory");
    Objects.requireNonNull(operatingSystem, "operatingSystem");
    Objects.requireNonNull(operations, "operations");
    PlatformBinding binding = operations.binding(operatingSystem);
    try (DirectoryHandle handle = binding.open(directory)) {
      if (binding.isInvalid(handle)) {
        throw failure();
      }
      IOException operationFailure;
      try {
        operationFailure = flush(binding, handle);
      } catch (RuntimeException | Error exception) {
        addSuppressedCloseFailure(exception, close(binding, handle));
        throw exception;
      }
      IOException closeFailure = close(binding, handle);
      if (operationFailure != null) {
        addSuppressedCloseFailure(operationFailure, closeFailure);
        throw operationFailure;
      }
      if (closeFailure != null) {
        throw closeFailure;
      }
    }
  }

  static OperatingSystem operatingSystem(String operatingSystemName) throws IOException {
    String normalizedName = Objects.requireNonNull(operatingSystemName, "operatingSystemName");
    String normalizedLowerCase = normalizedName.toLowerCase(Locale.ROOT);
    if (normalizedLowerCase.contains("windows")) {
      return OperatingSystem.WINDOWS;
    }
    if (normalizedLowerCase.contains("linux")
        || normalizedLowerCase.contains("mac")
        || normalizedLowerCase.contains("darwin")) {
      return OperatingSystem.POSIX;
    }
    throw new IOException(
        "Attestation key directory durability is supported only on macOS, Linux, and Windows. Detected: "
            + normalizedName);
  }

  static void requireNativeAccess(Module module) throws IOException {
    Objects.requireNonNull(module, "module");
    if (!module.isNativeAccessEnabled()) {
      throw new IOException(
          "Attestation key directory durability requires JVM native access. Rerun with --enable-native-access="
              + nativeAccessTarget(module)
              + ".");
    }
  }

  static String nativeAccessTarget(Module module) {
    Module checkedModule = Objects.requireNonNull(module, "module");
    return checkedModule.isNamed() ? checkedModule.getName() : "ALL-UNNAMED";
  }

  static IOException failure() {
    return new IOException(DURABILITY_FAILURE);
  }

  static IOException failure(Throwable cause) {
    return new IOException(DURABILITY_FAILURE, cause);
  }

  private static @Nullable IOException flush(PlatformBinding binding, DirectoryHandle handle) {
    try {
      return binding.flush(handle) == 0 ? null : failure();
    } catch (IOException exception) {
      return exception;
    }
  }

  private static @Nullable IOException close(PlatformBinding binding, DirectoryHandle handle) {
    try {
      return binding.close(handle) == 0 ? null : failure();
    } catch (IOException exception) {
      return exception;
    }
  }

  private static void addSuppressedCloseFailure(
      Throwable failure, @Nullable IOException closeFailure) {
    if (closeFailure != null) {
      failure.addSuppressed(closeFailure);
    }
  }

  /** Names the operating-system durability primitive required by a supported FinGrind target. */
  enum OperatingSystem {
    POSIX,
    WINDOWS
  }

  /** Provides the platform-specific handle operations that make a directory entry durable. */
  @FunctionalInterface
  interface DirectoryDurabilityOperations {
    /** Resolves the native operations for the requested supported platform. */
    PlatformBinding binding(OperatingSystem operatingSystem) throws IOException;
  }

  /** Owns one native directory handle and the three operations used to make it durable. */
  interface PlatformBinding {
    /** Opens the directory and returns its platform-native handle. */
    DirectoryHandle open(Path directory) throws IOException;

    /** Identifies an unsuccessful platform-native handle result. */
    boolean isInvalid(DirectoryHandle handle);

    /** Flushes the opened directory handle's pending metadata changes. */
    int flush(DirectoryHandle handle) throws IOException;

    /** Closes the opened directory handle after its flush attempt. */
    int close(DirectoryHandle handle) throws IOException;
  }

  /** Releases the foreign resources associated with a native directory handle. */
  @FunctionalInterface
  interface DirectoryHandle extends AutoCloseable {
    @Override
    void close();
  }
}

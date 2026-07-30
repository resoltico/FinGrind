package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises FFM transport creation failures without mixing them into retained-handle coverage. */
class WindowsPrivateOutputFileFfmTransportFailureTest {
  private static final Path PRIVATE_PATH = Path.of("synthetic-private-output.fg");

  @Test
  void mapsEveryFileAndDirectoryCreationOutcomeWithoutLeavingNativeOwnersLive() throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      WindowsPrivateOutputFileTransport.NativeFileOperations fileOperations =
          WindowsPrivateOutputFileFfmTransport.operationsFor(windows.calls());
      WindowsPrivateOutputDirectoryTransport.NativeDirectoryOperations directoryOperations =
          WindowsPrivateOutputDirectoryFfmTransport.operationsFor(windows.calls());

      windows.createFileError(WindowsPrivateOutputFileNative.ERROR_ALREADY_EXISTS);
      assertThrows(
          FileAlreadyExistsException.class,
          () -> WindowsPrivateOutputFileTransport.createNew(PRIVATE_PATH, fileOperations));
      windows.createFileError(5);
      assertThrows(
          IOException.class,
          () -> WindowsPrivateOutputFileTransport.createNew(PRIVATE_PATH, fileOperations));
      assertThrows(
          IOException.class,
          () ->
              WindowsPrivateOutputFileTransport.openExisting(
                  PRIVATE_PATH, PrivateOutputFile.Access.READ_WRITE, fileOperations));
      windows.createFileError(0);

      windows.createDirectoryError(WindowsPrivateOutputFileNative.ERROR_ALREADY_EXISTS);
      assertThrows(
          FileAlreadyExistsException.class,
          () ->
              WindowsPrivateOutputDirectoryTransport.createNew(
                  Path.of("existing-private-directory"), directoryOperations));
      windows.createDirectoryError(WindowsPrivateOutputFileNative.ERROR_FILE_EXISTS);
      assertThrows(
          FileAlreadyExistsException.class,
          () ->
              WindowsPrivateOutputDirectoryTransport.createNew(
                  Path.of("existing-private-directory-by-file-code"), directoryOperations));
      windows.createDirectoryError(5);
      assertThrows(
          IOException.class,
          () ->
              WindowsPrivateOutputDirectoryTransport.createNew(
                  Path.of("broken-private-directory"), directoryOperations));
      windows.createDirectoryError(0);
      windows.createFileError(5);
      IOException directoryOpenFailure =
          assertThrows(
              IOException.class,
              () ->
                  WindowsPrivateOutputDirectoryTransport.createNew(
                      Path.of("unopenable-private-directory"), directoryOperations));
      assertTrue(String.valueOf(directoryOpenFailure.getMessage()).contains("CreateFileW"));
      windows.createFileError(0);

      try (WindowsPrivateOutputFileTransport.CurrentOwner genericOwner = () -> "S-1-5-21-42") {
        assertThrows(
            IllegalArgumentException.class,
            () -> fileOperations.createNew(PRIVATE_PATH, genericOwner));
        assertThrows(
            IllegalArgumentException.class,
            () ->
                fileOperations.openExisting(
                    PRIVATE_PATH, PrivateOutputFile.Access.READ_ONLY, genericOwner));
        assertThrows(
            IllegalArgumentException.class,
            () -> directoryOperations.createDirectory(Path.of("directory"), genericOwner));
        assertThrows(
            IllegalArgumentException.class,
            () -> directoryOperations.openExistingDirectory(Path.of("directory"), genericOwner));
      }

      assertThrows(
          NullPointerException.class,
          () -> WindowsPrivateOutputFileFfmTransport.operationsFor(nullOf()));
      assertThrows(
          NullPointerException.class,
          () -> WindowsPrivateOutputDirectoryFfmTransport.operationsFor(nullOf()));
    }
  }
}

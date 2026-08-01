package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Exercises successful FFM transport integration through the platform-facing Windows boundary. */
class WindowsPrivateOutputFileFfmTransportIntegrationTest {
  private static final Path PRIVATE_PATH = Path.of("synthetic-private-output.fg");

  @Test
  void platformAdapterUsesOneFreshInjectedCallTableForEveryWindowsCapability() throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      WindowsPrivateOutputFilePlatformAdapter adapter =
          new WindowsPrivateOutputFilePlatformAdapter(windows::calls);

      try (PrivateOutputFile.OpenedFile created = adapter.createNew(PRIVATE_PATH)) {
        assertTrue(created.created());
      }
      try (PrivateOutputFile.OpenedFile opened =
          adapter.openExisting(PRIVATE_PATH, PrivateOutputFile.Access.READ_ONLY)) {
        assertFalse(opened.created());
      }
      windows.entryKind(WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY);
      adapter.createDirectory(Path.of("adapter-private-directory"));
      WindowsCurrentTokenAclPrincipalMatcher matcher =
          adapter.acquireCurrentTokenAclPrincipalMatcher();
      try {
        assertTrue(matcher.matchesCurrentToken(() -> "RUNNER\\runneradmin"));
      } finally {
        matcher.release();
      }

      assertEquals(4, windows.callTableRequests());
    }
  }

  @Test
  void createsProvesAndRetainsAFileWithoutLoadingWindowsLibraries() throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      try (PrivateOutputFile.OpenedFile opened =
          WindowsPrivateOutputFileTransport.createNew(
              PRIVATE_PATH, WindowsPrivateOutputFileFfmTransport.operationsFor(windows.calls()))) {
        assertTrue(opened.created());
        assertEquals(3, opened.write(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        opened.truncate(3L);
        opened.force();
        assertEquals(
            "windows-v1:volume=73:file=000102030405060708090a0b0c0d0e0f",
            opened.physicalObjectIdentity());
        try (PrivateOutputFile.HeldLock ignored =
            Objects.requireNonNull(opened.tryExclusiveLock(0L, 1L))) {
          assertTrue(opened.isOpen());
        }
      }

      assertTrue(windows.operationNames().contains("createFileW"));
      assertTrue(windows.operationNames().contains("lockFileEx"));
      assertTrue(windows.operationNames().contains("unlockFileEx"));
      assertTrue(windows.operationNames().contains("closeHandle"));
    }
  }

  @Test
  void opensReadOnlyFilesAndCreatesPrivateDirectoriesThroughTheSameLiveOwnerContext()
      throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      try (PrivateOutputFile.OpenedFile opened =
          WindowsPrivateOutputFileTransport.openExisting(
              PRIVATE_PATH,
              PrivateOutputFile.Access.READ_ONLY,
              WindowsPrivateOutputFileFfmTransport.operationsFor(windows.calls()))) {
        assertFalse(opened.created());
        assertEquals(-1, opened.read(ByteBuffer.allocate(1)));
      }

      windows.entryKind(WindowsPrivateOutputFileTransport.EntryKind.DIRECTORY);
      WindowsPrivateOutputDirectoryTransport.createNew(
          Path.of("synthetic-private-directory"),
          WindowsPrivateOutputDirectoryFfmTransport.operationsFor(windows.calls()));

      assertTrue(windows.operationNames().contains("createDirectoryW"));
    }
  }

  @Test
  void mapsAWindowsCreateCollisionAndRejectsADeadGenericOwnerBeforeAnyNativeProof()
      throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      windows.createFileError(WindowsPrivateOutputFileNative.ERROR_FILE_EXISTS);

      assertThrows(
          FileAlreadyExistsException.class,
          () ->
              WindowsPrivateOutputFileTransport.createNew(
                  PRIVATE_PATH,
                  WindowsPrivateOutputFileFfmTransport.operationsFor(windows.calls())));

      try (WindowsPrivateOutputFileHandle handle =
          new WindowsPrivateOutputFileHandle(
              windows.calls(), new WindowsPrivateOutputFileNative.Handle(0x77L))) {
        assertThrows(
            IllegalArgumentException.class, () -> handle.securityProof(() -> "not-a-native-owner"));
      }
    }
  }
}

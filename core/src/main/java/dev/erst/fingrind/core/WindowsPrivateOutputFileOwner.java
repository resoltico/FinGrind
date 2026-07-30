package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Retains the current Windows token user only while creating or proving one private artifact. */
final class WindowsPrivateOutputFileOwner
    implements WindowsPrivateOutputFileTransport.CurrentTokenUser {
  private final WindowsPrivateOutputFileOwnerCalls ownerCalls;
  private final WindowsPrivateOutputFileHandleCalls handleCalls;
  private final Arena arena;
  private final WindowsPrivateOutputFileNative.Handle token;
  private final MemorySegment ownerSid;
  private final String ownerSidText;
  private boolean closed;

  private WindowsPrivateOutputFileOwner(
      WindowsPrivateOutputFileOwnerCalls ownerCalls,
      WindowsPrivateOutputFileHandleCalls handleCalls,
      Arena arena,
      WindowsPrivateOutputFileNative.Handle token,
      MemorySegment ownerSid,
      String ownerSidText) {
    this.ownerCalls = Objects.requireNonNull(ownerCalls, "ownerCalls");
    this.handleCalls = Objects.requireNonNull(handleCalls, "handleCalls");
    this.arena = Objects.requireNonNull(arena, "arena");
    this.token = Objects.requireNonNull(token, "token");
    this.ownerSid = Objects.requireNonNull(ownerSid, "ownerSid");
    this.ownerSidText = Objects.requireNonNull(ownerSidText, "ownerSidText");
  }

  static WindowsPrivateOutputFileOwner acquire(WindowsPrivateOutputFileCalls calls)
      throws IOException {
    WindowsPrivateOutputFileCalls checkedCalls = Objects.requireNonNull(calls, "calls");
    WindowsPrivateOutputFileOwnerCalls ownerCalls = checkedCalls.ownerCalls();
    WindowsPrivateOutputFileHandleCalls handleCalls = checkedCalls.fileCalls();
    Arena arena = Arena.ofConfined();
    try {
      MemorySegment tokenOut = arena.allocate(ValueLayout.ADDRESS);
      WindowsPrivateOutputFileNative.Result<Long> process = ownerCalls.getCurrentProcess();
      WindowsPrivateOutputFileNative.requireTrue(
          ownerCalls.openProcessToken(
              MemorySegment.ofAddress(process.value()),
              WindowsPrivateOutputFileNative.TOKEN_QUERY,
              tokenOut),
          "OpenProcessToken");
      WindowsPrivateOutputFileNative.Handle token =
          new WindowsPrivateOutputFileNative.Handle(
              tokenOut.get(ValueLayout.ADDRESS, 0L).address());
      try {
        MemorySegment ownerSid = readTokenUserSid(ownerCalls, arena, token);
        return new WindowsPrivateOutputFileOwner(
            ownerCalls, handleCalls, arena, token, ownerSid, sidText(ownerCalls, ownerSid));
      } catch (IOException | RuntimeException | Error failure) {
        WindowsPrivateOutputFileNative.closePreservingFailure(handleCalls, token, failure);
        throw failure;
      }
    } catch (IOException | RuntimeException | Error failure) {
      arena.close();
      throw failure;
    }
  }

  static String protectedOwnerOnlyDescriptor(String ownerSidText) {
    String checkedOwner = Objects.requireNonNull(ownerSidText, "ownerSidText");
    return "O:" + checkedOwner + "D:P(A;;FA;;;" + checkedOwner + ")";
  }

  @Override
  public String ownerSidText() {
    requireOpen();
    return ownerSidText;
  }

  MemorySegment ownerSid() {
    requireOpen();
    return ownerSid;
  }

  SecurityAttributes securityAttributes(Arena callArena) throws IOException {
    requireOpen();
    MemorySegment descriptor = securityDescriptor(ownerCalls, ownerSidText);
    try {
      long pointerSize = ValueLayout.ADDRESS.byteSize();
      long descriptorOffset = WindowsPrivateOutputFileNative.alignUp(Integer.BYTES, pointerSize);
      long inheritOffset = descriptorOffset + pointerSize;
      long structureSize =
          WindowsPrivateOutputFileNative.alignUp(inheritOffset + Integer.BYTES, pointerSize);
      MemorySegment attributes = callArena.allocate(structureSize, pointerSize).fill((byte) 0);
      attributes.set(ValueLayout.JAVA_INT, 0L, Math.toIntExact(structureSize));
      attributes.set(ValueLayout.ADDRESS, descriptorOffset, descriptor);
      attributes.set(ValueLayout.JAVA_INT, inheritOffset, 0);
      return new SecurityAttributes(ownerCalls, attributes, descriptor);
    } catch (RuntimeException | Error failure) {
      try {
        WindowsPrivateOutputFileNative.localFree(ownerCalls, descriptor);
      } catch (IOException freeFailure) {
        failure.addSuppressed(freeFailure);
      }
      throw failure;
    }
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException failure = null;
    try (arena) {
      WindowsPrivateOutputFileNative.closeHandle(handleCalls, token);
    } catch (IOException exception) {
      failure = exception;
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static MemorySegment readTokenUserSid(
      WindowsPrivateOutputFileOwnerCalls calls,
      Arena arena,
      WindowsPrivateOutputFileNative.Handle token)
      throws IOException {
    MemorySegment requiredSize = arena.allocate(ValueLayout.JAVA_INT);
    WindowsPrivateOutputFileNative.Result<Integer> initial =
        calls.getTokenInformation(
            token.segment(),
            WindowsPrivateOutputFileNative.TOKEN_USER,
            MemorySegment.NULL,
            0,
            requiredSize);
    if (initial.value() != 0
        || initial.lastError() != WindowsPrivateOutputFileNative.ERROR_INSUFFICIENT_BUFFER) {
      throw WindowsPrivateOutputFileNative.windowsFailure(
          "GetTokenInformation(TokenUser)", initial.lastError());
    }
    int bytes = requiredSize.get(ValueLayout.JAVA_INT, 0L);
    if (bytes < ValueLayout.ADDRESS.byteSize()) {
      throw new IOException("GetTokenInformation(TokenUser) returned an invalid user size.");
    }
    MemorySegment owner = arena.allocate(bytes, ValueLayout.ADDRESS.byteAlignment());
    WindowsPrivateOutputFileNative.requireTrue(
        calls.getTokenInformation(
            token.segment(), WindowsPrivateOutputFileNative.TOKEN_USER, owner, bytes, requiredSize),
        "GetTokenInformation(TokenUser)");
    MemorySegment sid = owner.get(ValueLayout.ADDRESS, 0L);
    if (sid.address() == 0L) {
      throw new IOException("GetTokenInformation(TokenUser) returned no user SID.");
    }
    return sid;
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The Windows current-token-user context is already closed.");
    }
  }

  private static String sidText(WindowsPrivateOutputFileOwnerCalls calls, MemorySegment sid)
      throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment textOut = arena.allocate(ValueLayout.ADDRESS);
      WindowsPrivateOutputFileNative.requireTrue(
          calls.convertSidToStringSidW(sid, textOut), "ConvertSidToStringSidW");
      MemorySegment text = textOut.get(ValueLayout.ADDRESS, 0L);
      if (text.address() == 0L) {
        throw new IOException("ConvertSidToStringSidW returned no SID string.");
      }
      try {
        byte[] bytes =
            MemorySegment.ofAddress(text.address())
                .reinterpret(WindowsPrivateOutputFileNative.MAXIMUM_SID_STRING_BYTES)
                .toArray(ValueLayout.JAVA_BYTE);
        int length = 0;
        while (length + 1 < bytes.length && (bytes[length] != 0 || bytes[length + 1] != 0)) {
          length += Character.BYTES;
        }
        if (length + 1 >= bytes.length) {
          throw new IOException("Windows token user SID text exceeded its bounded buffer.");
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_16LE);
      } finally {
        WindowsPrivateOutputFileNative.localFree(calls, text);
      }
    }
  }

  private static MemorySegment securityDescriptor(
      WindowsPrivateOutputFileOwnerCalls calls, String ownerSidText) throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment descriptorOut = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment sizeOut = arena.allocate(ValueLayout.JAVA_INT);
      WindowsPrivateOutputFileNative.requireTrue(
          calls.convertStringSecurityDescriptorToSecurityDescriptorW(
              WindowsPrivateOutputFileNative.wideString(
                  arena, protectedOwnerOnlyDescriptor(ownerSidText)),
              WindowsPrivateOutputFileNative.SDDL_REVISION_1,
              descriptorOut,
              sizeOut),
          "ConvertStringSecurityDescriptorToSecurityDescriptorW");
      MemorySegment descriptor = descriptorOut.get(ValueLayout.ADDRESS, 0L);
      if (descriptor.address() == 0L) {
        throw new IOException(
            "ConvertStringSecurityDescriptorToSecurityDescriptorW returned no descriptor.");
      }
      return MemorySegment.ofAddress(descriptor.address());
    }
  }

  /**
   * Native security-attribute storage whose descriptor allocation must be released exactly once.
   */
  record SecurityAttributes(
      WindowsPrivateOutputFileOwnerCalls calls, MemorySegment attributes, MemorySegment descriptor)
      implements AutoCloseable {
    SecurityAttributes {
      Objects.requireNonNull(calls, "calls");
      Objects.requireNonNull(attributes, "attributes");
      Objects.requireNonNull(descriptor, "descriptor");
    }

    /**
     * Releases the descriptor allocated by {@code
     * ConvertStringSecurityDescriptorToSecurityDescriptorW}.
     */
    @Override
    public void close() throws IOException {
      WindowsPrivateOutputFileNative.localFree(calls, descriptor);
    }
  }
}

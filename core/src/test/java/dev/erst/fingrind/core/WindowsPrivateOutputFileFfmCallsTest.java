package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises every Windows FFM call translation against deterministic method handles. */
class WindowsPrivateOutputFileFfmCallsTest {
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  @Test
  void translatesEveryCapturedAndDirectWindowsCallWithoutLoadingWindowsLibraries()
      throws Exception {
    WindowsPrivateOutputFileCalls calls =
        WindowsPrivateOutputFileFfmCalls.fromBindings(
            bindings(capturedInt(7, 41), capturedAddress(91L, 42), directInt(9)));

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(ValueLayout.JAVA_LONG);

      assertEquals(7, calls.fileCalls().createDirectoryW(value, MemorySegment.NULL).value());
      assertEquals(41, calls.fileCalls().createDirectoryW(value, MemorySegment.NULL).lastError());
      assertEquals(
          91L,
          calls
              .fileCalls()
              .createFileW(value, 1, 2, MemorySegment.NULL, 3, 4, MemorySegment.NULL)
              .value());
      assertEquals(42, calls.ownerCalls().getCurrentProcess().lastError());
      assertEquals(91L, calls.ownerCalls().getCurrentProcess().value());
      assertEquals(
          7, calls.fileCalls().readFile(value, value, 1, value, MemorySegment.NULL).value());
      assertEquals(
          7, calls.fileCalls().writeFile(value, value, 1, value, MemorySegment.NULL).value());
      assertEquals(7, calls.fileCalls().flushFileBuffers(value).value());
      assertEquals(7, calls.fileCalls().closeHandle(value).value());
      assertEquals(7, calls.fileCalls().getFileInformationByHandleEx(value, 1, value, 8).value());
      assertEquals(7, calls.fileCalls().getFileSizeEx(value, value).value());
      assertEquals(7, calls.fileCalls().setFilePointerEx(value, 1L, value, 0).value());
      assertEquals(7, calls.fileCalls().setEndOfFile(value).value());
      assertEquals(7, calls.fileCalls().lockFileEx(value, 1, 0, 1, 0, value).value());
      assertEquals(7, calls.fileCalls().unlockFileEx(value, 0, 1, 0, value).value());
      assertEquals(7, calls.ownerCalls().openProcessToken(value, 1, value).value());
      assertEquals(7, calls.ownerCalls().getTokenInformation(value, 1, value, 8, value).value());
      assertEquals(91L, calls.ownerCalls().localFree(value).value());
      assertEquals(7, calls.ownerCalls().convertSidToStringSidW(value, value).value());
      assertEquals(
          7,
          calls
              .ownerCalls()
              .convertStringSecurityDescriptorToSecurityDescriptorW(value, 1, value, value)
              .value());
      assertEquals(
          9, calls.securityCalls().getSecurityInfo(value, 1, 1, value, value, value, value, value));
      assertEquals(
          7, calls.securityCalls().getSecurityDescriptorControl(value, value, value).value());
      assertEquals(
          7, calls.securityCalls().getSecurityDescriptorDacl(value, value, value, value).value());
      assertEquals(7, calls.securityCalls().getAclInformation(value, value, 12, 2).value());
      assertEquals(7, calls.securityCalls().getAce(value, 0, value).value());
      assertEquals(7, calls.securityCalls().getLengthSid(value).value());
      assertEquals(7, calls.securityCalls().equalSid(value, value).value());
    }
  }

  @Test
  void preservesUncheckedInvocationFailuresAndWrapsCheckedOnesAsIoFailures() throws Exception {
    IOException checked = new IOException("simulated checked invocation failure");
    WindowsPrivateOutputFileCalls checkedCalls =
        WindowsPrivateOutputFileFfmCalls.fromBindings(
            bindings(throwing(checked), capturedAddress(91L, 42), directInt(9)));
    IllegalStateException unchecked =
        new IllegalStateException("simulated unchecked invocation failure");
    WindowsPrivateOutputFileCalls uncheckedCalls =
        WindowsPrivateOutputFileFfmCalls.fromBindings(
            bindings(throwing(unchecked), capturedAddress(91L, 42), directInt(9)));
    WindowsPrivateOutputFileCalls directCheckedCalls =
        WindowsPrivateOutputFileFfmCalls.fromBindings(
            bindings(capturedInt(7, 41), capturedAddress(91L, 42), throwing(checked)));
    WindowsPrivateOutputFileCalls directUncheckedCalls =
        WindowsPrivateOutputFileFfmCalls.fromBindings(
            bindings(capturedInt(7, 41), capturedAddress(91L, 42), throwing(unchecked)));

    IOException checkedFailure =
        assertThrows(
            IOException.class,
            () ->
                checkedCalls.fileCalls().createDirectoryW(MemorySegment.NULL, MemorySegment.NULL));
    assertEquals(
        "FinGrind could not invoke one Windows private-output operation.",
        checkedFailure.getMessage());
    assertSame(checked, checkedFailure.getCause());
    assertSame(
        unchecked,
        assertThrows(
            IllegalStateException.class,
            () ->
                uncheckedCalls
                    .fileCalls()
                    .createDirectoryW(MemorySegment.NULL, MemorySegment.NULL)));
    IOException directFailure =
        assertThrows(
            IOException.class,
            () ->
                directCheckedCalls
                    .securityCalls()
                    .getSecurityInfo(
                        MemorySegment.NULL,
                        1,
                        1,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL));
    assertSame(checked, directFailure.getCause());
    assertSame(
        unchecked,
        assertThrows(
            IllegalStateException.class,
            () ->
                directUncheckedCalls
                    .securityCalls()
                    .getSecurityInfo(
                        MemorySegment.NULL,
                        1,
                        1,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL,
                        MemorySegment.NULL)));
  }

  @Test
  void rawBindingTablesRefuseIncompleteCallVocabularies() {
    assertThrows(
        IllegalArgumentException.class, () -> new WindowsPrivateOutputFileHandleBindings());
    assertThrows(
        NullPointerException.class,
        () -> new WindowsPrivateOutputFileOwnerBindings(new MethodHandle[6]));
    assertThrows(
        IllegalArgumentException.class, () -> new WindowsPrivateOutputFileSecurityBindings());
  }

  @Test
  void nativeRuntimeComposesTheCompleteFfmVocabularyThroughInjectedBindingDependencies()
      throws Exception {
    List<String> libraries = new ArrayList<>();
    SymbolLookup symbols = symbol -> Optional.of(MemorySegment.ofAddress(1L));
    MethodHandle captured = capturedInt(0, 0);
    MethodHandle direct = directInt(0);
    WindowsPrivateOutputFileBindingSupport.NativeRuntime runtime =
        new WindowsPrivateOutputFileBindingSupport.NativeRuntime(
            (name, arena) -> {
              libraries.add(name);
              return symbols;
            },
            new WindowsPrivateOutputFileBindingSupport.Binder() {
              @Override
              public MethodHandle captured(
                  SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
                return captured;
              }

              @Override
              public MethodHandle direct(
                  SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
                return direct;
              }
            });

    assertNotNull(runtime.calls());
    assertEquals(List.of("kernel32", "advapi32"), libraries);
    assertNotNull(
        new WindowsPrivateOutputFilePlatformAdapter.RuntimeCallTableSource(() -> runtime).calls());
    assertDoesNotThrow(WindowsPrivateOutputFileBindingSupport::nativeRuntime);
  }

  @Test
  void nativeBindingLookupReportsMissingSymbolsAndUnavailableLibraries() throws IOException {
    SymbolLookup missingSymbols = symbol -> Optional.empty();
    FunctionDescriptor descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT);
    RecordingDowncalls downcalls = new RecordingDowncalls();
    WindowsPrivateOutputFileBindingSupport.Binder binder =
        WindowsPrivateOutputFileBindingSupport.nativeBinder(downcalls, "GetLastError");

    IOException directMissing =
        assertThrows(
            IOException.class, () -> binder.direct(missingSymbols, "DirectMissing", descriptor));
    assertEquals(
        "Windows private-output transport is missing native symbol DirectMissing.",
        directMissing.getMessage());
    IOException capturedMissing =
        assertThrows(
            IOException.class,
            () -> binder.captured(missingSymbols, "CapturedMissing", descriptor));
    assertEquals(
        "Windows private-output transport is missing native symbol CapturedMissing.",
        capturedMissing.getMessage());
    assertThrows(
        IOException.class,
        () -> WindowsPrivateOutputFileHandleBindings.bind(missingSymbols, binder));
    assertThrows(
        IOException.class,
        () -> WindowsPrivateOutputFileOwnerBindings.bind(missingSymbols, missingSymbols, binder));
    assertThrows(
        IOException.class,
        () -> WindowsPrivateOutputFileSecurityBindings.bind(missingSymbols, binder));
    assertThrows(
        IOException.class,
        () -> WindowsPrivateOutputFileBindings.bind((name, arena) -> missingSymbols, binder));
    assertThrows(
        NullPointerException.class, () -> binder.captured(nullOf(), "Ignored", descriptor));
    assertThrows(
        IOException.class,
        () ->
            WindowsPrivateOutputFileBindings.bind(
                (name, arena) -> {
                  throw new IllegalArgumentException("missing library");
                },
                binder));
    SymbolLookup presentSymbols =
        symbol ->
            switch (symbol) {
              case "PresentDirect" -> Optional.of(MemorySegment.ofAddress(1L));
              case "PresentCaptured" -> Optional.of(MemorySegment.ofAddress(2L));
              default -> Optional.empty();
            };
    assertNotNull(binder.direct(presentSymbols, "PresentDirect", descriptor));
    assertNotNull(binder.captured(presentSymbols, "PresentCaptured", descriptor));
    assertEquals(
        List.of(
            new DowncallRequest(1L, DowncallStyle.DIRECT),
            new DowncallRequest(2L, DowncallStyle.CAPTURED)),
        downcalls.requests());
    assertEquals("GetLastError", downcalls.captureStateName());
  }

  @Test
  void nativeLinkerAdapterBuildsDirectAndCapturedHandlesForASupportedCaptureState() {
    WindowsPrivateOutputFileBindingSupport.FfmDowncallFactory factory =
        new WindowsPrivateOutputFileBindingSupport.FfmDowncallFactory(Linker.nativeLinker());
    FunctionDescriptor descriptor = FunctionDescriptor.ofVoid();
    MemorySegment address = MemorySegment.ofAddress(1L);

    assertNotNull(factory.directDowncall(address, descriptor));
    String captureStateName =
        Linker.Option.captureStateLayout().memberLayouts().stream()
            .map(MemoryLayout::name)
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow();
    assertNotNull(factory.capturedDowncall(address, descriptor, captureStateName));
  }

  private static WindowsPrivateOutputFileBindings bindings(
      MethodHandle capturedInt, MethodHandle capturedAddress, MethodHandle directInt)
      throws ReflectiveOperationException {
    MethodHandle[] fileCalls = new MethodHandle[12];
    Arrays.fill(fileCalls, capturedInt);
    fileCalls[1] = capturedAddress;
    MethodHandle[] ownerCalls = new MethodHandle[6];
    Arrays.fill(ownerCalls, capturedInt);
    ownerCalls[0] = capturedAddress;
    ownerCalls[3] = capturedAddress;
    MethodHandle[] securityCalls = new MethodHandle[7];
    Arrays.fill(securityCalls, capturedInt);
    securityCalls[0] = directInt;
    return new WindowsPrivateOutputFileBindings(
        new WindowsPrivateOutputFileHandleBindings(fileCalls),
        new WindowsPrivateOutputFileOwnerBindings(ownerCalls),
        new WindowsPrivateOutputFileSecurityBindings(securityCalls));
  }

  private static MethodHandle capturedInt(int value, int lastError)
      throws ReflectiveOperationException {
    return varargsHandle(new CapturedInt(value, lastError));
  }

  private static MethodHandle capturedAddress(long value, int lastError)
      throws ReflectiveOperationException {
    return varargsHandle(new CapturedAddress(value, lastError));
  }

  private static MethodHandle directInt(int value) throws ReflectiveOperationException {
    return varargsHandle(new DirectInt(value));
  }

  private static MethodHandle throwing(Throwable failure) throws ReflectiveOperationException {
    return varargsHandle(new ThrowingInvocation(failure));
  }

  private static MethodHandle varargsHandle(VarargsInvocation receiver)
      throws ReflectiveOperationException {
    return LOOKUP
        .findVirtual(
            VarargsInvocation.class, "invoke", MethodType.methodType(Object.class, Object[].class))
        .bindTo(receiver)
        .asVarargsCollector(Object[].class);
  }

  /** Method-handle receiver that accepts the dynamically shaped native invocation arguments. */
  @FunctionalInterface
  private interface VarargsInvocation {
    @SuppressWarnings("UnusedMethod")
    Object invoke(Object... arguments) throws Throwable;
  }

  private record CapturedInt(int value, int lastError) implements VarargsInvocation {
    @Override
    public Object invoke(Object... arguments) {
      ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0L, lastError);
      return value;
    }
  }

  private record CapturedAddress(long value, int lastError) implements VarargsInvocation {
    @Override
    public Object invoke(Object... arguments) {
      ((MemorySegment) arguments[0]).set(ValueLayout.JAVA_INT, 0L, lastError);
      return MemorySegment.ofAddress(value);
    }
  }

  private record DirectInt(int value) implements VarargsInvocation {
    @Override
    public Object invoke(Object... ignored) {
      return value;
    }
  }

  private record ThrowingInvocation(Throwable failure) implements VarargsInvocation {
    @Override
    public Object invoke(Object... ignored) throws Throwable {
      throw failure;
    }
  }

  /** Records downcall declarations without requiring a host-native linker or symbol. */
  private static final class RecordingDowncalls
      implements WindowsPrivateOutputFileBindingSupport.DowncallFactory {
    private final List<DowncallRequest> requests = new ArrayList<>();
    private String captureStateName = "";

    @Override
    public MethodHandle capturedDowncall(
        MemorySegment address, FunctionDescriptor descriptor, String captureStateName) {
      requests.add(new DowncallRequest(address.address(), DowncallStyle.CAPTURED));
      this.captureStateName = captureStateName;
      return MethodHandles.empty(MethodType.methodType(void.class));
    }

    @Override
    public MethodHandle directDowncall(MemorySegment address, FunctionDescriptor descriptor) {
      requests.add(new DowncallRequest(address.address(), DowncallStyle.DIRECT));
      return MethodHandles.empty(MethodType.methodType(void.class));
    }

    private List<DowncallRequest> requests() {
      return List.copyOf(requests);
    }

    private String captureStateName() {
      return captureStateName;
    }
  }

  /** Captures the native binder's direct versus captured-last-error distinction. */
  private record DowncallRequest(long address, DowncallStyle style) {}

  /** Distinguishes ordinary native downcalls from last-error-capturing downcalls. */
  private enum DowncallStyle {
    DIRECT,
    CAPTURED
  }
}

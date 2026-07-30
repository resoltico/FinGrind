package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves the complete Win32 symbol, library, descriptor, and last-error binding contract. */
class WindowsPrivateOutputFileBindingContractTest {
  private static final Library KERNEL32 = new Library("kernel32");
  private static final Library ADVAPI32 = new Library("advapi32");

  @Test
  void declaresTheCompleteWin32AbiWithItsOwningLibraryAndCallingConvention() throws IOException {
    RecordingBinder binder = new RecordingBinder();

    assertNotNull(
        WindowsPrivateOutputFileBindings.bind(
            (libraryName, arena) ->
                switch (libraryName) {
                  case "kernel32" -> KERNEL32;
                  case "advapi32" -> ADVAPI32;
                  default -> throw new AssertionError("Unexpected Windows library: " + libraryName);
                },
            binder));

    assertEquals(
        List.of(
            captured("kernel32", "CreateDirectoryW", intLayout(), addressLayout(), addressLayout()),
            captured(
                "kernel32",
                "CreateFileW",
                addressLayout(),
                addressLayout(),
                intLayout(),
                intLayout(),
                addressLayout(),
                intLayout(),
                intLayout(),
                addressLayout()),
            captured(
                "kernel32",
                "ReadFile",
                intLayout(),
                addressLayout(),
                addressLayout(),
                intLayout(),
                addressLayout(),
                addressLayout()),
            captured(
                "kernel32",
                "WriteFile",
                intLayout(),
                addressLayout(),
                addressLayout(),
                intLayout(),
                addressLayout(),
                addressLayout()),
            captured("kernel32", "FlushFileBuffers", intLayout(), addressLayout()),
            captured("kernel32", "CloseHandle", intLayout(), addressLayout()),
            captured(
                "kernel32",
                "GetFileInformationByHandleEx",
                intLayout(),
                addressLayout(),
                intLayout(),
                addressLayout(),
                intLayout()),
            captured("kernel32", "GetFileSizeEx", intLayout(), addressLayout(), addressLayout()),
            captured(
                "kernel32",
                "SetFilePointerEx",
                intLayout(),
                addressLayout(),
                longLayout(),
                addressLayout(),
                intLayout()),
            captured("kernel32", "SetEndOfFile", intLayout(), addressLayout()),
            captured(
                "kernel32",
                "LockFileEx",
                intLayout(),
                addressLayout(),
                intLayout(),
                intLayout(),
                intLayout(),
                intLayout(),
                addressLayout()),
            captured(
                "kernel32",
                "UnlockFileEx",
                intLayout(),
                addressLayout(),
                intLayout(),
                intLayout(),
                intLayout(),
                addressLayout()),
            captured("kernel32", "GetCurrentProcess", addressLayout()),
            captured(
                "advapi32",
                "OpenProcessToken",
                intLayout(),
                addressLayout(),
                intLayout(),
                addressLayout()),
            captured(
                "advapi32",
                "GetTokenInformation",
                intLayout(),
                addressLayout(),
                intLayout(),
                addressLayout(),
                intLayout(),
                addressLayout()),
            captured("kernel32", "LocalFree", addressLayout(), addressLayout()),
            captured(
                "advapi32",
                "ConvertSidToStringSidW",
                intLayout(),
                addressLayout(),
                addressLayout()),
            captured(
                "advapi32",
                "LookupAccountSidW",
                intLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout()),
            captured(
                "advapi32",
                "ConvertStringSecurityDescriptorToSecurityDescriptorW",
                intLayout(),
                addressLayout(),
                intLayout(),
                addressLayout(),
                addressLayout()),
            direct(
                "advapi32",
                "GetSecurityInfo",
                intLayout(),
                addressLayout(),
                intLayout(),
                intLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout()),
            captured(
                "advapi32",
                "GetSecurityDescriptorControl",
                intLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout()),
            captured(
                "advapi32",
                "GetSecurityDescriptorDacl",
                intLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout(),
                addressLayout()),
            captured(
                "advapi32",
                "GetAclInformation",
                intLayout(),
                addressLayout(),
                addressLayout(),
                intLayout(),
                intLayout()),
            captured(
                "advapi32", "GetAce", intLayout(), addressLayout(), intLayout(), addressLayout()),
            captured("advapi32", "GetLengthSid", intLayout(), addressLayout()),
            captured("advapi32", "EqualSid", intLayout(), addressLayout(), addressLayout())),
        binder.requests());
  }

  @Test
  void rejectsAnOwnerBindingTableWithTheWrongNumberOfCalls() {
    assertThrows(IllegalArgumentException.class, WindowsPrivateOutputFileOwnerBindings::new);
  }

  private static BindingRequest captured(
      String libraryName,
      String symbol,
      MemoryLayout returnLayout,
      MemoryLayout... parameterLayouts) {
    return new BindingRequest(
        libraryName,
        symbol,
        FunctionDescriptor.of(returnLayout, parameterLayouts),
        CallStyle.CAPTURED);
  }

  private static BindingRequest direct(
      String libraryName,
      String symbol,
      MemoryLayout returnLayout,
      MemoryLayout... parameterLayouts) {
    return new BindingRequest(
        libraryName,
        symbol,
        FunctionDescriptor.of(returnLayout, parameterLayouts),
        CallStyle.DIRECT);
  }

  private static ValueLayout intLayout() {
    return ValueLayout.JAVA_INT;
  }

  private static ValueLayout longLayout() {
    return ValueLayout.JAVA_LONG;
  }

  private static ValueLayout addressLayout() {
    return ValueLayout.ADDRESS;
  }

  /** Distinguishes a direct status return from a call that writes the captured last-error state. */
  private enum CallStyle {
    CAPTURED,
    DIRECT
  }

  /** Captures one table declaration for an exact ABI assertion. */
  private record BindingRequest(
      String libraryName, String symbol, FunctionDescriptor descriptor, CallStyle callStyle) {}

  /** Builds typed inert method handles while retaining each table's ABI declaration. */
  private static final class RecordingBinder
      implements WindowsPrivateOutputFileBindingSupport.Binder {
    private final List<BindingRequest> requests = new ArrayList<>();

    @Override
    public MethodHandle captured(
        SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
      return record(CallStyle.CAPTURED, lookup, symbol, descriptor);
    }

    @Override
    public MethodHandle direct(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
      return record(CallStyle.DIRECT, lookup, symbol, descriptor);
    }

    private MethodHandle record(
        CallStyle callStyle, SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
      if (!(lookup instanceof Library library)) {
        throw new AssertionError("Unexpected symbol lookup.");
      }
      requests.add(new BindingRequest(library.name(), symbol, descriptor, callStyle));
      return MethodHandles.empty(methodType(descriptor, callStyle));
    }

    private List<BindingRequest> requests() {
      return List.copyOf(requests);
    }

    private static MethodType methodType(FunctionDescriptor descriptor, CallStyle callStyle) {
      List<MemoryLayout> argumentLayouts = descriptor.argumentLayouts();
      Class<?>[] parameterTypes =
          new Class<?>[argumentLayouts.size() + (callStyle == CallStyle.CAPTURED ? 1 : 0)];
      int parameterOffset = callStyle == CallStyle.CAPTURED ? 1 : 0;
      if (callStyle == CallStyle.CAPTURED) {
        parameterTypes[0] = MemorySegment.class;
      }
      for (int index = 0; index < argumentLayouts.size(); index++) {
        parameterTypes[index + parameterOffset] = carrier(argumentLayouts.get(index));
      }
      Class<?> returnType =
          descriptor.returnLayout().isPresent()
              ? carrier(descriptor.returnLayout().orElseThrow())
              : void.class;
      return MethodType.methodType(returnType, parameterTypes);
    }

    private static Class<?> carrier(MemoryLayout layout) {
      if (layout instanceof ValueLayout valueLayout) {
        return valueLayout.carrier();
      }
      throw new AssertionError("Expected a value layout.");
    }
  }

  /** Labels an injected native library without resolving or touching host-native symbols. */
  private record Library(String name) implements SymbolLookup {
    @Override
    public Optional<MemorySegment> find(String ignored) {
      return Optional.empty();
    }
  }
}

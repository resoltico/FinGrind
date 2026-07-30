package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Raw Win32 bindings for current-owner and protected-descriptor construction. */
final class WindowsPrivateOutputFileOwnerBindings {
  final MethodHandle getCurrentProcess;
  final MethodHandle openProcessToken;
  final MethodHandle getTokenInformation;
  final MethodHandle localFree;
  final MethodHandle convertSidToStringSidW;
  final MethodHandle convertStringSecurityDescriptorToSecurityDescriptorW;

  WindowsPrivateOutputFileOwnerBindings(MethodHandle... calls) {
    Objects.requireNonNull(calls, "calls");
    if (calls.length != 6) {
      throw new IllegalArgumentException("The Windows protected-output owner table is incomplete.");
    }
    for (int index = 0; index < calls.length; index++) {
      Objects.requireNonNull(calls[index], "Windows protected-output owner binding " + index);
    }
    getCurrentProcess = calls[0];
    openProcessToken = calls[1];
    getTokenInformation = calls[2];
    localFree = calls[3];
    convertSidToStringSidW = calls[4];
    convertStringSecurityDescriptorToSecurityDescriptorW = calls[5];
  }

  static WindowsPrivateOutputFileOwnerBindings bind(
      SymbolLookup kernel32,
      SymbolLookup advapi32,
      WindowsPrivateOutputFileBindingSupport.Binder binder)
      throws IOException {
    return new WindowsPrivateOutputFileOwnerBindings(
        binder.captured(kernel32, "GetCurrentProcess", FunctionDescriptor.of(ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "OpenProcessToken",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "GetTokenInformation",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)),
        binder.captured(
            kernel32, "LocalFree", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "ConvertSidToStringSidW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "ConvertStringSecurityDescriptorToSecurityDescriptorW",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS)));
  }
}

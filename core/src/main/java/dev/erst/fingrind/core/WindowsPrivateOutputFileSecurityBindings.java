package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Raw Win32 bindings for descriptor and DACL proof. */
final class WindowsPrivateOutputFileSecurityBindings {
  final MethodHandle getSecurityInfo;
  final MethodHandle getSecurityDescriptorControl;
  final MethodHandle getSecurityDescriptorDacl;
  final MethodHandle getAclInformation;
  final MethodHandle getAce;
  final MethodHandle getLengthSid;
  final MethodHandle equalSid;

  WindowsPrivateOutputFileSecurityBindings(MethodHandle... calls) {
    Objects.requireNonNull(calls, "calls");
    if (calls.length != 7) {
      throw new IllegalArgumentException(
          "The Windows protected-output security table is incomplete.");
    }
    for (int index = 0; index < calls.length; index++) {
      Objects.requireNonNull(calls[index], "Windows protected-output security binding " + index);
    }
    getSecurityInfo = calls[0];
    getSecurityDescriptorControl = calls[1];
    getSecurityDescriptorDacl = calls[2];
    getAclInformation = calls[3];
    getAce = calls[4];
    getLengthSid = calls[5];
    equalSid = calls[6];
  }

  static WindowsPrivateOutputFileSecurityBindings bind(
      SymbolLookup advapi32, WindowsPrivateOutputFileBindingSupport.Binder binder)
      throws IOException {
    return new WindowsPrivateOutputFileSecurityBindings(
        binder.direct(
            advapi32,
            "GetSecurityInfo",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "GetSecurityDescriptorControl",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "GetSecurityDescriptorDacl",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "GetAclInformation",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT)),
        binder.captured(
            advapi32,
            "GetAce",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "GetLengthSid",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
        binder.captured(
            advapi32,
            "EqualSid",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));
  }
}

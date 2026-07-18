package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolSharedRequestFields;
import dev.erst.fingrind.core.AccountCode;
import java.util.List;
import java.util.Map;

/** Owns the minimal executable request schema for retiring one account. */
final class MachineContractRetireAccountSchemas {
  private MachineContractRetireAccountSchemas() {}

  static ContractRequestShapes.RetireAccountRequestShapeDescriptor descriptor() {
    List<MachineContractFieldSpec> fields =
        List.of(
            MachineContractFieldSpec.required(
                ProtocolSharedRequestFields.ACCOUNT_CODE,
                "Declared account code to retire. The account must have a zero current balance and no active durable dependencies.",
                MachineContractScalarSchemas.tokenStringSchema(
                    "Declared account code to retire.",
                    AccountCode.pattern(),
                    AccountCode.maxLength())));
    return new ContractRequestShapes.RetireAccountRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(fields),
        MachineContractSchemaSupport.rootObjectSchema(
            "Retire one zero-balance account without removing its history.", fields));
  }

  static Map<String, Object> schema() {
    return descriptor().schema();
  }
}

/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.v2.operation;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.v2.StackArithmetic;

/**
 * EVM v2 PUSH1-PUSH32 operation. Reads immediate bytes from bytecode and pushes them as a 256-bit
 * value onto the long[] stack.
 */
public class PushOperationV2 extends AbstractFixedCostOperationV2 {

  /** The constant PUSH_BASE (0x5F). PUSH1 = PUSH_BASE + 1, PUSH32 = PUSH_BASE + 32. */
  public static final int PUSH_BASE = 0x5F;

  /** The Push operation success result. */
  static final OperationResult pushSuccess = new OperationResult(3, null);

  private final int length;

  /**
   * Instantiates a new Push operation.
   *
   * @param length the number of immediate bytes (1..32)
   * @param gasCalculator the gas calculator
   */
  public PushOperationV2(final int length, final GasCalculator gasCalculator) {
    super(
        PUSH_BASE + length,
        "PUSH" + length,
        0,
        1,
        gasCalculator,
        gasCalculator.getVeryLowTierGasCost());
    this.length = length;
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    return staticOperation(frame, frame.stackDataV2(), code, frame.getPC(), length);
  }

  /**
   * Performs Push operation on the v2 stack. Reads {@code pushSize} bytes from the bytecode
   * starting after the current opcode and pushes them as a right-aligned 256-bit value.
   *
   * @param frame the frame
   * @param stack the v2 operand stack ({@code long[]} in big-endian limb order)
   * @param code the raw bytecode array
   * @param pc the current program counter (pointing at the PUSH opcode)
   * @param pushSize the number of immediate bytes to read (1..32)
   * @return the operation result
   */
  public static OperationResult staticOperation(
      final MessageFrame frame,
      final long[] stack,
      final byte[] code,
      final int pc,
      final int pushSize) {
    if (!frame.stackHasSpace(1)) return OVERFLOW_RESPONSE;
    frame.setTopV2(
        StackArithmetic.pushFromBytes(stack, frame.stackTopV2(), code, pc + 1, pushSize));
    frame.setPC(pc + pushSize);
    return pushSuccess;
  }

  /**
   * Specialized PUSH1 operation. Avoids the generic pushFromBytes dispatch for the single most
   * frequently executed opcode.
   *
   * @param frame the frame
   * @param stack the v2 operand stack
   * @param code the raw bytecode array
   * @param pc the current program counter
   * @return the operation result
   */
  public static OperationResult staticPush1(
      final MessageFrame frame, final long[] stack, final byte[] code, final int pc) {
    if (!frame.stackHasSpace(1)) return OVERFLOW_RESPONSE;
    frame.setTopV2(StackArithmetic.push1(stack, frame.stackTopV2(), code, pc));
    frame.setPC(pc + 1);
    return pushSuccess;
  }

  /**
   * Specialized PUSH2 operation. Avoids the generic pushFromBytes dispatch for the second most
   * common PUSH variant, used for jump destinations.
   *
   * @param frame the frame
   * @param stack the v2 operand stack
   * @param code the raw bytecode array
   * @param pc the current program counter
   * @return the operation result
   */
  public static OperationResult staticPush2(
      final MessageFrame frame, final long[] stack, final byte[] code, final int pc) {
    if (!frame.stackHasSpace(1)) return OVERFLOW_RESPONSE;
    frame.setTopV2(StackArithmetic.push2(stack, frame.stackTopV2(), code, pc));
    frame.setPC(pc + 2);
    return pushSuccess;
  }
}

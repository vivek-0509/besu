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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.v2.testutils.TestMessageFrameBuilderV2;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class PopOperationV2Test {

  private final GasCalculator gasCalculator = new SpuriousDragonGasCalculator();
  private final PopOperationV2 operation = new PopOperationV2(gasCalculator);

  @Test
  void popRemovesTopItem() {
    final MessageFrame frame =
        new TestMessageFrameBuilderV2()
            .pushStackItem(Bytes32.fromHexStringLenient("0x01"))
            .pushStackItem(Bytes32.fromHexStringLenient("0x02"))
            .build();
    assertThat(frame.stackTopV2()).isEqualTo(2);

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(2L);
    assertThat(frame.stackTopV2()).isEqualTo(1);
  }

  @Test
  void popOnEmptyStackReturnsUnderflow() {
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();
    assertThat(frame.stackTopV2()).isEqualTo(0);

    final OperationResult result = PopOperationV2.staticOperation(frame);

    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
  }

  @Test
  void popStaticOperationDecrementsTop() {
    final MessageFrame frame =
        new TestMessageFrameBuilderV2()
            .pushStackItem(Bytes32.fromHexStringLenient("0xABCD"))
            .build();
    assertThat(frame.stackTopV2()).isEqualTo(1);

    final OperationResult result = PopOperationV2.staticOperation(frame);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.stackTopV2()).isEqualTo(0);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}

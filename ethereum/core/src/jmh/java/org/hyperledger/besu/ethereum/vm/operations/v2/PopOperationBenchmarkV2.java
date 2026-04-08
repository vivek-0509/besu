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
package org.hyperledger.besu.ethereum.vm.operations.v2;

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.PopOperation;
import org.hyperledger.besu.evm.v2.operation.PopOperationV2;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmark comparing v1 and v2 POP operations.
 *
 * <p>Each iteration pushes a value then pops it, measuring the pop cost. The push is needed to
 * ensure the stack is non-empty.
 */
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class PopOperationBenchmarkV2 {

  private static final Bytes STACK_VALUE =
      Bytes.fromHexString("0x3232323232323232323232323232323232323232323232323232323232323232");

  private MessageFrame v1Frame;
  private MessageFrame v2Frame;

  @Setup(Level.Iteration)
  public void setUp() {
    v1Frame = createFrame(false);
    v2Frame = createFrame(true);
  }

  @Benchmark
  public void v1Pop(final Blackhole blackhole) {
    v1Frame.pushStackItem(STACK_VALUE);
    blackhole.consume(PopOperation.staticOperation(v1Frame));
  }

  @Benchmark
  public void v2Pop(final Blackhole blackhole) {
    // Push one item onto v2 stack so POP has something to remove
    final long[] s = v2Frame.stackDataV2();
    final int top = v2Frame.stackTopV2();
    final int dst = top << 2;
    s[dst] = 0x3232323232323232L;
    s[dst + 1] = 0x3232323232323232L;
    s[dst + 2] = 0x3232323232323232L;
    s[dst + 3] = 0x3232323232323232L;
    v2Frame.setTopV2(top + 1);

    blackhole.consume(PopOperationV2.staticOperation(v2Frame));
  }

  private static MessageFrame createFrame(final boolean enableV2) {
    return MessageFrame.builder()
        .enableEvmV2(enableV2)
        .worldUpdater(mock(WorldUpdater.class))
        .originator(Address.ZERO)
        .gasPrice(Wei.ONE)
        .blobGasPrice(Wei.ONE)
        .blockValues(mock(BlockValues.class))
        .miningBeneficiary(Address.ZERO)
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(Long.MAX_VALUE)
        .address(Address.ZERO)
        .contract(Address.ZERO)
        .inputData(Bytes.EMPTY)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(Code.EMPTY_CODE)
        .completer(__ -> {})
        .build();
  }
}

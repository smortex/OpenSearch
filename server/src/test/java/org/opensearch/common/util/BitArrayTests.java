/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.common.util;

import org.opensearch.common.breaker.CircuitBreaker;
import org.opensearch.common.breaker.CircuitBreakingException;
import org.opensearch.common.breaker.NoopCircuitBreaker;
import org.opensearch.common.unit.ByteSizeUnit;
import org.opensearch.common.unit.ByteSizeValue;
import org.opensearch.indices.breaker.CircuitBreakerService;
import org.opensearch.test.OpenSearchTestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assume.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitArrayTests extends OpenSearchTestCase {
    public void testRandom() {
        try (BitArray bitArray = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE)) {
            int numBits = randomIntBetween(1000, 10000);
            for (int step = 0; step < 3; step++) {
                boolean[] bits = new boolean[numBits];
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < numBits; i++) {
                    bits[i] = randomBoolean();
                    slots.add(i);
                }
                Collections.shuffle(slots, random());
                for (int i : slots) {
                    if (bits[i]) {
                        bitArray.set(i);
                    } else {
                        bitArray.clear(i);
                    }
                }
                for (int i = 0; i < numBits; i++) {
                    assertEquals(bitArray.get(i), bits[i]);
                }
            }
        }
    }

    public void testVeryLarge() {
        assumeThat(Runtime.getRuntime().maxMemory(), greaterThanOrEqualTo(ByteSizeUnit.MB.toBytes(512)));
        try (BitArray bitArray = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE)) {
            long index = randomLongBetween(Integer.MAX_VALUE, (long) (Integer.MAX_VALUE * 1.5));
            assertFalse(bitArray.get(index));
            bitArray.set(index);
            assertTrue(bitArray.get(index));
            bitArray.clear(index);
            assertFalse(bitArray.get(index));
        }
    }

    public void testTooBigIsNotSet() {
        try (BitArray bitArray = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE)) {
            for (int i = 0; i < 1000; i++) {
                /*
                 * The first few times this is called we check within the
                 * array. But we quickly go beyond it and those all return
                 * false as well.
                 */
                assertFalse(bitArray.get(i));
            }
        }
    }

    public void testClearingDoesntAllocate() {
        CircuitBreakerService breaker = mock(CircuitBreakerService.class);
        ByteSizeValue max = new ByteSizeValue(1, ByteSizeUnit.KB);
        when(breaker.getBreaker(CircuitBreaker.REQUEST)).thenReturn(new NoopCircuitBreaker(CircuitBreaker.REQUEST) {
            private long total = 0;

            @Override
            public double addEstimateBytesAndMaybeBreak(long bytes, String label) throws CircuitBreakingException {
                total += bytes;
                if (total > max.getBytes()) {
                    throw new CircuitBreakingException("test error", bytes, max.getBytes(), Durability.TRANSIENT);
                }
                return total;
            }

            @Override
            public long addWithoutBreaking(long bytes) {
                total += bytes;
                return total;
            }
        });
        BigArrays bigArrays = new BigArrays(null, breaker, CircuitBreaker.REQUEST, true);
        try (BitArray bitArray = new BitArray(1, bigArrays)) {
            bitArray.clear(100000000);
        }
    }

    public void testOr() {
        try (BitArray bitArray1 = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE);
             BitArray bitArray2 = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE);
             BitArray bitArrayFull = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE)) {
            int numBits = randomIntBetween(1000, 10000);
            for (int step = 0; step < 3; step++) {
                for (int i = 0; i < numBits; i++) {
                    if (randomBoolean()) {
                        if (rarely()) {
                            bitArray1.set(i);
                            bitArray2.set(i);
                        } else if (randomBoolean()) {
                            bitArray1.set(i);
                        } else {
                            bitArray2.set(i);
                        }
                        bitArrayFull.set(i);
                    }
                }
                bitArray1.or(bitArray2);
                for (int i = 0; i < numBits; i++) {
                    assertEquals(bitArrayFull.get(i), bitArray1.get(i));
                }
            }
        }
    }

    public void testNextBitSet() {
        try (BitArray bitArray = new BitArray(1, BigArrays.NON_RECYCLING_INSTANCE)) {
            int numBits = randomIntBetween(1000, 10000);
            for (int step = 0; step < 3; step++) {
                for (int i = 0; i < numBits; i++) {
                    if (randomBoolean()) {
                        bitArray.set(i);
                    }
                }
                long next = bitArray.nextSetBit(0);
                for (int i = 0; i < numBits; i++) {
                    if (i == next) {
                        assertEquals(true, bitArray.get(i));
                        if (i < numBits - 1) {
                            next = bitArray.nextSetBit(i + 1);
                        }
                    } else {
                        assertEquals(false, bitArray.get(i));
                    }
                }
            }
        }
    }
}

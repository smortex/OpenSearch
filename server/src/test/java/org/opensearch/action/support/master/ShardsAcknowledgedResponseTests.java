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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.action.support.master;

import org.opensearch.Version;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.util.Collections;

import static org.hamcrest.Matchers.is;

public class ShardsAcknowledgedResponseTests extends OpenSearchTestCase {

    public void testSerialization() throws Exception {
        ShardsAcknowledgedResponse testInstance = new TestImpl(true, true);

        ShardsAcknowledgedResponse result =
            copyWriteable(testInstance, new NamedWriteableRegistry(Collections.emptyList()),
                in -> new TestImpl(in, true, true), Version.CURRENT);
        assertThat(result.isAcknowledged(), is(true));
        assertThat(result.isShardsAcknowledged(), is(true));

        result = copyWriteable(testInstance, new NamedWriteableRegistry(Collections.emptyList()),
            in -> new TestImpl(in, false, false), Version.CURRENT);
        assertThat(result.isAcknowledged(), is(false));
        assertThat(result.isShardsAcknowledged(), is(false));
    }

    private static class TestImpl extends ShardsAcknowledgedResponse {

        private TestImpl(StreamInput in, boolean readShardsAcknowledged, boolean readAcknowledged) throws IOException {
            super(in, readShardsAcknowledged, readAcknowledged);
        }

        private TestImpl(boolean acknowledged, boolean shardsAcknowledged) {
            super(acknowledged, shardsAcknowledged);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            writeShardsAcknowledged(out);
        }
    }

}

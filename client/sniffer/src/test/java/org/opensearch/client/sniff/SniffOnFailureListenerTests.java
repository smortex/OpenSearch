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

package org.opensearch.client.sniff;

import org.apache.http.HttpHost;
import org.opensearch.client.Node;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SniffOnFailureListenerTests extends RestClientTestCase {

    public void testSetSniffer() throws Exception {
        SniffOnFailureListener listener = new SniffOnFailureListener();

        try {
            listener.onFailure(null);
            fail("should have failed");
        } catch(IllegalStateException e) {
            assertEquals("sniffer was not set, unable to sniff on failure", e.getMessage());
        }

        try {
            listener.setSniffer(null);
            fail("should have failed");
        } catch(NullPointerException e) {
            assertEquals("sniffer must not be null", e.getMessage());
        }

        try (RestClient restClient = RestClient.builder(new HttpHost("localhost", 9200)).build()) {
            try (Sniffer sniffer = Sniffer.builder(restClient).setNodesSniffer(new MockNodesSniffer()).build()) {
                listener.setSniffer(sniffer);
                try {
                    listener.setSniffer(sniffer);
                    fail("should have failed");
                } catch(IllegalStateException e) {
                    assertEquals("sniffer can only be set once", e.getMessage());
                }
                listener.onFailure(new Node(new HttpHost("localhost", 9200)));
            }
        }
    }
}

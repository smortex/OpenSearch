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

package org.opensearch.action.search;

import org.apache.lucene.search.TotalHits;
import org.opensearch.action.ActionListener;
import org.opensearch.common.document.DocumentField;
import org.opensearch.common.text.Text;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.InnerHitBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExpandSearchPhaseTests extends OpenSearchTestCase {

    public void testCollapseSingleHit() throws IOException {
        final int iters = randomIntBetween(5, 10);
        for (int i = 0; i < iters; i++) {
            final int numInnerHits = randomIntBetween(1, 5);
            List<SearchHits> collapsedHits = new ArrayList<>(numInnerHits);
            for (int innerHitNum = 0; innerHitNum < numInnerHits; innerHitNum++) {
                SearchHits hits = new SearchHits(new SearchHit[]{new SearchHit(innerHitNum, "ID", new Text("type"),
                    Collections.emptyMap(), Collections.emptyMap()), new SearchHit(innerHitNum + 1, "ID", new Text("type"),
                    Collections.emptyMap(), Collections.emptyMap())}, new TotalHits(2, TotalHits.Relation.EQUAL_TO), 1.0F);
                collapsedHits.add(hits);
            }

            AtomicBoolean executedMultiSearch = new AtomicBoolean(false);
            QueryBuilder originalQuery = randomBoolean() ? null : QueryBuilders.termQuery("foo", "bar");

            final MockSearchPhaseContext mockSearchPhaseContext = new MockSearchPhaseContext(1);
            String collapseValue = randomBoolean() ? null : "boom";

            mockSearchPhaseContext.getRequest().source(new SearchSourceBuilder()
                .collapse(new CollapseBuilder("someField")
                    .setInnerHits(IntStream.range(0, numInnerHits).mapToObj(hitNum -> new InnerHitBuilder().setName("innerHit" + hitNum))
                        .collect(Collectors.toList()))));
            mockSearchPhaseContext.getRequest().source().query(originalQuery);
            mockSearchPhaseContext.searchTransport = new SearchTransportService(null, null) {
                @Override
                void sendExecuteMultiSearch(MultiSearchRequest request, SearchTask task, ActionListener<MultiSearchResponse> listener) {
                    assertTrue(executedMultiSearch.compareAndSet(false, true));
                    assertEquals(numInnerHits, request.requests().size());
                    SearchRequest searchRequest = request.requests().get(0);
                    assertTrue(searchRequest.source().query() instanceof BoolQueryBuilder);

                    BoolQueryBuilder groupBuilder = (BoolQueryBuilder) searchRequest.source().query();
                    if (collapseValue == null) {
                        assertThat(groupBuilder.mustNot(), Matchers.contains(QueryBuilders.existsQuery("someField")));
                    } else {
                        assertThat(groupBuilder.filter(), Matchers.contains(QueryBuilders.matchQuery("someField", "boom")));
                    }
                    if (originalQuery != null) {
                        assertThat(groupBuilder.must(), Matchers.contains(QueryBuilders.termQuery("foo", "bar")));
                    }
                    assertArrayEquals(mockSearchPhaseContext.getRequest().indices(), searchRequest.indices());
                    assertArrayEquals(mockSearchPhaseContext.getRequest().types(), searchRequest.types());


                    List<MultiSearchResponse.Item> mSearchResponses = new ArrayList<>(numInnerHits);
                    for (int innerHitNum = 0; innerHitNum < numInnerHits; innerHitNum++) {
                        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(collapsedHits.get(innerHitNum),
                            null, null, null, false, null, 1);
                        mockSearchPhaseContext.sendSearchResponse(internalSearchResponse, null);
                        mSearchResponses.add(new MultiSearchResponse.Item(mockSearchPhaseContext.searchResponse.get(), null));
                    }

                    listener.onResponse(
                            new MultiSearchResponse(mSearchResponses.toArray(new MultiSearchResponse.Item[0]), randomIntBetween(1, 10000)));
                }
            };

            SearchHits hits = new SearchHits(new SearchHit[]{new SearchHit(1, "ID", new Text("type"),
                Collections.singletonMap("someField", new DocumentField("someField", Collections.singletonList(collapseValue))),
                Collections.emptyMap())}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0F);
            InternalSearchResponse internalSearchResponse = new InternalSearchResponse(hits, null, null, null, false, null, 1);
            ExpandSearchPhase phase = new ExpandSearchPhase(mockSearchPhaseContext, internalSearchResponse, null);

            phase.run();
            mockSearchPhaseContext.assertNoFailure();
            SearchResponse theResponse = mockSearchPhaseContext.searchResponse.get();
            assertNotNull(theResponse);
            assertEquals(numInnerHits, theResponse.getHits().getHits()[0].getInnerHits().size());

            for (int innerHitNum = 0; innerHitNum < numInnerHits; innerHitNum++) {
                assertSame(theResponse.getHits().getHits()[0].getInnerHits().get("innerHit" + innerHitNum), collapsedHits.get(innerHitNum));
            }

            assertTrue(executedMultiSearch.get());
        }
    }

    public void testFailOneItemFailsEntirePhase() throws IOException {
        AtomicBoolean executedMultiSearch = new AtomicBoolean(false);

        SearchHits collapsedHits = new SearchHits(new SearchHit[]{new SearchHit(2, "ID", new Text("type"),
            Collections.emptyMap(), Collections.emptyMap()), new SearchHit(3, "ID", new Text("type"),
            Collections.emptyMap(), Collections.emptyMap())}, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0F);
        MockSearchPhaseContext mockSearchPhaseContext = new MockSearchPhaseContext(1);
        String collapseValue = randomBoolean() ? null : "boom";
        mockSearchPhaseContext.getRequest().source(new SearchSourceBuilder()
            .collapse(new CollapseBuilder("someField").setInnerHits(new InnerHitBuilder().setName("foobarbaz"))));
        mockSearchPhaseContext.searchTransport = new SearchTransportService(null, null) {
            @Override
            void sendExecuteMultiSearch(MultiSearchRequest request, SearchTask task, ActionListener<MultiSearchResponse> listener) {
                assertTrue(executedMultiSearch.compareAndSet(false, true));
                InternalSearchResponse internalSearchResponse = new InternalSearchResponse(collapsedHits,
                    null, null, null, false, null, 1);
                SearchResponse searchResponse = new SearchResponse(internalSearchResponse, null, 1, 1, 0, 0,
                    ShardSearchFailure.EMPTY_ARRAY, SearchResponse.Clusters.EMPTY);
                listener.onResponse(new MultiSearchResponse(
                    new MultiSearchResponse.Item[]{
                            new MultiSearchResponse.Item(null, new RuntimeException("boom")),
                            new MultiSearchResponse.Item(searchResponse, null)
                    }, randomIntBetween(1, 10000)));
            }
        };

        SearchHits hits = new SearchHits(new SearchHit[]{new SearchHit(1, "ID", new Text("type"),
            Collections.singletonMap("someField", new DocumentField("someField", Collections.singletonList(collapseValue))),
            Collections.emptyMap()), new SearchHit(2, "ID2", new Text("type"), Collections.singletonMap("someField",
            new DocumentField("someField", Collections.singletonList(collapseValue))), Collections.emptyMap())},
            new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0F);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(hits, null, null, null, false, null, 1);
        ExpandSearchPhase phase = new ExpandSearchPhase(mockSearchPhaseContext, internalSearchResponse, null);
        phase.run();
        assertThat(mockSearchPhaseContext.phaseFailure.get(), Matchers.instanceOf(RuntimeException.class));
        assertEquals("boom", mockSearchPhaseContext.phaseFailure.get().getMessage());
        assertNotNull(mockSearchPhaseContext.phaseFailure.get());
        assertNull(mockSearchPhaseContext.searchResponse.get());
    }

    public void testSkipPhase() throws IOException {
        MockSearchPhaseContext mockSearchPhaseContext = new MockSearchPhaseContext(1);
        mockSearchPhaseContext.searchTransport = new SearchTransportService(null, null) {
            @Override
            void sendExecuteMultiSearch(MultiSearchRequest request, SearchTask task, ActionListener<MultiSearchResponse> listener) {
              fail("no collapsing here");
            }
        };

        SearchHits hits = new SearchHits(new SearchHit[]{new SearchHit(1, "ID", new Text("type"),
            Collections.singletonMap("someField", new DocumentField("someField", Collections.singletonList(null))),
            Collections.emptyMap()), new SearchHit(2, "ID2", new Text("type"), Collections.singletonMap("someField",
            new DocumentField("someField", Collections.singletonList(null))), Collections.emptyMap())},
            new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0F);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(hits, null, null, null, false, null, 1);
        ExpandSearchPhase phase = new ExpandSearchPhase(mockSearchPhaseContext, internalSearchResponse, null);
        phase.run();
        mockSearchPhaseContext.assertNoFailure();
        assertNotNull(mockSearchPhaseContext.searchResponse.get());
    }

    public void testSkipExpandCollapseNoHits() throws IOException {
        MockSearchPhaseContext mockSearchPhaseContext = new MockSearchPhaseContext(1);
        mockSearchPhaseContext.searchTransport = new SearchTransportService(null, null) {
            @Override
            void sendExecuteMultiSearch(MultiSearchRequest request, SearchTask task, ActionListener<MultiSearchResponse> listener) {
                fail("expand should not try to send empty multi search request");
            }
        };
        mockSearchPhaseContext.getRequest().source(new SearchSourceBuilder()
            .collapse(new CollapseBuilder("someField").setInnerHits(new InnerHitBuilder().setName("foobarbaz"))));

        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(hits, null, null, null, false, null, 1);
        ExpandSearchPhase phase = new ExpandSearchPhase(mockSearchPhaseContext, internalSearchResponse, null);
        phase.run();
        mockSearchPhaseContext.assertNoFailure();
        assertNotNull(mockSearchPhaseContext.searchResponse.get());
    }

    public void testExpandRequestOptions() throws IOException {
        MockSearchPhaseContext mockSearchPhaseContext = new MockSearchPhaseContext(1);
        boolean version = randomBoolean();
        final boolean seqNoAndTerm = randomBoolean();

        mockSearchPhaseContext.searchTransport = new SearchTransportService(null, null) {
            @Override
            void sendExecuteMultiSearch(MultiSearchRequest request, SearchTask task, ActionListener<MultiSearchResponse> listener) {
                final QueryBuilder postFilter = QueryBuilders.existsQuery("foo");
                assertTrue(request.requests().stream().allMatch((r) -> "foo".equals(r.preference())));
                assertTrue(request.requests().stream().allMatch((r) -> "baz".equals(r.routing())));
                assertTrue(request.requests().stream().allMatch((r) -> version == r.source().version()));
                assertTrue(request.requests().stream().allMatch((r) -> seqNoAndTerm == r.source().seqNoAndPrimaryTerm()));
                assertTrue(request.requests().stream().allMatch((r) -> postFilter.equals(r.source().postFilter())));
                assertTrue(request.requests().stream().allMatch((r) -> r.source().fetchSource().fetchSource() == false));
                assertTrue(request.requests().stream().allMatch((r) -> r.source().fetchSource().includes().length == 0));
                assertTrue(request.requests().stream().allMatch((r) -> r.source().fetchSource().excludes().length == 0));
            }
        };
        mockSearchPhaseContext.getRequest().source(new SearchSourceBuilder()
            .collapse(
                new CollapseBuilder("someField")
                    .setInnerHits(new InnerHitBuilder().setName("foobarbaz").setVersion(version).setSeqNoAndPrimaryTerm(seqNoAndTerm))
            )
            .fetchSource(false)
            .postFilter(QueryBuilders.existsQuery("foo")))
            .preference("foobar")
            .routing("baz");

        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(hits, null, null, null, false, null, 1);
        ExpandSearchPhase phase = new ExpandSearchPhase(mockSearchPhaseContext, internalSearchResponse, null);
        phase.run();
        mockSearchPhaseContext.assertNoFailure();
        assertNotNull(mockSearchPhaseContext.searchResponse.get());
    }
}

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

package org.opensearch.search.suggest.phrase;

import org.apache.lucene.codecs.TermStats;
import org.apache.lucene.util.BytesRef;
import org.opensearch.search.suggest.phrase.DirectCandidateGenerator.Candidate;
import org.opensearch.search.suggest.phrase.DirectCandidateGenerator.CandidateSet;

import java.io.IOException;
import java.util.Arrays;
//TODO public for tests
public final class MultiCandidateGeneratorWrapper extends CandidateGenerator {


    private final CandidateGenerator[] candidateGenerator;
    private int numCandidates ;

    public MultiCandidateGeneratorWrapper(int numCandidates, CandidateGenerator...candidateGenerators) {
        this.candidateGenerator = candidateGenerators;
        this.numCandidates = numCandidates;
    }
    @Override
    public boolean isKnownWord(BytesRef term) throws IOException {
        return candidateGenerator[0].isKnownWord(term);
    }

    @Override
    public TermStats termStats(BytesRef term) throws IOException {
        return candidateGenerator[0].termStats(term);
    }

    @Override
    public CandidateSet drawCandidates(CandidateSet set) throws IOException {
        for (CandidateGenerator generator : candidateGenerator) {
            generator.drawCandidates(set);
        }
        return reduce(set, numCandidates);
    }

    private CandidateSet reduce(CandidateSet set, int numCandidates) {
        if (set.candidates.length > numCandidates) {
            Candidate[] candidates = set.candidates;
            Arrays.sort(candidates, (left, right) -> Double.compare(right.score, left.score));
            Candidate[] newSet = new Candidate[numCandidates];
            System.arraycopy(candidates, 0, newSet, 0, numCandidates);
            set.candidates = newSet;
        }

        return set;
    }
    @Override
    public Candidate createCandidate(BytesRef term, TermStats termStats, double channelScore, boolean userInput) throws IOException {
        return candidateGenerator[0].createCandidate(term, termStats, channelScore, userInput);
    }

}

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

package org.opensearch.script;

import org.opensearch.OpenSearchException;
import org.opensearch.common.io.stream.StreamInput;

import java.io.IOException;

/**
 * Simple exception class from a script.
 * <p>
 * Use of this exception should generally be avoided, it doesn't provide
 * much context or structure to users trying to debug scripting when
 * things go wrong.
 * @deprecated Use ScriptException for exceptions from the scripting engine,
 *             otherwise use a more appropriate exception (e.g. if thrown
 *             from various abstractions)
 */
@Deprecated
public class GeneralScriptException extends OpenSearchException {

    public GeneralScriptException(String msg) {
        super(msg);
    }

    public GeneralScriptException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public GeneralScriptException(StreamInput in) throws IOException{
        super(in);
    }
}

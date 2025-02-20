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

package org.opensearch.search.fetch;

import org.opensearch.common.ParsingException;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.rest.RestRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Context used to fetch the {@code stored_fields}.
 */
public class StoredFieldsContext implements Writeable {
    public static final String _NONE_ = "_none_";

    private final List<String> fieldNames;
    private boolean fetchFields;

    private StoredFieldsContext(boolean fetchFields) {
        this.fetchFields = fetchFields;
        this.fieldNames = null;
    }

    private StoredFieldsContext(List<String> fieldNames) {
        Objects.requireNonNull(fieldNames, "fieldNames must not be null");
        this.fetchFields = true;
        this.fieldNames = new ArrayList<>(fieldNames);
    }

    public StoredFieldsContext(StoredFieldsContext other) {
        this.fetchFields = other.fetchFields();
        if (other.fieldNames() != null) {
            this.fieldNames = new ArrayList<>(other.fieldNames());
        } else {
            this.fieldNames = null;
        }
    }

    public StoredFieldsContext(StreamInput in) throws IOException {
        this.fetchFields = in.readBoolean();
        if (fetchFields) {
            this.fieldNames = new ArrayList<>((List<String>) in.readGenericValue());
        } else {
            this.fieldNames = null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(fetchFields);
        if (fetchFields) {
            out.writeGenericValue(fieldNames);
        }
    }

    /**
     * Gets the field names to load and return as part of the search request.
     */
    public List<String> fieldNames() {
        return fieldNames;
    }

    /**
     * Adds the field names {@code fieldNames} to the list of fields to load.
     */
    public StoredFieldsContext addFieldNames(List<String> fieldNames) {
        if (fetchFields == false || fieldNames.contains(_NONE_)) {
            throw new IllegalArgumentException("cannot combine _none_ with other fields");
        }
        this.fieldNames.addAll(fieldNames);
        return this;
    }

    /**
     * Adds a field name {@code field} to the list of fields to load.
     */
    public StoredFieldsContext addFieldName(String field) {
        if (fetchFields == false || _NONE_.equals(field)) {
            throw new IllegalArgumentException("cannot combine _none_ with other fields");
        }
        this.fieldNames.add(field);
        return this;
    }

    /**
     * Returns true if the stored fields should be fetched, false otherwise.
     */
    public boolean fetchFields() {
        return fetchFields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StoredFieldsContext that = (StoredFieldsContext) o;

        if (fetchFields != that.fetchFields) return false;
        return fieldNames != null ? fieldNames.equals(that.fieldNames) : that.fieldNames == null;

    }

    @Override
    public int hashCode() {
        int result = fieldNames != null ? fieldNames.hashCode() : 0;
        result = 31 * result + (fetchFields ? 1 : 0);
        return result;
    }

    public void toXContent(String preferredName, XContentBuilder builder) throws IOException {
        if (fetchFields == false) {
            builder.field(preferredName, _NONE_);
        }
        if (fieldNames != null) {
            if (fieldNames.size() == 1) {
                builder.field(preferredName, fieldNames.get(0));
            } else {
                builder.startArray(preferredName);
                for (String fieldName : fieldNames) {
                    builder.value(fieldName);
                }
                builder.endArray();
            }
        }
    }

    public static StoredFieldsContext fromList(List<String> fieldNames) {
        if (fieldNames.size() == 1 && _NONE_.equals(fieldNames.get(0))) {
            return new StoredFieldsContext(false);
        }
        if (fieldNames.contains(_NONE_)) {
            throw new IllegalArgumentException("cannot combine _none_ with other fields");
        }
        return new StoredFieldsContext(fieldNames);
    }

    public static StoredFieldsContext fromXContent(String fieldName, XContentParser parser) throws IOException {
        XContentParser.Token token = parser.currentToken();

        if (token == XContentParser.Token.VALUE_STRING) {
            return fromList(Collections.singletonList(parser.text()));
        } else if (token == XContentParser.Token.START_ARRAY) {
            ArrayList<String> list = new ArrayList<>();
            while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
                list.add(parser.text());
            }
            return fromList(list);
        } else {
            throw new ParsingException(parser.getTokenLocation(),
                "Expected [" + XContentParser.Token.VALUE_STRING + "] or ["
                    + XContentParser.Token.START_ARRAY + "] in [" + fieldName + "] but found [" + token + "]",
                parser.getTokenLocation());
        }
    }

    public static StoredFieldsContext fromRestRequest(String name, RestRequest request) {
        String sField = request.param(name);
        if (sField != null) {
            String[] sFields = Strings.splitStringByCommaToArray(sField);
            return fromList(Arrays.asList(sFields));
        }
        return null;
    }
}

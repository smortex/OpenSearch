/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.common.inject.internal;

import org.opensearch.common.inject.Binder;
import org.opensearch.common.inject.Injector;
import org.opensearch.common.inject.Key;
import org.opensearch.common.inject.spi.BindingTargetVisitor;
import org.opensearch.common.inject.spi.Dependency;
import org.opensearch.common.inject.spi.UntargettedBinding;

public class UntargettedBindingImpl<T> extends BindingImpl<T> implements UntargettedBinding<T> {

    public UntargettedBindingImpl(Injector injector, Key<T> key, Object source) {
        super(injector, key, source, new InternalFactory<T>() {
            @Override
            public T get(Errors errors, InternalContext context, Dependency<?> dependency) {
                throw new AssertionError();
            }
        }, Scoping.UNSCOPED);
    }

    public UntargettedBindingImpl(Object source, Key<T> key, Scoping scoping) {
        super(source, key, scoping);
    }

    @Override
    public <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor) {
        return visitor.visit(this);
    }

    @Override
    public BindingImpl<T> withScoping(Scoping scoping) {
        return new UntargettedBindingImpl<>(getSource(), getKey(), scoping);
    }

    @Override
    public BindingImpl<T> withKey(Key<T> key) {
        return new UntargettedBindingImpl<>(getSource(), key, getScoping());
    }

    @Override
    public void applyTo(Binder binder) {
        getScoping().applyTo(binder.withSource(getSource()).bind(getKey()));
    }

    @Override
    public String toString() {
        return new ToStringBuilder(UntargettedBinding.class)
                .add("key", getKey())
                .add("source", getSource())
                .toString();
    }
}

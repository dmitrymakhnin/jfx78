/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javafx.beans.property;

import javafx.beans.binding.ObjectExpression;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;

/**
 * Super class for all readonly properties wrapping an arbitrary {@code Object}.
 *
 * For specialized implementations for {@link ObservableList}, {@link ObservableSet} and
 * {@link ObservableMap} that also report changes inside the collections, see
 * {@link ReadOnlyListProperty}, {@link ReadOnlySetProperty} and {@link ReadOnlyMapProperty}, respectively.
 *
 * @see javafx.beans.value.ObservableObjectValue
 * @see javafx.beans.binding.ObjectExpression
 * @see ReadOnlyProperty
 *
 *
 * @param <T>
 *            the type of the wrapped {@code Object}
 * @since JavaFX 2.0
 */
public abstract class ReadOnlyObjectProperty<T> extends ObjectExpression<T>
        implements ReadOnlyProperty<T> {

    /**
     * The constructor of {@code ReadOnlyObjectProperty}.
     */
    public ReadOnlyObjectProperty() {
    }

    /**
     * Returns a string representation of this {@code ReadOnlyObjectProperty} object.
     * @return a string representation of this {@code ReadOnlyObjectProperty} object.
     */
    @Override
    public String toString() {
        final Object bean = getBean();
        final String name = getName();
        final StringBuilder result = new StringBuilder(
                "ReadOnlyObjectProperty [");
        if (bean != null) {
            result.append("bean: ").append(bean).append(", ");
        }
        if ((name != null) && !name.equals("")) {
            result.append("name: ").append(name).append(", ");
        }
        result.append("value: ").append(get()).append("]");
        return result.toString();
    }

}

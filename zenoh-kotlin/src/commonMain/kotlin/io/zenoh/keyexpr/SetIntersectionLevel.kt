//
// Copyright (c) 2023 ZettaScale Technology
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License 2.0 which is available at
// http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
//
// Contributors:
//   ZettaScale Zenoh Team, <zenoh@zettascale.tech>
//

package io.zenoh.keyexpr

/**
 * The possible relations between two sets.
 *
 * Note that [EQUALS] implies [INCLUDES], which itself implies [INTERSECTS].
 */
enum class SetIntersectionLevel(internal val value: Int) {
    DISJOINT(0),
    INTERSECTS(1),
    INCLUDES(2),
    EQUALS(3);

    companion object {
        internal fun fromInt(value: Int) = SetIntersectionLevel.entries.first { it.value == value }
    }
}

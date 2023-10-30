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

package io.zenoh.publication

/**
 * The Priority of Zenoh messages.
 *
 * A Priority is identified by a numeric value. Lower the value, higher the priority. Higher the value, lower the priority.
 *
 * - Highest priority: 1 ([REALTIME])
 * - Lowest priority: 7 ([BACKGROUND])
 */
enum class Priority(val value: Int) {
    REALTIME(1),
    INTERACTIVE_HIGH(2),
    INTERACTIVE_LOW(3),
    DATA_HIGH(4),
    DATA(5),
    DATA_LOW(6),
    BACKGROUND(7);
}


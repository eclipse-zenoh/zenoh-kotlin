//
// Copyright (c) 2025 ZettaScale Technology
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

package io.zenoh.handlers

import io.zenoh.annotations.Unstable
import io.zenoh.pubsub.SampleMiss

/**
 * Runnable sample miss callback.
 *
 * @constructor Create empty Callback
 */
@Unstable
fun interface SampleMissCallback {

    /** Callback to be run. */
    fun run(miss: SampleMiss)

}

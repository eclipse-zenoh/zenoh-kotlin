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

package io.zenoh.query

/** The kind of consolidation. */
enum class ConsolidationMode {
    /** No consolidation applied: multiple samples may be received for the same key-timestamp.*/
    NONE,

    /**
     * Monotonic consolidation immediately forwards samples, except if one with an equal or more recent timestamp
     * has already been sent with the same key.
     *
     * This optimizes latency while potentially reducing bandwidth.
     *
     * Note that this doesn't cause re-ordering, but drops the samples for which a more recent timestamp has already
     * been observed with the same key.
     */
    MONOTONIC,

    /** Holds back samples to only send the set of samples that had the highest timestamp for their key. */
    LATEST;
}

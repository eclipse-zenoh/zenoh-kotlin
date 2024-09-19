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

package io.zenoh

import io.zenoh.exceptions.ZError

/** Logger class to redirect the Rust logs from Zenoh to the kotlin environment. */
internal class Logger {

    companion object {

        internal const val LOG_ENV: String = "RUST_LOG"

        fun start(filter: String) = runCatching {
            startLogsViaJNI(filter)
        }

        /**
         * Redirects the rust logs either to logcat for Android systems or to the standard output (for non-android
         * systems).
         *
         * See https://docs.rs/env_logger/latest/env_logger/index.html for accepted filter format.
         */
        @Throws(ZError::class)
        private external fun startLogsViaJNI(filter: String)
    }
}

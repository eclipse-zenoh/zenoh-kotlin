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

package io.zenoh.liveliness

import io.zenoh.jni.JNILivelinessToken
import io.zenoh.session.SessionDeclaration

/**
 * A token whose liveliness is tied to the Zenoh [Session].
 *
 * A declared liveliness token will be seen as alive by any other Zenoh
 * application in the system that monitors it while the liveliness token
 * is not undeclared or dropped, while the Zenoh application that declared
 * it is alive (didn't stop or crashed) and while the Zenoh application
 * that declared the token has Zenoh connectivity with the Zenoh application
 * that monitors it.
 *
 * Liveliness tokens are automatically undeclared when dropped.
 *
 * Example:
 * ```kotlin
 * val session = Zenoh.open(Config.default()).getOrThrow()
 * val keyExpr = "A/B/C".intoKeyExpr().getOrThrow()
 * val token = session.liveliness().declareToken(keyExpr).getOrThrow()
 * //....
 * ```
 */
class LivelinessToken internal constructor(private var jniLivelinessToken: JNILivelinessToken?): SessionDeclaration, AutoCloseable {

    /**
     * Undeclares the token.
     */
    override fun undeclare() {
        jniLivelinessToken?.undeclare()
        jniLivelinessToken = null
    }

    /**
     * Closes the token. This function is equivalent to [undeclare].
     * When using try-with-resources, this function is called automatically.
     */
    override fun close() {
        undeclare()
    }

    protected fun finalize() {
        undeclare()
    }
}

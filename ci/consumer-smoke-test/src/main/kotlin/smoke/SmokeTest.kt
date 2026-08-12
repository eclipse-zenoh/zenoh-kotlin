//
// Copyright (c) 2026 ZettaScale Technology
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

package smoke

import io.zenoh.keyexpr.KeyExpr

/**
 * The minimum that proves a published zenoh-kotlin artifact is usable: loading
 * the native library out of the transitive zenoh-flat-jni dependency, crossing
 * the JNI boundary in both directions, and doing it through the API a user of
 * this SDK actually calls.
 *
 * Key expressions rather than a session, because they need no network, no ports
 * and no discovery — a CI runner cannot make them flaky.
 */
fun main() {
    val expr = "demo/example/**"
    val ke = KeyExpr.tryFrom(expr).getOrThrow()

    check(ke.toString() == expr) { "key expression round-tripped as `$ke`, expected `$expr`" }
    check(ke.intersects(KeyExpr.tryFrom("demo/example/smoke").getOrThrow())) {
        "`$expr` should intersect `demo/example/smoke`"
    }
    check(!ke.intersects(KeyExpr.tryFrom("other/key").getOrThrow())) {
        "`$expr` should not intersect `other/key`"
    }

    println(
        "zenoh-kotlin smoke test OK on " +
            System.getProperty("os.name") + " " + System.getProperty("os.arch")
    )
}

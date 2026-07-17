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

package io.zenoh.exceptions

import io.zenoh.jni.ErrorHandler
import io.zenoh.jni.JniErrorHandler

/**
 * Helpers converting the generated flat-jni error-callback protocol into
 * [Result].
 *
 * Two distinct failure channels are folded into one [Result]:
 *
 * 1. **Native errors** — a generated wrapper never throws from native code;
 *    on failure it *returns* the value produced by its trailing `onError`
 *    handler (a generated typed `fun interface`). The helpers supply a
 *    handler that records the error into a local and, where the wrapper's
 *    return type demands a value, produces a throw-away *sentinel* (a
 *    born-closed handle such as `Session(0L)`, an empty list, …). No
 *    exception is ever in flight for a native error.
 * 2. **JVM-side exceptions** — argument preparation (collection ops, …),
 *    user-supplied conversions (`IntoZBytes.into()`), and class
 *    initialization (native-library loading) can throw before or around the
 *    native call. The block runs inside `runCatching` so these surface as
 *    [Result.failure], preserving the public `Result` contract of the
 *    pre-flat API. The `runCatching` never observes a native error — channel
 *    1 reports those without throwing.
 *
 * `je` is the binding-layer error (UTF-8 decode, closed handle, …); `message`
 * is the library (zenoh) error message. Exactly one is set.
 *
 * For the few non-[Result] contexts (where the public API today already lets
 * an unchecked [ZError] propagate), [throwZError]/[throwZError0] throw from
 * the handler instead — documented safe, as the handler runs after the native
 * call has returned.
 */

/** Fallible flat call returning `Unit` — no sentinel needed. */
internal inline fun zCallUnit(crossinline block: (ErrorHandler<Unit>) -> Unit): Result<Unit> {
    var err: ZError? = null
    val outcome = runCatching {
        block(ErrorHandler { je, message -> err = ZError(je ?: message) })
    }
    err?.let { return Result.failure(it) }
    return outcome
}

/** Binding-only-fallible flat call returning `Unit`. */
internal inline fun zCallUnit0(crossinline block: (JniErrorHandler<Unit>) -> Unit): Result<Unit> {
    var err: ZError? = null
    val outcome = runCatching {
        block(JniErrorHandler { je -> err = ZError(je ?: "native binding error") })
    }
    err?.let { return Result.failure(it) }
    return outcome
}

/**
 * Fallible flat call returning `T`; [sentinel] runs only on the error path to
 * satisfy the wrapper's return type (its value is discarded).
 */
internal inline fun <T> zCall(
    crossinline sentinel: () -> T,
    crossinline block: (ErrorHandler<T>) -> T
): Result<T> {
    var err: ZError? = null
    val outcome = runCatching {
        block(
            ErrorHandler { je, message ->
                err = ZError(je ?: message)
                sentinel()
            }
        )
    }
    err?.let { return Result.failure(it) }
    return outcome
}

/** Binding-only-fallible flat call returning `T`; [sentinel] as in [zCall]. */
internal inline fun <T> zCall0(
    crossinline sentinel: () -> T,
    crossinline block: (JniErrorHandler<T>) -> T
): Result<T> {
    var err: ZError? = null
    val outcome = runCatching {
        block(
            JniErrorHandler { je ->
                err = ZError(je ?: "native binding error")
                sentinel()
            }
        )
    }
    err?.let { return Result.failure(it) }
    return outcome
}

/** Handler for non-[Result] contexts: throws [ZError] (safe — runs after the
 * native call has returned). Binds `out R` to [Nothing] so one instance
 * satisfies every wrapper. */
internal val throwZError: ErrorHandler<Nothing> =
    ErrorHandler { je, message -> throw ZError(je ?: message) }

/** [throwZError] twin for binding-only-fallible wrappers. */
internal val throwZError0: JniErrorHandler<Nothing> =
    JniErrorHandler { je -> throw ZError(je ?: "native binding error") }

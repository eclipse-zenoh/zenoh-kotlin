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
 * [Result] without any exception in flight.
 *
 * A generated wrapper never throws from native code — on failure it *returns*
 * the value produced by its trailing `onError` handler (a generated typed
 * `fun interface`). The `zCall*` helpers below supply a handler that records
 * the error into a local and, where the wrapper's return type demands a value,
 * produces a throw-away *sentinel* (a born-closed handle such as
 * `Session(0L)`, an empty list, …). After the call returns, the recorded
 * error — if any — becomes `Result.failure`; otherwise the wrapper's return
 * value becomes `Result.success`. No `try`/`catch`, no `runCatching`.
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
internal inline fun zCallUnit(block: (ErrorHandler<Unit>) -> Unit): Result<Unit> {
    var err: ZError? = null
    block(ErrorHandler { je, message -> err = ZError(je ?: message) })
    return err?.let { Result.failure(it) } ?: Result.success(Unit)
}

/** Binding-only-fallible flat call returning `Unit`. */
internal inline fun zCallUnit0(block: (JniErrorHandler<Unit>) -> Unit): Result<Unit> {
    var err: ZError? = null
    block(JniErrorHandler { je -> err = ZError(je ?: "native binding error") })
    return err?.let { Result.failure(it) } ?: Result.success(Unit)
}

/**
 * Fallible flat call returning `T`; [sentinel] runs only on the error path to
 * satisfy the wrapper's return type (its value is discarded).
 */
internal inline fun <T> zCall(
    crossinline sentinel: () -> T,
    block: (ErrorHandler<T>) -> T
): Result<T> {
    var err: ZError? = null
    val value = block(
        ErrorHandler { je, message ->
            err = ZError(je ?: message)
            sentinel()
        }
    )
    return err?.let { Result.failure(it) } ?: Result.success(value)
}

/** Binding-only-fallible flat call returning `T`; [sentinel] as in [zCall]. */
internal inline fun <T> zCall0(
    crossinline sentinel: () -> T,
    block: (JniErrorHandler<T>) -> T
): Result<T> {
    var err: ZError? = null
    val value = block(
        JniErrorHandler { je ->
            err = ZError(je ?: "native binding error")
            sentinel()
        }
    )
    return err?.let { Result.failure(it) } ?: Result.success(value)
}

/** Handler for non-[Result] contexts: throws [ZError] (safe — runs after the
 * native call has returned). Binds `out R` to [Nothing] so one instance
 * satisfies every wrapper. */
internal val throwZError: ErrorHandler<Nothing> =
    ErrorHandler { je, message -> throw ZError(je ?: message) }

/** [throwZError] twin for binding-only-fallible wrappers. */
internal val throwZError0: JniErrorHandler<Nothing> =
    JniErrorHandler { je -> throw ZError(je ?: "native binding error") }

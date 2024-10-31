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

internal enum class Target {
    WINDOWS_X86_64_MSVC,
    WINDOWS_AARCH64_MSVC,
    LINUX_X86_64,
    LINUX_AARCH64,
    APPLE_AARCH64,
    APPLE_X86_64;

    override fun toString(): String {
        return when (this) {
            WINDOWS_X86_64_MSVC -> "x86_64-pc-windows-msvc"
            WINDOWS_AARCH64_MSVC -> "aarch64-pc-windows-msvc"
            LINUX_X86_64 -> "x86_64-unknown-linux-gnu"
            LINUX_AARCH64 -> "aarch64-unknown-linux-gnu"
            APPLE_AARCH64 -> "aarch64-apple-darwin"
            APPLE_X86_64 -> "x86_64-apple-darwin"
        }
    }
}

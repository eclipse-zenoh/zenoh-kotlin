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

use std::sync::Arc;

use jni::{objects::JClass, JNIEnv};
use zenoh::query::Queryable;

/// Frees the [Queryable].
///
/// # Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `queryable_ptr`: The raw pointer to the Zenoh queryable ([Queryable]).
///
/// # Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided queryable pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the queryable pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQueryable_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    queryable_ptr: *const Queryable<'_, ()>,
) {
    Arc::from_raw(queryable_ptr);
}

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

use std::ops::Deref;
use std::sync::Arc;

use jni::objects::JClass;
use jni::sys::{jboolean, jstring};
use jni::{objects::JString, JNIEnv};
use zenoh::key_expr::KeyExpr;

use crate::errors::Error;
use crate::errors::Result;
use crate::utils::decode_string;
use crate::{jni_error, key_expr_error, throw_exception};

/// Validates the provided `key_expr` to be a valid key expression, returning it back
/// in case of success or throwing an exception in case of failure.
///
/// # Parameters:
/// `env`: The JNI environment.
/// `_class`: the Java class (unused).
/// `key_expr`: Java string representation of the intended key expression.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_tryFromViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr: JString,
) -> jstring {
    decode_key_expr(&mut env, &key_expr)
        .map(|_| **key_expr)
        .unwrap_or_else(|err| {
            throw_exception!(env, err);
            JString::default().as_raw()
        })
}

/// Returns a java string representation of the autocanonized version of the provided `key_expr`.
/// In case of failure and exception will be thrown.
///
/// # Parameters:
/// `env`: The JNI environment.
/// `_class`: the Java class (unused).
/// `key_expr`: Java string representation of the intended key expression.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_autocanonizeViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr: JString,
) -> jstring {
    autocanonize_key_expr(&mut env, &key_expr)
        .and_then(|key_expr| {
            env.new_string(key_expr.to_string())
                .map(|kexp| kexp.as_raw())
                .map_err(|err| jni_error!(err))
        })
        .unwrap_or_else(|err| {
            throw_exception!(env, err);
            JString::default().as_raw()
        })
}

/// Returns true in case key_expr_1 intersects key_expr_2.
///
/// # Params:
/// - `key_expr_ptr_1`: Pointer to the key expression 1, differs from null only if it's a declared key expr.
/// - `key_expr_ptr_1`: String representation of the key expression 1.
/// - `key_expr_ptr_2`: Pointer to the key expression 2, differs from null only if it's a declared key expr.
/// - `key_expr_ptr_2`: String representation of the key expression 2.
///
/// # Safety
/// - This function is marked as unsafe due to raw pointer manipulation, which happens only when providing
/// key expressions that were declared from a session (in that case the key expression has a pointer associated).
/// In that case, this function assumes the pointers are valid pointers to key expressions and those pointers
/// remain valid after the call to this function.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_intersectsViaJNI(
    mut env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str_1: JString,
    key_expr_ptr_2: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str_2: JString,
) -> jboolean {
    || -> Result<jboolean> {
        let key_expr_1 = process_kotlin_key_expr(&mut env, &key_expr_str_1, key_expr_ptr_1)?;
        let key_expr_2 = process_kotlin_key_expr(&mut env, &key_expr_str_2, key_expr_ptr_2)?;
        Ok(key_expr_1.intersects(&key_expr_2) as jboolean)
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        false as jboolean
    })
}

/// Returns true in case key_expr_1 includes key_expr_2.
///
/// # Params:
/// - `key_expr_ptr_1`: Pointer to the key expression 1, differs from null only if it's a declared key expr.
/// - `key_expr_ptr_1`: String representation of the key expression 1.
/// - `key_expr_ptr_2`: Pointer to the key expression 2, differs from null only if it's a declared key expr.
/// - `key_expr_ptr_2`: String representation of the key expression 2.
///
/// # Safety
/// - This function is marked as unsafe due to raw pointer manipulation, which happens only when providing
/// key expressions that were declared from a session (in that case the key expression has a pointer associated).
/// In that case, this function assumes the pointers are valid pointers to key expressions and those pointers
/// remain valid after the call to this function.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_includesViaJNI(
    mut env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str_1: JString,
    key_expr_ptr_2: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str_2: JString,
) -> jboolean {
    || -> Result<jboolean> {
        let key_expr_1 = process_kotlin_key_expr(&mut env, &key_expr_str_1, key_expr_ptr_1)?;
        let key_expr_2 = process_kotlin_key_expr(&mut env, &key_expr_str_2, key_expr_ptr_2)?;
        Ok(key_expr_1.includes(&key_expr_2) as jboolean)
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        false as jboolean
    })
}

/// Frees a declared key expression.
///
/// # Parameters
/// - `_env`: Unused. The JNI environment.
/// - `_class`: Unused. The java class from which the function was called.
/// - `key_expr_ptr`: the pointer to the key expression.
///
/// # Safety
/// - This function assumes the provided pointer is valid and points to a native key expression.
/// - The memory associated to the pointer is freed after returning from this call, turning the
///   pointer invalid after that.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    key_expr_ptr: *const KeyExpr<'static>,
) {
    Arc::from_raw(key_expr_ptr);
}

fn decode_key_expr(env: &mut JNIEnv, key_expr: &JString) -> Result<KeyExpr<'static>> {
    let key_expr_str = decode_string(env, key_expr)
        .map_err(|err| jni_error!("Unable to get key expression string value: '{}'.", err))?;

    KeyExpr::try_from(key_expr_str)
        .map_err(|err| key_expr_error!("Unable to create key expression: '{}'.", err))
}

fn autocanonize_key_expr(env: &mut JNIEnv, key_expr: &JString) -> Result<KeyExpr<'static>> {
    decode_string(env, key_expr)
        .map_err(|err| jni_error!("Unable to get key expression string value: '{}'.", err))
        .and_then(|key_expr_str| {
            KeyExpr::autocanonize(key_expr_str)
                .map_err(|err| key_expr_error!("Unable to create key expression: '{}'", err))
        })
}

/// Processes a kotlin key expression.
///
/// Receives the Java/Kotlin string representation of the key expression as well as a pointer.
/// The pointer is only valid in cases where the key expression is associated to a native pointer
/// (when the key expression was declared from a session).
/// If the pointer is valid, the key expression returned is the key expression the pointer pointed to.
/// Otherwise, a key expression created from the string representation of the key expression is returned.
///
pub(crate) unsafe fn process_kotlin_key_expr(
    env: &mut JNIEnv,
    key_expr_str: &JString,
    key_expr_ptr: *const KeyExpr<'static>,
) -> Result<KeyExpr<'static>> {
    if key_expr_ptr.is_null() {
        decode_key_expr(env, key_expr_str)
            .map_err(|err| jni_error!("Unable to process key expression: '{}'.", err))
    } else {
        let key_expr = Arc::from_raw(key_expr_ptr);
        let key_expr_clone = key_expr.deref().clone();
        std::mem::forget(key_expr);
        Ok(key_expr_clone)
    }
}

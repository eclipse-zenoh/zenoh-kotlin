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

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_tryFromViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr: JString,
) -> jstring {
    decode_key_expr(&mut env, &key_expr)
        .and_then(|key_expr| {
            env.new_string(key_expr.to_string())
                .map(|kexp| kexp.as_raw())
                .map_err(|err| Error::KeyExpr(err.to_string()))
        })
        .unwrap_or_else(|err| {
            let _ = err.throw_on_jvm(&mut env);
            JString::default().as_raw()
        })
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_autocanonizeViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr: JString,
) -> jstring {
    autocanonize_key_expr(&mut env, &key_expr)
        .and_then(|key_expr| {
            env.new_string(key_expr.to_string())
                .map(|kexp| kexp.as_raw())
                .map_err(|err| Error::KeyExpr(err.to_string()))
        })
        .unwrap_or_else(|err| {
            let _ = err.throw_on_jvm(&mut env);
            JString::default().as_raw()
        })
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_intersectsViaJNI(
    mut env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: *const KeyExpr<'static>,
    key_expr_str_1: JString,
    key_expr_ptr_2: *const KeyExpr<'static>,
    key_expr_str_2: JString,
) -> jboolean {
    let key_expr_1 =
        match process_kotlin_key_expr_or_throw(&mut env, &key_expr_str_1, key_expr_ptr_1) {
            Ok(key_expr) => key_expr,
            Err(_) => return false as jboolean,
        };
    let key_expr_2 =
        match process_kotlin_key_expr_or_throw(&mut env, &key_expr_str_2, key_expr_ptr_2) {
            Ok(key_expr) => key_expr,
            Err(_) => return false as jboolean,
        };
    let intersects = key_expr_1.intersects(&key_expr_2);
    intersects as jboolean
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_includesViaJNI(
    mut env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: *const KeyExpr<'static>,
    key_expr_str_1: JString,
    key_expr_ptr_2: *const KeyExpr<'static>,
    key_expr_str_2: JString,
) -> jboolean {
    let key_expr_1 =
        match process_kotlin_key_expr_or_throw(&mut env, &key_expr_str_1, key_expr_ptr_1) {
            Ok(key_expr) => key_expr,
            Err(_) => return false as jboolean,
        };
    let key_expr_2 =
        match process_kotlin_key_expr_or_throw(&mut env, &key_expr_str_2, key_expr_ptr_2) {
            Ok(key_expr) => key_expr,
            Err(_) => return false as jboolean,
        };
    key_expr_1.includes(&key_expr_2) as jboolean
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const KeyExpr<'static>,
) {
    Arc::from_raw(ptr);
}

pub(crate) fn decode_key_expr(env: &mut JNIEnv, key_expr: &JString) -> Result<KeyExpr<'static>> {
    let key_expr_str = decode_string(env, key_expr).map_err(|err| {
        Error::Jni(format!(
            "Unable to get key expression string value: {}",
            err
        ))
    })?;

    KeyExpr::try_from(key_expr_str).map_err(|err| {
        Error::Jni(format!(
            "Unable to create key expression from string: {}",
            err
        ))
    })
}

pub(crate) fn autocanonize_key_expr(
    env: &mut JNIEnv,
    key_expr: &JString,
) -> Result<KeyExpr<'static>> {
    decode_string(env, key_expr)
        .map_err(|err| {
            Error::Jni(format!(
                "Unable to get key expression string value: {}",
                err
            ))
        })
        .and_then(|key_expr_str| {
            KeyExpr::autocanonize(key_expr_str).map_err(|err| {
                Error::Jni(format!(
                    "Unable to create key expression from string: {}",
                    err
                ))
            })
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
            .map_err(|err| Error::Jni(format!("Unable to process key expression: {}", err)))
    } else {
        let key_expr = Arc::from_raw(key_expr_ptr);
        let key_expr_clone = key_expr.deref().clone();
        std::mem::forget(key_expr);
        Ok(key_expr_clone)
    }
}

/// Internal helper function that wraps [process_kotlin_key_expr] and throws an Exception through the JVM in
/// case of error.
unsafe fn process_kotlin_key_expr_or_throw(
    env: &mut JNIEnv,
    key_expr_str: &JString,
    key_expr_ptr: *const KeyExpr<'static>,
) -> core::result::Result<KeyExpr<'static>, ()> {
    process_kotlin_key_expr(env, key_expr_str, key_expr_ptr).map_err(|err| {
        let _ = err.throw_on_jvm(env).map_err(|err| {
            tracing::error!(
                "Unable to throw exception while processing key expression: '{}'.",
                err
            )
        });
    })
}

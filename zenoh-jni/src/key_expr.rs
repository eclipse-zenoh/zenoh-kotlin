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

use std::ptr::null;
use std::sync::Arc;

use jni::objects::JClass;
use jni::sys::{jboolean, jstring};
use jni::{objects::JString, JNIEnv};
use zenoh::prelude::KeyExpr;

use crate::errors::Error;
use crate::utils::decode_string;

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_tryFromViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr: JString,
) -> *const KeyExpr<'static> {
    let key_expr_str = match decode_string(&mut env, key_expr) {
        Ok(key_expr) => key_expr,
        Err(err) => {
            _ = err.throw_on_jvm(&mut env);
            return null();
        }
    };
    let key_expr = match KeyExpr::try_from(key_expr_str) {
        Ok(key_expr) => key_expr,
        Err(err) => {
            _ = Error::KeyExpr(err.to_string()).throw_on_jvm(&mut env);
            return null();
        }
    };
    Arc::into_raw(Arc::new(key_expr))
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_00024Companion_autocanonizeViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    key_expr: JString,
) -> *const KeyExpr<'static> {
    let key_expr_str = match decode_string(&mut env, key_expr) {
        Ok(key_expr) => key_expr,
        Err(err) => {
            _ = err.throw_on_jvm(&mut env);
            return null();
        }
    };
    let key_expr = match KeyExpr::autocanonize(key_expr_str) {
        Ok(key_expr) => key_expr,
        Err(err) => {
            _ = Error::KeyExpr(err.to_string()).throw_on_jvm(&mut env);
            return null();
        }
    };
    Arc::into_raw(Arc::new(key_expr))
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_getStringValueViaJNI(
    mut env: JNIEnv,
    _: JClass,
    ptr: *const KeyExpr<'static>,
) -> jstring {
    let key_expr = Arc::from_raw(ptr);
    let key_expr_str = match env.new_string(key_expr.to_string()) {
        Ok(key_expr) => key_expr,
        Err(err) => {
            _ = Error::Jni(format!(
                "Unable to get key expression string value: {}",
                err
            ))
            .throw_on_jvm(&mut env);
            std::mem::forget(key_expr);
            return JString::default().as_raw();
        }
    };
    std::mem::forget(key_expr);
    key_expr_str.as_raw()
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_intersectsViaJNI(
    _env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: *const KeyExpr<'static>,
    key_expr_ptr_2: *const KeyExpr<'static>,
) -> jboolean {
    let key_expr_1 = Arc::from_raw(key_expr_ptr_1);
    let key_expr_2 = Arc::from_raw(key_expr_ptr_2);
    let intersects = key_expr_1.intersects(&key_expr_2);
    std::mem::forget(key_expr_1);
    std::mem::forget(key_expr_2);
    intersects as jboolean
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_includesViaJNI(
    _env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: *const KeyExpr<'static>,
    key_expr_ptr_2: *const KeyExpr<'static>,
) -> jboolean {
    let key_expr_1 = Arc::from_raw(key_expr_ptr_1);
    let key_expr_2 = Arc::from_raw(key_expr_ptr_2);
    let includes = key_expr_1.includes(&key_expr_2);
    std::mem::forget(key_expr_1);
    std::mem::forget(key_expr_2);
    includes as jboolean
}

#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIKeyExpr_equalsViaJNI(
    _env: JNIEnv,
    _: JClass,
    key_expr_ptr_1: *const KeyExpr<'static>,
    key_expr_ptr_2: *const KeyExpr<'static>,
) -> jboolean {
    let key_expr_1 = Arc::from_raw(key_expr_ptr_1);
    let key_expr_2 = Arc::from_raw(key_expr_ptr_2);
    let is_equal = key_expr_1.eq(&key_expr_2);
    std::mem::forget(key_expr_1);
    std::mem::forget(key_expr_2);
    is_equal as jboolean
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

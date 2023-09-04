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

use std::{ops::Deref, sync::Arc};

use jni::{
    objects::{JByteArray, JClass},
    sys::jint,
    JNIEnv,
};
use zenoh::{
    prelude::{sync::SyncResolve, KeyExpr},
    publication::Publisher,
    Session,
};

use crate::{
    errors::{Error, Result},
    sample::decode_sample_kind,
};
use crate::{
    put::{decode_congestion_control, decode_priority},
    value::decode_value,
};
use zenoh::SessionDeclarations;

/// Performs a put operation on a Zenoh publisher via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `payload`: The payload to be published, represented as a Java byte array (`JByteArray`).
/// - `encoding`: The encoding type of the payload.
/// - `ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The ownership of the publisher is not transferred, and it is safe to continue using the publisher
///   after this function call.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_putViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
    encoding: jint,
    ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(ptr);
    match perform_put(&env, payload, encoding, publisher.clone()) {
        Ok(_) => {}
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                log::error!(
                    "Unable to throw exception on PUT operation failure: {}",
                    err
                )
            });
        }
    };
    std::mem::forget(publisher)
}

/// Frees the memory associated with a Zenoh publisher raw pointer via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the publisher pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    ptr: *const Publisher,
) {
    Arc::from_raw(ptr);
}

/// Declares a Zenoh publisher via JNI.
///
/// Parameters:
/// - `key_expr_ptr`: Raw pointer to the key expression to be used for the publisher.
/// - `session_ptr`: Raw pointer to the Zenoh [Session] to be used for the publisher.
/// - `congestion_control`: The [zenoh::CongestionControl] configuration as an ordinal.
/// - `priority`: The [zenoh::Priority] configuration as an ordinal.
///
/// Returns:
/// - A [Result] containing a raw pointer to the declared Zenoh publisher ([Publisher]) in case of success,
///   or an [Error] variant in case of failure.
///
/// Safety:
/// - The returned raw pointer should be stored appropriately and later freed using [Java_io_zenoh_jni_JNIPublisher_freePtrViaJNI].
///
pub(crate) unsafe fn declare_publisher(
    key_expr_ptr: *const KeyExpr<'static>,
    session_ptr: *const Session,
    congestion_control: jint,
    priority: jint,
) -> Result<*const Publisher<'static>> {
    let session = Arc::from_raw(session_ptr);
    let key_expr = Arc::from_raw(key_expr_ptr);
    let key_expr_clone = key_expr.deref().clone();
    let congestion_control = decode_congestion_control(congestion_control)?;
    let priority = decode_priority(priority)?;
    let result = session
        .declare_publisher(key_expr_clone.to_owned())
        .congestion_control(congestion_control)
        .priority(priority)
        .res();
    std::mem::forget(session);
    std::mem::forget(key_expr);
    match result {
        Ok(publisher) => {
            log::trace!("Declared publisher ok key expr '{key_expr_clone}', with congestion control '{congestion_control:?}', priority '{priority:?}'.");
            Ok(Arc::into_raw(Arc::new(publisher)))
        }
        Err(err) => Err(Error::Session(err.to_string())),
    }
}

/// Performs a PUT operation via JNI using the specified Zenoh publisher.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `payload`: The payload as a `JByteArray`.
/// - `encoding`: The encoding of the payload.
/// - `publisher`: The Zenoh publisher.
///
/// Returns:
/// - A [Result] indicating the success or failure of the operation.
///
fn perform_put(
    env: &JNIEnv,
    payload: JByteArray,
    encoding: jint,
    publisher: Arc<Publisher>,
) -> Result<()> {
    let value = decode_value(env, payload, encoding)?;
    publisher
        .put(value)
        .res_sync()
        .map_err(|err| Error::Session(err.to_string()))
}

/// Modifies the congestion control policy of a running Publisher through JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Publisher class (unused but required).
/// - `congestion_control`: The [zenoh::CongestionControl] value to be set.
/// - `ptr`: Pointer to the publisher.
///
/// Safety:
/// - This function is maked as unsafe due to raw pointer manipulation.
/// - This function is NOT thread safe; if there were to be multiple threads calling this function
///   concurrently while providing the same Publisher pointer, the result will be non deterministic.
///  
/// Throws:
/// - An exception in case the congestion control fails to be decoded.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_setCongestionControlViaJNI(
    env: &mut JNIEnv,
    _class: JClass,
    congestion_control: jint,
    ptr: *const Publisher<'static>,
) {
    let congestion_control = match decode_congestion_control(congestion_control) {
        Ok(congestion_control) => congestion_control,
        Err(err) => {
            _ = err.throw_on_jvm(env);
            return;
        }
    };
    log::debug!("Setting publisher congestion control with '{congestion_control:?}'.");
    unsafe {
        let publisher = core::ptr::read(ptr);
        core::ptr::write(
            ptr as *mut _,
            publisher.congestion_control(congestion_control),
        );
    }
}

/// Modifies the priority policy of a running Publisher through JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The Publisher class (unused but required).
/// - `priority`: The [zenoh::Priority] value to be set.
/// - `ptr`: Pointer to the publisher.
///
/// Safety:
/// - This function is maked as unsafe due to raw pointer manipulation.
/// - This function is NOT thread safe; if there were to be multiple threads calling this function
///   concurrently while providing the same Publisher pointer, the result will be non deterministic.
///  
/// Throws:
/// - An exception in case the priority fails to be decoded.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_setPriorityViaJNI(
    env: &mut JNIEnv,
    _class: JClass,
    priority: jint,
    ptr: *const Publisher<'static>,
) {
    let priority = match decode_priority(priority) {
        Ok(priority) => priority,
        Err(err) => {
            _ = err.throw_on_jvm(env);
            return;
        }
    };
    log::debug!("Setting publisher priority with '{priority:?}'.");
    unsafe {
        let publisher = core::ptr::read(ptr);
        core::ptr::write(ptr as *mut _, publisher.priority(priority));
    }
}

/// Performs a WRITE operation via JNI using the specified Zenoh publisher.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `payload`: The payload as a `JByteArray`.
/// - `encoding`: The [zenoh::Encoding] of the payload.
/// - `sample_kind`: The [zenoh::SampleKind] to use.
/// - `publisher`: The Zenoh [Publisher].
///
/// Returns:
/// - A [Result] indicating the success or failure of the operation.
///
fn perform_write(
    env: &JNIEnv,
    payload: JByteArray,
    encoding: jint,
    sample_kind: jint,
    publisher: Arc<Publisher>,
) -> Result<()> {
    let value = decode_value(env, payload, encoding)?;
    let sample_kind = decode_sample_kind(sample_kind)?;
    publisher
        .write(sample_kind, value)
        .res()
        .map_err(|err| Error::Session(format!("{}", err)))
}

/// Performs a WRITE operation on a Zenoh publisher via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `payload`: The payload to be published, represented as a [Java byte array](JByteArray).
/// - `encoding`: The [`encoding`](zenoh::Encoding) of the payload.
/// - `sample_kind`: The [`kind`](zenoh::SampleKind) to use.
/// - `ptr`: The raw pointer to the Zenoh publisher ([Publisher]).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The ownership of the publisher is not transferred, and it is safe to continue using the publisher
///   after this function call.
/// - The function may throw an exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_writeViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    payload: JByteArray,
    encoding: jint,
    sample_kind: jint,
    ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(ptr);
    match perform_write(&env, payload, encoding, sample_kind, publisher.clone()) {
        Ok(_) => {}
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                log::error!(
                    "Unable to throw exception on WRITE operation failure: {}",
                    err
                )
            });
        }
    };
    std::mem::forget(publisher)
}

/// Performs a DELETE operation via JNI using the specified Zenoh publisher.
///
/// Parameters:
/// - `publisher`: The Zenoh [Publisher].
///
/// Returns:
/// - A [Result] indicating the success or failure of the operation.
///
fn perform_delete(publisher: Arc<Publisher>) -> Result<()> {
    publisher
        .delete()
        .res()
        .map_err(|err| Error::Session(format!("{}", err)))
}

/// Performs a DELETE operation on a Zenoh publisher via JNI.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `ptr`: The raw pointer to the [Zenoh publisher](Publisher).
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided publisher pointer is valid and has not been modified or freed.
/// - The ownership of the publisher is not transferred, and it is safe to continue using the publisher
///   after this function call.
/// - The function may throw an exception in case of failure, which should be handled by the caller.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIPublisher_deleteViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    ptr: *const Publisher<'static>,
) {
    let publisher = Arc::from_raw(ptr);
    match perform_delete(publisher.clone()) {
        Ok(_) => {}
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                log::error!(
                    "Unable to throw exception on WRITE operation failure: {}",
                    err
                )
            });
        }
    };
    std::mem::forget(publisher)
}

//
// Copyright (c) 2025 ZettaScale Technology
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

use jni::sys::jboolean;
use jni::{objects::JClass, JNIEnv};
use zenoh::pubsub::Subscriber;
use zenoh_ext::AdvancedSubscriber;
use zenoh_ext::SampleMissListener;

use crate::sample_callback::SetJniSampleCallback;
use jni::objects::JObject;

use crate::errors::ZResult;
use jni::objects::JValue;
use zenoh::Wait;

use crate::owned_object::OwnedObject;

use crate::utils::{get_callback_global_ref, get_java_vm, load_on_close};
use crate::zerror;
use std::ptr::null;

use crate::throw_exception;

/// Declares a subscriber to detect matching publishers for an [AdvancedSubscriber] via JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `advanced_subscriber_ptr`: The raw pointer to the [AdvancedSubscriber].
/// - `callback`: The callback function as an instance of the `JNISubscriberCallback` interface in Java/Kotlin.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called upon closing the subscriber.
///
/// Returns:
/// - A raw pointer to the declared [Subscriber]. In case of failure, an exception is thrown and null is returned.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided [AdvancedSubscriber] pointer is valid and has not been modified or freed.
/// - The [AdvancedSubscriber] pointer remains valid and the ownership of the [AdvancedSubscriber] is not transferred,
///   allowing safe usage of the [AdvancedSubscriber] after this function call.
/// - The callback function passed as `callback` must be a valid instance of the `JNISubscriberCallback` interface
///   in Java/Kotlin, matching the specified signature.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[cfg(feature = "zenoh-ext")]
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIAdvancedSubscriber_declareDetectPublishersSubscriberViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    advanced_subscriber_ptr: *const AdvancedSubscriber<()>,
    history: jboolean,
    callback: JObject,
    on_close: JObject,
) -> *const Subscriber<()> {
    let advanced_subscriber = OwnedObject::from_raw(advanced_subscriber_ptr);

    || -> ZResult<*const Subscriber<()>> {
        tracing::debug!(
            "Declaring detect publishers subscriber on '{}'...",
            advanced_subscriber.key_expr()
        );

        let detect_publishers_subscriber = advanced_subscriber
            .detect_publishers()
            .history(history != 0)
            .set_jni_sample_callback(&mut env, callback, on_close)?
            .wait()
            .map_err(|err| zerror!("Unable to declare detect publishers subscriber: {}", err))?;

        tracing::debug!(
            "Detect publishers subscriber declared on '{}'...",
            advanced_subscriber.key_expr()
        );
        Ok(Arc::into_raw(Arc::new(detect_publishers_subscriber)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}

/// Declares a background subscriber to detect matching publishers for an [AdvancedSubscriber] via JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `advanced_subscriber_ptr`: The raw pointer to the [AdvancedSubscriber].
/// - `callback`: The callback function as an instance of the `JNISubscriberCallback` interface in Java/Kotlin.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called upon closing the subscriber.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided [AdvancedSubscriber] pointer is valid and has not been modified or freed.
/// - The [AdvancedSubscriber] pointer remains valid and the ownership of the [AdvancedSubscriber] is not transferred,
///   allowing safe usage of the [AdvancedSubscriber] after this function call.
/// - The callback function passed as `callback` must be a valid instance of the `JNISubscriberCallback` interface
///   in Java/Kotlin, matching the specified signature.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[cfg(feature = "zenoh-ext")]
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIAdvancedSubscriber_declareBackgroundDetectPublishersSubscriberViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    advanced_subscriber_ptr: *const AdvancedSubscriber<()>,
    history: jboolean,
    callback: JObject,
    on_close: JObject,
) {
    let advanced_subscriber = OwnedObject::from_raw(advanced_subscriber_ptr);

    || -> ZResult<()> {
        tracing::debug!(
            "Declaring detect publishers subscriber on '{}'...",
            advanced_subscriber.key_expr()
        );

        advanced_subscriber
            .detect_publishers()
            .history(history != 0)
            .set_jni_sample_callback(&mut env, callback, on_close)?
            .background()
            .wait()
            .map_err(|err| zerror!("Unable to declare detect publishers subscriber: {}", err))?;

        tracing::debug!(
            "Detect publishers subscriber declared on '{}'...",
            advanced_subscriber.key_expr()
        );
        Ok(())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
    });
}

/// Declares a [SampleMissListener] to detect missed samples for an [AdvancedSubscriber] via JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `advanced_subscriber_ptr`: The raw pointer to the [AdvancedSubscriber].
/// - `callback`: The callback function as an instance of the `JNISampleMissListenerCallback` interface in Java/Kotlin.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called upon closing the subscriber.
///
/// Returns:
/// - A raw pointer to the declared [SampleMissListener]. In case of failure, an exception is thrown and null is returned.
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided [AdvancedSubscriber] pointer is valid and has not been modified or freed.
/// - The [AdvancedSubscriber] pointer remains valid and the ownership of the [AdvancedSubscriber] is not transferred,
///   allowing safe usage of the [AdvancedSubscriber] after this function call.
/// - The callback function passed as `callback` must be a valid instance of the `JNISampleMissListenerCallback` interface
///   in Java/Kotlin, matching the specified signature.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[cfg(feature = "zenoh-ext")]
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIAdvancedSubscriber_declareSampleMissListenerViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    advanced_subscriber_ptr: *const AdvancedSubscriber<()>,

    callback: JObject,
    on_close: JObject,
) -> *const SampleMissListener<()> {
    let advanced_subscriber = OwnedObject::from_raw(advanced_subscriber_ptr);

    || -> ZResult<*const SampleMissListener<()>> {
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);

        tracing::debug!(
            "Declaring sample miss listener on '{}'...",
            advanced_subscriber.key_expr()
        );

        let result = advanced_subscriber
            .sample_miss_listener()
            .callback(move |miss| {
                on_close.noop(); // Moves `on_close` inside the closure so it gets destroyed with the closure
                let _ = || -> ZResult<()> {
                    let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                        zerror!("Unable to attach thread for sample miss listener: {}", err)
                    })?;

                    let (zid_lower, zid_upper, eid) = {
                        let id = miss.source();

                        let zid = id.zid().to_le_bytes();
                        let zid_lower = i64::from_le_bytes(zid[0..8].try_into().unwrap());
                        let zid_upper = i64::from_le_bytes(zid[8..16].try_into().unwrap());

                        (zid_lower, zid_upper, id.eid())
                    };
                    let missed_count = miss.nb();

                    env.call_method(
                        &callback_global_ref,
                        "run",
                        "(JJJJ)V",
                        &[
                            JValue::from(zid_lower),
                            JValue::from(zid_upper),
                            JValue::from(eid as i64),
                            JValue::from(missed_count as i64),
                        ],
                    )
                    .map_err(|err| zerror!(err))?;
                    Ok(())
                }()
                .map_err(|err| tracing::error!("On sample miss listener callback error: {err}"));
            })
            .wait();

        let sample_miss_listener =
            result.map_err(|err| zerror!("Unable to declare sample miss listener: {}", err))?;

        tracing::debug!(
            "Matching listener declared on '{}'...",
            advanced_subscriber.key_expr()
        );
        Ok(Arc::into_raw(Arc::new(sample_miss_listener)))
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
        null()
    })
}

/// Declare a background sample miss listener for [AdvancedSubscriber] via JNI.
/// Register the listener callback to be run in background until the [AdvancedSubscriber] is undeclared.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `advanced_subscriber_ptr`: The raw pointer to an [AdvancedSubscriber].
/// - `callback`: The callback function as an instance of the `JNISampleMissListenerCallback` interface in Java/Kotlin.
/// - `on_close`: A Java/Kotlin `JNIOnCloseCallback` function interface to be called upon undeclaring the [AdvancedSubscriber].
///
/// Safety:
/// - The function is marked as unsafe due to raw pointer manipulation and JNI interaction.
/// - It assumes that the provided [AdvancedSubscriber] pointer is valid and has not been modified or freed.
/// - The [AdvancedSubscriber] pointer remains valid and the ownership of the [AdvancedSubscriber] is not transferred,
///   allowing safe usage of the [AdvancedSubscriber] after this function call.
/// - The callback function passed as `callback` must be a valid instance of the `JNISampleMissListenerCallback` interface
///   in Java/Kotlin, matching the specified signature.
/// - The function may throw a JNI exception in case of failure, which should be handled by the caller.
///
#[cfg(feature = "zenoh-ext")]
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIAdvancedSubscriber_declareBackgroundSampleMissListenerViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    advanced_subscriber_ptr: *const AdvancedSubscriber<()>,

    callback: JObject,
    on_close: JObject,
) {
    let advanced_subscriber = OwnedObject::from_raw(advanced_subscriber_ptr);

    || -> ZResult<()> {
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);

        tracing::debug!(
            "Declaring background sample miss listener on '{}'...",
            advanced_subscriber.key_expr()
        );

        advanced_subscriber
            .sample_miss_listener()
            .callback(move |miss| {
                on_close.noop(); // Moves `on_close` inside the closure so it gets destroyed with the closure
                let _ = || -> ZResult<()> {
                    let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                        zerror!(
                            "Unable to attach thread for background sample miss listener: {}",
                            err
                        )
                    })?;

                    let (zid_lower, zid_upper, eid) = {
                        let id = miss.source();

                        let zid = id.zid().to_le_bytes();
                        let zid_lower = i64::from_le_bytes(zid[0..8].try_into().unwrap());
                        let zid_upper = i64::from_le_bytes(zid[8..16].try_into().unwrap());

                        (zid_lower, zid_upper, id.eid())
                    };
                    let missed_count = miss.nb();

                    env.call_method(
                        &callback_global_ref,
                        "run",
                        "(JJJJ)V",
                        &[
                            JValue::from(zid_lower),
                            JValue::from(zid_upper),
                            JValue::from(eid as i64),
                            JValue::from(missed_count as i64),
                        ],
                    )
                    .map_err(|err| zerror!(err))?;
                    Ok(())
                }()
                .map_err(|err| {
                    tracing::error!("On subscriber background sample miss listener error: {err}")
                });
            })
            .background()
            .wait()
            .map_err(|err| zerror!("Unable to declare background sample miss listener: {}", err))?;

        tracing::debug!(
            "Background sample miss listener declared on '{}'...",
            advanced_subscriber.key_expr()
        );
        Ok(())
    }()
    .unwrap_or_else(|err| {
        throw_exception!(env, err);
    })
}

/// Frees the [AdvancedSubscriber].
///
/// # Parameters:
/// - `_env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `subscriber_ptr`: The raw pointer to the [AdvancedSubscriber].
///
/// # Safety:
/// - The function is marked as unsafe due to raw pointer manipulation.
/// - It assumes that the provided [AdvancedSubscriber] pointer is valid and has not been modified or freed.
/// - The function takes ownership of the raw pointer and releases the associated memory.
/// - After calling this function, the [AdvancedSubscriber] pointer becomes invalid and should not be used anymore.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIAdvancedSubscriber_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    subscriber_ptr: *const AdvancedSubscriber<()>,
) {
    Arc::from_raw(subscriber_ptr);
}

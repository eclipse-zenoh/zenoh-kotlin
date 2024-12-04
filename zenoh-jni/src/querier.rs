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

use jni::{
    objects::{JByteArray, JClass, JObject, JString},
    sys::jint,
    JNIEnv,
};
use zenoh::{key_expr::KeyExpr, query::Querier, Wait};

use crate::{
    errors::ZResult,
    key_expr::process_kotlin_key_expr,
    session::{on_reply_error, on_reply_success},
    throw_exception,
    utils::{
        decode_byte_array, decode_encoding, decode_string, get_callback_global_ref, get_java_vm,
        load_on_close,
    },
    zerror,
};

/// Perform a Zenoh GET through a querier.
///
/// This function is meant to be called from Java/Kotlin code through JNI.
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `querier_ptr`: The raw pointer to the querier.
/// - `key_expr_ptr`: A raw pointer to the [KeyExpr] provided to the kotlin querier. May be null in case of using an
///     undeclared key expression.
/// - `key_expr_str`: String representation of the key expression used during the querier declaration.
///     It won't be considered in case a key_expr_ptr to a declared key expression is provided.
/// - `selector_params`: Optional selector parameters for the query.
/// - `callback`: Reference to the Kotlin callback to be run upon receiving a reply.
/// - `on_close`: Reference to a kotlin callback to be run upon finishing the get operation, mostly used for closing a provided channel.
/// - `attachment`: Optional attachment.
/// - `payload`: Optional payload for the query.
/// - `encoding_id`: Encoding id of the payload provided.
/// - `encoding_schema`: Encoding schema of the payload provided.
///
#[no_mangle]
#[allow(non_snake_case)]
pub unsafe extern "C" fn Java_io_zenoh_jni_JNIQuerier_getViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    querier_ptr: *const Querier,
    key_expr_ptr: /*nullable*/ *const KeyExpr<'static>,
    key_expr_str: JString,
    selector_params: /*nullable*/ JString,
    callback: JObject,
    on_close: JObject,
    attachment: /*nullable*/ JByteArray,
    payload: /*nullable*/ JByteArray,
    encoding_id: jint,
    encoding_schema: /*nullable*/ JString,
) {
    let querier = Arc::from_raw(querier_ptr);
    let _ = || -> ZResult<()> {
        let key_expr = process_kotlin_key_expr(&mut env, &key_expr_str, key_expr_ptr)?;
        let java_vm = Arc::new(get_java_vm(&mut env)?);
        let callback_global_ref = get_callback_global_ref(&mut env, callback)?;
        let on_close_global_ref = get_callback_global_ref(&mut env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);
        let mut get_builder = querier.get().callback(move |reply| {
            || -> ZResult<()> {
                on_close.noop(); // Does nothing, but moves `on_close` inside the closure so it gets destroyed with the closure
                tracing::debug!("Receiving reply through JNI: {:?}", reply);
                let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                    zerror!("Unable to attach thread for GET query callback: {}.", err)
                })?;

                match reply.result() {
                    Ok(sample) => {
                        on_reply_success(&mut env, reply.replier_id(), sample, &callback_global_ref)
                    }
                    Err(error) => {
                        on_reply_error(&mut env, reply.replier_id(), error, &callback_global_ref)
                    }
                }
            }()
            .unwrap_or_else(|err| tracing::error!("Error on get callback: {err}"));
        });

        if !selector_params.is_null() {
            let params = decode_string(&mut env, &selector_params)?;
            get_builder = get_builder.parameters(params)
        };

        if !payload.is_null() {
            let encoding = decode_encoding(&mut env, encoding_id, &encoding_schema)?;
            get_builder = get_builder.encoding(encoding);
            get_builder = get_builder.payload(decode_byte_array(&env, payload)?);
        }

        if !attachment.is_null() {
            let attachment = decode_byte_array(&env, attachment)?;
            get_builder = get_builder.attachment::<Vec<u8>>(attachment);
        }

        get_builder
            .wait()
            .map(|_| tracing::trace!("Performing get on '{key_expr}'.",))
            .map_err(|err| zerror!(err))
    }()
    .map_err(|err| throw_exception!(env, err));
    std::mem::forget(querier);
}

///
/// Frees the pointer of the querier.
///
/// After a call to this function, no further jni operations should be performed using the querier associated to the raw pointer provided.
///
#[no_mangle]
#[allow(non_snake_case)]
pub(crate) unsafe extern "C" fn Java_io_zenoh_jni_JNIQuerier_freePtrViaJNI(
    _env: JNIEnv,
    _: JClass,
    querier_ptr: *const Querier<'static>,
) {
    Arc::from_raw(querier_ptr);
}

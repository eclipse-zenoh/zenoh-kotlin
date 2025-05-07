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
    objects::{JByteArray, JObject, JString, JValue},
    sys::jint,
    JNIEnv,
};
use zenoh::{
    handlers::{Callback, DefaultHandler},
    liveliness::LivelinessSubscriberBuilder,
    pubsub::SubscriberBuilder,
    sample::Sample,
};

use crate::{errors::ZResult, utils::*, zerror};

pub(crate) trait SetJniSampleCallback: Sized + HasSampleCallbackSetter {
    unsafe fn set_jni_sample_callback(
        self,
        env: &mut JNIEnv,
        callback: JObject,
        on_close: JObject,
    ) -> ZResult<Self::BuilderWithCallback> {
        let java_vm = Arc::new(get_java_vm(env)?);
        let callback_global_ref = get_callback_global_ref(env, callback)?;
        let on_close_global_ref = get_callback_global_ref(env, on_close)?;
        let on_close = load_on_close(&java_vm, on_close_global_ref);

        let builder = self.set_callback(move |sample: Sample| {
            on_close.noop(); // Moves `on_close` inside the closure so it gets destroyed with the closure
            let _ = || -> ZResult<()> {
                let mut env = java_vm.attach_current_thread_as_daemon().map_err(|err| {
                    zerror!("Unable to attach thread for sample callback: {}", err)
                })?;
                let byte_array = bytes_to_java_array(&env, sample.payload())
                    .map(|array| env.auto_local(array))?;

                let encoding_id: jint = sample.encoding().id() as jint;
                let encoding_schema = match sample.encoding().schema() {
                    Some(schema) => slice_to_java_string(&env, schema)?,
                    None => JString::default(),
                };
                let kind = sample.kind() as jint;
                let (timestamp, is_valid) = sample
                    .timestamp()
                    .map(|timestamp| (timestamp.get_time().as_u64(), true))
                    .unwrap_or((0, false));

                let attachment_bytes = sample
                    .attachment()
                    .map_or_else(
                        || Ok(JByteArray::default()),
                        |attachment| bytes_to_java_array(&env, attachment),
                    )
                    .map(|array| env.auto_local(array))
                    .map_err(|err| zerror!("Error processing attachment: {}", err))?;

                let key_expr_str = env.auto_local(
                    env.new_string(sample.key_expr().to_string())
                        .map_err(|err| zerror!("Error processing sample key expr: {}", err))?,
                );

                let express = sample.express();
                let priority = sample.priority() as jint;
                let cc = sample.congestion_control() as jint;

                env.call_method(
                    &callback_global_ref,
                    "run",
                    "(Ljava/lang/String;[BILjava/lang/String;IJZ[BZII)V",
                    &[
                        JValue::from(&key_expr_str),
                        JValue::from(&byte_array),
                        JValue::from(encoding_id),
                        JValue::from(&encoding_schema),
                        JValue::from(kind),
                        JValue::from(timestamp as i64),
                        JValue::from(is_valid),
                        JValue::from(&attachment_bytes),
                        JValue::from(express),
                        JValue::from(priority),
                        JValue::from(cc),
                    ],
                )
                .map_err(|err| zerror!(err))?;
                Ok(())
            }()
            .map_err(|err| tracing::error!("On sample callback error: {err}"));
        });
        Ok(builder)
    }
}

impl<T: HasSampleCallbackSetter> SetJniSampleCallback for T {}

pub(crate) trait HasSampleCallbackSetter {
    type BuilderWithCallback;

    fn set_callback<F>(self, callback: F) -> Self::BuilderWithCallback
    where
        F: Fn(Sample) + Send + Sync + 'static;
}

impl<'a, 'b> HasSampleCallbackSetter for SubscriberBuilder<'a, 'b, DefaultHandler> {
    type BuilderWithCallback = SubscriberBuilder<'a, 'b, Callback<Sample>>;

    fn set_callback<F>(self, callback: F) -> Self::BuilderWithCallback
    where
        F: Fn(Sample) + Send + Sync + 'static,
    {
        self.callback(callback)
    }
}

impl<'a, 'b> HasSampleCallbackSetter for LivelinessSubscriberBuilder<'a, 'b, DefaultHandler> {
    type BuilderWithCallback = LivelinessSubscriberBuilder<'a, 'b, Callback<Sample>>;

    fn set_callback<F>(self, callback: F) -> Self::BuilderWithCallback
    where
        F: Fn(Sample) + Send + Sync + 'static,
    {
        self.callback(callback)
    }
}

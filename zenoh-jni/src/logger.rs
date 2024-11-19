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

use jni::{
    objects::{JClass, JString},
    JNIEnv,
};

use crate::{errors::ZResult, throw_exception, zerror};

/// Redirects the Rust logs either to logcat for Android systems or to the standard output (for non-Android systems).
///
/// This function is meant to be called from Java/Kotlin code through JNI. It takes a `filter`
/// indicating the desired log level.
///
/// See https://docs.rs/env_logger/latest/env_logger/index.html for accepted filter format.
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `filter`: The logs filter.
///
/// # Errors:
/// - If there is an error parsing the log level string, a `JNIException` is thrown on the JVM.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_Logger_00024Companion_startLogsViaJNI(
    mut env: JNIEnv,
    _class: JClass,
    filter: JString,
) {
    || -> ZResult<()> {
        let log_level = parse_filter(&mut env, filter)?;
        #[cfg(target_os = "android")]
        {
            android_logd_logger::builder()
                .parse_filters(log_level.as_str())
                .tag_target_strip()
                .prepend_module(true)
                .pstore(false)
                .init();
        }

        #[cfg(not(target_os = "android"))]
        {
            android_logd_logger::builder()
                .parse_filters(log_level.as_str())
                .tag_target_strip()
                .prepend_module(true)
                .init();
        }

        Ok(())
    }()
    .unwrap_or_else(|err| throw_exception!(env, err))
}

fn parse_filter(env: &mut JNIEnv, log_level: JString) -> ZResult<String> {
    let log_level = env.get_string(&log_level).map_err(|err| zerror!(err))?;
    log_level
        .to_str()
        .map(|level| Ok(level.to_string()))
        .map_err(|err| zerror!(err))?
}

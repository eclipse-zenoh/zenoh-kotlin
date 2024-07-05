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

use crate::{
    errors::{Error, Result},
    jni_error, throw_exception,
};

/// Redirects the Rust logs either to logcat for Android systems or to the standard output (for non-Android systems).
///
/// This function is meant to be called from Java/Kotlin code through JNI. It takes a `log_level`
/// indicating the desired log level, which must be one of the following: "info", "debug", "warn",
/// "trace", or "error".
///
/// # Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `log_level`: The log level java string indicating the desired log level.
///
/// # Errors:
/// - If there is an error parsing the log level string, a `JNIException` is thrown on the JVM.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_Logger_00024Companion_start(
    mut env: JNIEnv,
    _class: JClass,
    log_level: JString,
) {
    || -> Result<()> {
        let log_level = parse_log_level(&mut env, log_level)?;
        android_logd_logger::builder()
            .parse_filters(log_level.as_str())
            .tag_target_strip()
            .prepend_module(true)
            .init();
        Ok(())
    }()
    .unwrap_or_else(|err| throw_exception!(env, err))
}

fn parse_log_level(env: &mut JNIEnv, log_level: JString) -> Result<String> {
    let log_level = env.get_string(&log_level).map_err(|err| jni_error!(err))?;
    log_level
        .to_str()
        .map(|level| Ok(level.to_string()))
        .map_err(|err| jni_error!(err))?
}

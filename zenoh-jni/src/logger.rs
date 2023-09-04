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

use crate::errors::{Error, Result};

/// Redirects the Rust logs either to logcat for Android systems or to the standard output (for non-Android systems).
///
/// This function is meant to be called from Java/Kotlin code through JNI. It takes a `log_level`
/// indicating the desired log level, which must be one of the following: "info", "debug", "warn",
/// "trace", or "error".
///
/// Parameters:
/// - `env`: The JNI environment.
/// - `_class`: The JNI class.
/// - `log_level`: The log level java string indicating the desired log level.
///
/// Errors:
/// - If there is an error parsing the log level string, a `JNIException` is thrown on the JVM.
///
#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_io_zenoh_Logger_00024Companion_start(
    mut env: JNIEnv,
    _class: JClass,
    log_level: JString,
) {
    let log_level = match parse_log_level(&mut env, log_level) {
        Ok(level) => level,
        Err(err) => {
            _ = err.throw_on_jvm(&mut env).map_err(|err| {
                log::error!("Error throwing exception on log start failure! {}", err)
            });
            return;
        }
    };
    android_logd_logger::builder()
        .parse_filters(log_level.as_str())
        .tag_target_strip()
        .prepend_module(true)
        .init();
}

/// Parses the log level string from the JNI environment into a Rust String.
///
/// This function takes a mutable reference to the JNI environment (`env`) and a `log_level`
/// indicating the desired log level as a JString. It retrieves the log level string from the JNI
/// environment and converts it into a Rust String.
///
/// Parameters:
/// - `env`: A mutable reference to the JNI environment.
/// - `log_level`: The log level as a JString.
///
/// Returns:
/// - A [Result] containing the parsed log level string as a [String].
///
/// Errors:
/// - If there is an error retrieving or converting the log level string, a [Error::Jni] is returned
///   with the error message.
///
fn parse_log_level(env: &mut JNIEnv, log_level: JString) -> Result<String> {
    let log_level = env
        .get_string(&log_level)
        .map_err(|err| Error::Jni(err.to_string()))?;
    log_level
        .to_str()
        .map(|level| Ok(level.to_string()))
        .map_err(|err| Error::Jni(err.to_string()))?
}

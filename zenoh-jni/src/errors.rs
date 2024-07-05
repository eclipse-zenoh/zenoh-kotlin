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

use std::fmt;

use jni::JNIEnv;

#[macro_export]
macro_rules! throw_exception {
    ($env:expr, $err:expr) => {{
        let _ = $err.throw_on_jvm(&mut $env).map_err(|err| {
            tracing::error!("Unable to throw exception: {}", err);
        });
    }};
}

#[macro_export]
macro_rules! jni_error {
    ($arg:expr) => {
        Error::Jni($arg.to_string())
    };
    ($fmt:expr, $($arg:tt)*) => {
        Error::Jni(format!($fmt, $($arg)*))
    };
}

#[macro_export]
macro_rules! session_error {
    ($arg:expr) => {
        $crate::errors::Error::Session($arg.to_string())
    };
    ($fmt:expr, $($arg:tt)*) => {
        Error::Session(format!($fmt, $($arg)*))
    };

}

#[macro_export]
macro_rules! key_expr_error {
    ($arg:expr) => {
        Error::KeyExpr($arg.to_string())
    };
    ($fmt:expr, $($arg:tt)*) => {
        Error::KeyExpr(format!($fmt, $($arg)*))
    };
}

pub(crate) type Result<T> = core::result::Result<T, Error>;

#[derive(Debug)]
pub(crate) enum Error {
    Session(String),
    KeyExpr(String),
    Jni(String),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Session(msg) => write!(f, "{}", msg),
            Error::KeyExpr(msg) => write!(f, "{}", msg),
            Error::Jni(msg) => write!(f, "{}", msg),
        }
    }
}

impl Error {
    fn get_associated_kotlin_exception(&self) -> String {
        let class = match self {
            Error::Session(_) => "io/zenoh/exceptions/SessionException",
            Error::KeyExpr(_) => "io/zenoh/exceptions/KeyExprException",
            Error::Jni(_) => "io/zenoh/exceptions/JNIException",
        };
        class.to_string()
    }

    pub fn throw_on_jvm(&self, env: &mut JNIEnv) -> Result<()> {
        let exception_name = self.get_associated_kotlin_exception();
        let exception_class = env
            .find_class(&exception_name)
            .map_err(|err| jni_error!("Failed to retrieve exception class: {}", err))?;
        env.throw_new(exception_class, self.to_string())
            .map_err(|err| jni_error!("Failed to throw exception: {}", err))
    }
}

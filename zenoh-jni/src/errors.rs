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
macro_rules! zerror {
    ($arg:expr) => {
        $crate::errors::ZError($arg.to_string())
    };
    ($fmt:expr, $($arg:tt)*) => {
        $crate::errors::ZError(format!($fmt, $($arg)*))
    };
}

pub(crate) type ZResult<T> = core::result::Result<T, ZError>;

#[derive(Debug)]
pub(crate) struct ZError(pub String);

impl fmt::Display for ZError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl ZError {
    const KOTLIN_EXCEPTION_NAME: &'static str = "io/zenoh/exceptions/ZError";

    pub fn throw_on_jvm(&self, env: &mut JNIEnv) -> ZResult<()> {
        let exception_class = env
            .find_class(Self::KOTLIN_EXCEPTION_NAME)
            .map_err(|err| zerror!("Failed to retrieve exception class: {}", err))?;
        env.throw_new(exception_class, self.to_string())
            .map_err(|err| zerror!("Failed to throw exception: {}", err))
    }
}

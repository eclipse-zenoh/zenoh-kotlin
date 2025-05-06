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

/// Safe accessor to refocounted ([Arc]) owned objects.
/// Helps to avoid early drop by offloading [std::mem::forget] from user
pub(crate) struct OwnedObject<T: ?Sized> {
    inner: Option<Arc<T>>,
}

impl<T: ?Sized> Deref for OwnedObject<T> {
    type Target = T;

    fn deref(&self) -> &Self::Target {
        // SAFETY: inner is always initialized
        unsafe { self.inner.as_ref().unwrap_unchecked() }
    }
}

impl<T: ?Sized> Drop for OwnedObject<T> {
    fn drop(&mut self) {
        // SAFETY: inner is always initialized
        let inner = unsafe { self.inner.take().unwrap_unchecked() };
        std::mem::forget(inner);
    }
}

impl<T: ?Sized> OwnedObject<T> {
    pub(crate) unsafe fn from_raw(ptr: *const T) -> Self {
        Self {
            inner: Some(Arc::from_raw(ptr)),
        }
    }
}

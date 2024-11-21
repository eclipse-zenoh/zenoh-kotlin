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

mod config;
mod errors;
mod key_expr;
mod liveliness;
mod logger;
mod publisher;
mod query;
mod queryable;
mod scouting;
mod session;
mod subscriber;
mod utils;
#[cfg(feature = "zenoh-ext")]
mod zbytes;
mod zenoh_id;

// Test should be runned with `cargo test --no-default-features`
#[test]
#[cfg(not(feature = "default"))]
fn test_no_default_features() {
    assert_eq!(zenoh::FEATURES, concat!(" zenoh/unstable"));
}

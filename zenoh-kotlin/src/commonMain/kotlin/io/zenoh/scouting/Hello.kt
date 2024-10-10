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

package io.zenoh.scouting

import io.zenoh.ZenohType
import io.zenoh.config.WhatAmI
import io.zenoh.config.ZenohId

/**
 * Hello message received while scouting.
 *
 * @property whatAmI [WhatAmI] configuration: it indicates the role of the zenoh node sending the HELLO message.
 * @property zid [ZenohId] of the node sending the hello message.
 * @property locators The locators of this hello message.
 * @see Scout
 */
data class Hello(val whatAmI: WhatAmI, val zid: ZenohId, val locators: List<String>): ZenohType

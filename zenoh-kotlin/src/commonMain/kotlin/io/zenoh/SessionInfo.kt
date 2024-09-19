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

package io.zenoh

import io.zenoh.protocol.ZenohID

/**
 * Class allowing to obtain the information of a [Session].
 */
class SessionInfo(private val session: Session) {

    /**
     *  Return the [ZenohID] of the current Zenoh [Session]
     */
    fun id(): Result<ZenohID> {
        return session.zid()
    }

    /**
     * Return the [ZenohID] of the zenoh peers the session is currently connected to.
     */
    fun peersId(): Result<List<ZenohID>> {
        return session.getPeersId()
    }

    /**
     * Return the [ZenohID] of the zenoh routers the session is currently connected to.
     */
    fun routersId(): Result<List<ZenohID>> {
        return session.getRoutersId()
    }
}
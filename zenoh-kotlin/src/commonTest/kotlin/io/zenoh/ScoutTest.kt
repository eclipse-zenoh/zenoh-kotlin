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

import io.zenoh.scouting.Hello
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertNotNull

class ScoutTest {

    @Test
    fun `scouting is declared and undeclared properly test`() {
        val scout = Zenoh.scout(Channel()).getOrThrow()
        scout.close()
    }

    @Test
    fun `scouting detects session test`() {
        val session = Session.open(Config.default()).getOrThrow()

        var hello: Hello? = null
        Zenoh.scout(callback = {
            hello = it
        }).getOrThrow()

        Thread.sleep(1000)

        assertNotNull(hello)
        session.close()
    }

    @Test
    fun `scouting loads config successfully test`() {
        Zenoh.scout({}, config = Config.default()).getOrThrow()
    }
}
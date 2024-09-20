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

import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.bytes.into
import io.zenoh.config.Config
import io.zenoh.sample.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PutTest {

    companion object {
        const val TEST_KEY_EXP = "example/testing/keyexpr"
        val TEST_PAYLOAD = "Hello".into()
    }

    @Test
    fun putTest() {
        val session = Session.open(Config.default()).getOrThrow()
        var receivedSample: Sample? = null
        val keyExpr = TEST_KEY_EXP.intoKeyExpr().getOrThrow()
        val subscriber = session.declareSubscriber(keyExpr, callback = { sample -> receivedSample = sample }).getOrThrow()
        session.put(keyExpr, TEST_PAYLOAD, encoding = Encoding.TEXT_PLAIN)
        subscriber.close()
        session.close()
        assertNotNull(receivedSample)
        assertEquals(TEST_PAYLOAD, receivedSample!!.payload)
    }
}

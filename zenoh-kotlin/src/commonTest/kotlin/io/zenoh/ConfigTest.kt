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
import io.zenoh.prelude.SampleKind
import io.zenoh.sample.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfigTest {

    @Test
    fun tlsConfigLoadingTest() {
        val configData = ConfigData(
            mode = "peer",
            connect = Connect(
                endpoints = arrayListOf("tls/localhost:7447")
            ),
            transport = Transport(
                link = Link(
                    tls = Tls(
                        clientAuth = true,
                        rootCaCertificate = "minica.pem",
                        serverPrivateKey = "key.pem",
                        serverCertificate = "cert.pem"
                    )
                )
            )
        )
        val session = Session.open(Config.from(configData)).getOrThrow()
        var receivedSample: Sample? = null
        session.declareSubscriber(DeleteTest.TEST_KEY_EXP).with { sample -> receivedSample = sample }.res()
        session.delete(DeleteTest.TEST_KEY_EXP).res()
        session.close()

        assertNotNull(receivedSample)
        assertEquals(receivedSample!!.kind, SampleKind.DELETE)
    }
}

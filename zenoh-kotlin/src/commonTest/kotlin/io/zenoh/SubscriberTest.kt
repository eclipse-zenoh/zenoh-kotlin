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

import io.zenoh.handlers.Handler
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.bytes.Encoding
import io.zenoh.sample.Sample
import io.zenoh.qos.CongestionControl
import io.zenoh.qos.Priority
import io.zenoh.qos.QoS
import io.zenoh.bytes.into
import io.zenoh.config.Config
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.collections.ArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.*

class SubscriberTest {

    companion object {
        val TEST_PRIORITY = Priority.DATA_HIGH
        val TEST_CONGESTION_CONTROL = CongestionControl.BLOCK

        val testValues = arrayListOf(
            Pair("Test 1".into(), Encoding.TEXT_PLAIN),
            Pair("Test 2".into(), Encoding.TEXT_JSON),
            Pair("Test 3".into(), Encoding.TEXT_CSV),
        )
    }

    private lateinit var session: Session
    private lateinit var testKeyExpr: KeyExpr

    @BeforeTest
    fun setUp() {
        session = Session.open(Config.default()).getOrThrow()
        testKeyExpr = "example/testing/keyexpr".intoKeyExpr().getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        session.close()
        testKeyExpr.close()
    }

    @Test
    fun subscriber_runsWithCallback() {
        val receivedSamples = ArrayList<Sample>()
        val subscriber =
            session.declareSubscriber(testKeyExpr, callback = { sample -> receivedSamples.add(sample)}).getOrThrow()

        testValues.forEach { value ->
            session.put(testKeyExpr, value.first, encoding = value.second, qos = QoS(priority = TEST_PRIORITY, congestionControl = TEST_CONGESTION_CONTROL))
        }
        assertEquals(receivedSamples.size, testValues.size)

        receivedSamples.zip(testValues).forEach { (sample, value) ->
            assertEquals(sample.payload, value.first)
            assertEquals(sample.encoding, value.second)
            assertEquals(sample.qos.priority, TEST_PRIORITY)
            assertEquals(sample.qos.congestionControl, TEST_CONGESTION_CONTROL)
        }

        subscriber.close()
    }

    @Test
    fun subscriber_runsWithHandler() {
        val handler = QueueHandler<Sample>()
        val subscriber = session.declareSubscriber(testKeyExpr, handler = handler).getOrThrow()

        testValues.forEach { value ->
            session.put(testKeyExpr, value.first, encoding = value.second, qos = QoS(priority = TEST_PRIORITY, congestionControl = TEST_CONGESTION_CONTROL))
        }
        assertEquals(handler.queue.size, testValues.size)

        handler.queue.zip(testValues).forEach { (sample, value) ->
            assertEquals(sample.payload, value.first)
            assertEquals(sample.encoding, value.second)
            assertEquals(sample.qos.priority, TEST_PRIORITY)
            assertEquals(sample.qos.congestionControl, TEST_CONGESTION_CONTROL)
        }

        subscriber.close()
    }

    @Test
    fun subscriber_isDeclaredWithNonDeclaredKeyExpression() {
        // Declaring a subscriber with an undeclared key expression and verifying it properly receives samples.
        val keyExpr = KeyExpr("example/**")
        val session = Session.open(Config.default()).getOrThrow()

        val receivedSamples = ArrayList<Sample>()
        val subscriber = session.declareSubscriber(keyExpr, callback = { sample -> receivedSamples.add(sample) }).getOrThrow()
        testValues.forEach { value -> session.put(testKeyExpr, value.first) }
        subscriber.close()

        assertEquals(receivedSamples.size, testValues.size)

        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample.payload, testValues[index].first)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun onCloseTest() = runBlocking {
        var onCloseWasCalled = false
        val subscriber = session.declareSubscriber(testKeyExpr, channel = Channel(), onClose = { onCloseWasCalled = true }).getOrThrow()
        subscriber.undeclare()

        assertTrue(onCloseWasCalled)
        assertTrue(subscriber.receiver.isClosedForReceive)
    }
}

private class QueueHandler<T : ZenohType> : Handler<T, ArrayDeque<T>> {

    val queue: ArrayDeque<T> = ArrayDeque()
    override fun handle(t: T) {
        queue.add(t)
    }

    override fun receiver(): ArrayDeque<T> {
        return queue
    }

    override fun onClose() {}
}

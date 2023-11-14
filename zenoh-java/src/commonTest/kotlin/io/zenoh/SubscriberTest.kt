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
import io.zenoh.prelude.KnownEncoding
import io.zenoh.keyexpr.intoKeyExpr
import io.zenoh.prelude.Encoding
import io.zenoh.sample.Sample
import io.zenoh.value.Value
import java.util.*
import java.util.concurrent.BlockingQueue
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class SubscriberTest {

    companion object {
        val TEST_KEY_EXP = "example/testing/keyexpr".intoKeyExpr()

        val testValues = arrayListOf(
            Value("Test 1".encodeToByteArray(), Encoding(KnownEncoding.TEXT_PLAIN)),
            Value("Test 2".encodeToByteArray(), Encoding(KnownEncoding.TEXT_JSON)),
            Value("Test 3".encodeToByteArray(), Encoding(KnownEncoding.TEXT_CSV))
        )
    }

    @Test
    fun subscriber_runsWithCallback() {
        val session = Session.open()
        val receivedSamples = ArrayList<Sample>()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).with { sample -> receivedSamples.add(sample) }.res()

        publishTestValues(session)

        subscriber.undeclare()
        session.close()

        assertEquals(receivedSamples.size, testValues.size)

        for ((index, sample) in receivedSamples.withIndex()) {
            assertEquals(sample.value, testValues[index])
        }
    }

    @Test
    fun subscriber_runsWithHandler() {
        val handler = QueueHandler<Sample>()
        val session = Session.open()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).with(handler).res()
        publishTestValues(session)
        subscriber.undeclare()
        session.close()

        val queue = subscriber.receiver!!
        assertEquals(queue.size, testValues.size)
        for ((index, sample) in queue.withIndex()) {
            assertEquals(sample.value, testValues[index])
        }
    }

    @Test
    fun subscriberBuilder_queueHandlerIsTheDefaultHandler() {
        val session = Session.open()
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).res()
        assertTrue(subscriber.receiver is BlockingQueue<Optional<Sample>>)
    }

    @Test
    fun onCloseTest() {
        val session = Session.open()
        var onCloseWasCalled = false
        val subscriber = session.declareSubscriber(TEST_KEY_EXP).onClose { onCloseWasCalled = true }.res()
        subscriber.undeclare()
        assertTrue(onCloseWasCalled)
        session.close()
    }

    private fun publishTestValues(session: Session): ArrayList<Value> {
        val publisher = session.declarePublisher(TEST_KEY_EXP).res()
        testValues.forEach { value -> publisher.put(value).res() }
        publisher.undeclare()
        return testValues
    }
}

private class QueueHandler<T: ZenohType> : Handler<T, ArrayDeque<T>> {

    val queue: ArrayDeque<T> = ArrayDeque()
    override fun handle(t: T) {
        queue.add(t)
    }

    override fun receiver(): ArrayDeque<T> {
        return queue
    }

    override fun onClose() {}
}

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

package io.zenoh;

import io.zenoh.exceptions.ZenohException;
import io.zenoh.keyexpr.KeyExpr;
import io.zenoh.sample.Sample;
import io.zenoh.subscriber.Subscriber;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;

public class ZSub {

    public static void main(String[] args) throws ZenohException, InterruptedException {
        System.out.println("Opening session...");
        try (Session session = Session.open()) {
            try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example/**")) {
                System.out.println("Declaring Subscriber on '" + keyExpr + "'...");
                try (Subscriber<BlockingQueue<Optional<Sample>>> subscriber = session.declareSubscriber(keyExpr).res()) {
                    BlockingQueue<Optional<Sample>> receiver = subscriber.getReceiver();
                    assert receiver != null;
                    while (true) {
                        Optional<Sample> wrapper = receiver.take();
                        if (wrapper.isEmpty()) {
                            break;
                        }
                        Sample sample = wrapper.get();
                        System.out.println(">> [Subscriber] Received " + sample.getKind() + " ('" + sample.getKeyExpr() + "': '" + sample.getValue() + "')");
                    }
                }
            }
        }
    }
}

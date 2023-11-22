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
import io.zenoh.publication.Publisher;

public class ZPub {
    public static void main(String[] args) throws ZenohException, InterruptedException {
        System.out.println("Opening session...");
        try (Session session = Session.open()) {
            try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example/zenoh-java-pub")) {
                System.out.println("Declaring publisher on '" + keyExpr + "'...");
                try (Publisher publisher = session.declarePublisher(keyExpr).res()) {
                    String payload = "Pub from Java!";
                    int idx = 0;
                    while (true) {
                        Thread.sleep(1000);
                        System.out.println("Putting Data ('" + keyExpr + "': '[" + String.format("%4s", idx) + "] " + payload + "')...");
                        publisher.put(payload).res();
                        idx++;
                    }
                }
            }
        }
    }
}

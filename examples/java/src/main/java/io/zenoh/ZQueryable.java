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
import io.zenoh.prelude.SampleKind;
import io.zenoh.queryable.Query;
import io.zenoh.queryable.Queryable;
import org.apache.commons.net.ntp.TimeStamp;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;

public class ZQueryable {

    public static void main(String[] args) throws ZenohException, InterruptedException {
        try (Session session = Session.open()) {
            try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example/zenoh-java-queryable")) {
                System.out.println("Declaring Queryable");
                try (Queryable<BlockingQueue<Optional<Query>>> queryable = session.declareQueryable(keyExpr).res()) {
                    BlockingQueue<Optional<Query>> receiver = queryable.getReceiver();
                    assert receiver != null;
                    handleRequests(receiver, keyExpr);
                }
            }
        }
    }

    private static void handleRequests(BlockingQueue<Optional<Query>> receiver, KeyExpr keyExpr) throws InterruptedException {
        while (true) {
            Optional<Query> wrapper = receiver.take();
            if (wrapper.isEmpty()) {
                break;
            }
            Query query = wrapper.get();
            String valueInfo = query.getValue() != null ? " with value '" + query.getValue() + "'" : "";
            System.out.println(">> [Queryable] Received Query '" + query.getSelector() + "'" + valueInfo);
            try {
                query.reply(keyExpr).success("Queryable from Java!").withKind(SampleKind.PUT).withTimeStamp(TimeStamp.getCurrentTime()).res();
            } catch (Exception e) {
                System.out.println(">> [Queryable] Error sending reply: " + e);
            }
        }
    }
}

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
import io.zenoh.query.Reply;
import io.zenoh.selector.Selector;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;

public class ZGet {

    public static void main(String[] args) throws ZenohException, InterruptedException {
        System.out.println("Opening session...");
        try (Session session = Session.open()) {
            try (Selector selector = Selector.tryFrom("demo/example/**")) {
                System.out.println("Performing Get on '" + selector + "'...");
                BlockingQueue<Optional<Reply>> receiver = session.get(selector).res();
                assert receiver != null;
                while (true) {
                    Optional<Reply> wrapper = receiver.take();
                    if (wrapper.isEmpty()) {
                        break;
                    }
                    Reply reply = wrapper.get();
                    if (reply instanceof Reply.Success) {
                        Reply.Success successReply = (Reply.Success) reply;
                        System.out.println("Received ('" + successReply.getSample().getKeyExpr() + "': '" + successReply.getSample().getValue() + "')");
                    } else {
                        Reply.Error errorReply = (Reply.Error) reply;
                        System.out.println("Received (ERROR: '" + errorReply.getError() + "')");
                    }
                }
            }
        }
    }
}

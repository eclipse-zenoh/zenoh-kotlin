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
import io.zenoh.prelude.Encoding;
import io.zenoh.prelude.KnownEncoding;
import io.zenoh.publication.CongestionControl;
import io.zenoh.publication.Publisher;
import io.zenoh.value.Value;

public class ZPubThr {

    public static void main(String[] args) throws ZenohException {
        int size = 8;
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 10);
        }
        Value value = new Value(data, new Encoding(KnownEncoding.EMPTY));
        try (Session session = Session.open()) {
            try (KeyExpr keyExpr = KeyExpr.tryFrom("test/thr")) {
                try (Publisher publisher = session.declarePublisher(keyExpr).congestionControl(CongestionControl.BLOCK).res()) {
                    System.out.println("Publisher declared on test/thr.");
                    while (true) {
                        publisher.put(value).res();
                    }
                }
            }
        }
    }
}

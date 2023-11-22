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

public class ZDelete {
    public static void main(String[] args) throws ZenohException {
        System.out.println("Opening session...");
        try (Session session = Session.open()) {
            try (KeyExpr keyExpr = KeyExpr.tryFrom("demo/example/zenoh-java-put")) {
                System.out.println("Deleting resources matching '" + keyExpr + "'...");
                session.delete(keyExpr).res();
            }
        }
    }
}

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
import io.zenoh.subscriber.Subscriber;
import kotlin.Unit;

import java.util.Scanner;

public class ZSubThr {

    private static final long NANOS_TO_SEC = 1_000_000_000L;
    private static final long n = 50000L;
    private static int batchCount = 0;
    private static int count = 0;
    private static long startTimestampNs = 0;
    private static long globalStartTimestampNs = 0;

    public static void listener() {
        if (count == 0) {
            startTimestampNs = System.nanoTime();
            if (globalStartTimestampNs == 0L) {
                globalStartTimestampNs = startTimestampNs;
            }
            count++;
            return;
        }
        if (count < n) {
            count++;
            return;
        }
        long stop = System.nanoTime();
        double msgs = (double) (n * NANOS_TO_SEC) / (stop - startTimestampNs);
        System.out.println(msgs + " msgs/sec");
        batchCount++;
        count = 0;
    }

    public static void report() {
        long end = System.nanoTime();
        long total = batchCount * n + count;
        double msgs = (double) (end - globalStartTimestampNs) / NANOS_TO_SEC;
        double avg = (double) (total * NANOS_TO_SEC) / (end - globalStartTimestampNs);
        System.out.println("Received " + total + " messages in " + msgs +
                ": averaged " + avg + " msgs/sec");
    }

    public static void main(String[] args) throws ZenohException {
        System.out.println("Opening Session");
        try (Session session = Session.open()) {
            try (KeyExpr keyExpr = KeyExpr.tryFrom("test/thr")) {
                try (Subscriber<Unit> subscriber = session.declareSubscriber(keyExpr).with(sample -> listener()).res()) {
                    Scanner scanner = new Scanner(System.in);
                    while (!scanner.nextLine().equals("q")) {
                        // Do nothing
                    }
                    scanner.close();
                }
            }
        }
        report();
    }
}

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

package io.zenoh.session

import io.zenoh.pubsub.Publisher
import io.zenoh.pubsub.Subscriber
import io.zenoh.query.Queryable
import io.zenoh.keyexpr.KeyExpr
import io.zenoh.Session

/**
 * Session declaration.
 *
 * A session declaration is either a [Publisher],
 * a [Subscriber], a [Queryable] or a [KeyExpr] declared from a [Session].
 */
interface SessionDeclaration {

    /** Undeclare a declaration. No further operations should be performed after calling this function. */
    fun undeclare()
}

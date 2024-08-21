package io.zenoh.scouting

enum class WhatAmI(internal val value: Int) {
    Router(1),
    Peer(2),
    Client(4);

    companion object {
        internal fun fromInt(value: Int) = entries.first { value == it.value }
    }
}
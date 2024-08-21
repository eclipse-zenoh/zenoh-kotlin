package io.zenoh.scouting

enum class WhatAmI(val value: Int) {
    Router(0),
    Peer(1),
    Client(2);

    companion object {
        fun fromInt(value: Int) = entries.first { value == it.value }
    }
}
package io.zenoh.scouting

import io.zenoh.ZenohType
import io.zenoh.protocol.ZenohID

data class Hello(val whatAmI: WhatAmI, val zid: ZenohID, val locators: List<String>): ZenohType
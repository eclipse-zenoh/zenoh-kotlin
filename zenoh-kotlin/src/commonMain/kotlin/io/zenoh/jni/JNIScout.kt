package io.zenoh.jni

import io.zenoh.Config
import io.zenoh.handlers.Callback
import io.zenoh.jni.callbacks.JNIScoutCallback
import io.zenoh.protocol.ZenohID
import io.zenoh.scouting.Hello
import io.zenoh.scouting.Scout
import io.zenoh.scouting.WhatAmI

class JNIScout(private val ptr: Long) {

    fun close() {
        freePtrViaJNI(ptr)
    }

    companion object {
        fun <R> scout(
            what: Set<WhatAmI>,
            callback: Callback<Hello>,
            config: Config,
            receiver: R
        ): Scout<R> {
            val scoutCallback = JNIScoutCallback { whatAmI: Int, id: String, locators: List<String> ->
                callback.run(Hello(WhatAmI.fromInt(whatAmI), ZenohID(id), locators))
            }
            val ptr = scoutViaJNI(what.toString(), scoutCallback, config.jsonConfig.toString())
            return Scout(receiver, JNIScout(ptr))
        }

        @Throws(Exception::class)
        private external fun scoutViaJNI(what: String, callback: JNIScoutCallback, config: String): Long

        @Throws(Exception::class)
        external fun freePtrViaJNI(ptr: Long)
    }
}

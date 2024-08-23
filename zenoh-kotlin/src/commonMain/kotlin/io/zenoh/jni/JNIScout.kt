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
            whatAmI: Set<WhatAmI>,
            callback: Callback<Hello>,
            config: Config?,
            receiver: R
        ): Scout<R> {
            val scoutCallback = JNIScoutCallback { whatAmI2: Int, id: String, locators: List<String> ->
                callback.run(Hello(WhatAmI.fromInt(whatAmI2), ZenohID(id), locators))
            }
            val binaryWhatAmI: Int = whatAmI.map { it.value }.reduce { acc, it -> acc or it }
            val ptr = scoutViaJNI(
                binaryWhatAmI, scoutCallback, config?.config, config?.format?.ordinal ?: 0,
                config?.path?.toString()
            )
            return Scout(receiver, JNIScout(ptr))
        }

        @Throws(Exception::class)
        private external fun scoutViaJNI(
            whatAmI: Int,
            callback: JNIScoutCallback,
            config: String?,
            format: Int,
            path: String?
        ): Long

        @Throws(Exception::class)
        external fun freePtrViaJNI(ptr: Long)
    }
}

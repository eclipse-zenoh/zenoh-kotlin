package io.zenoh.jni.callbacks

internal fun interface JNIScoutCallback {

    fun run(whatAmI: Int, zid: String, locators: List<String>)
}
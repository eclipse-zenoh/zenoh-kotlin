package io.zenoh.jni

internal class JNILivelinessToken(val ptr: Long) {

    fun undeclare() {
        undeclareViaJNI(this.ptr)
    }

    companion object {
        external fun undeclareViaJNI(ptr: Long)
    }
}

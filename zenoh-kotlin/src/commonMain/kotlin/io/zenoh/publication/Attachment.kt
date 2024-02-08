package io.zenoh.publication

class Attachment(val values: MutableList<Pair<String, ByteArray>> = mutableListOf()) {

    constructor(values: Iterable<Pair<String, ByteArray>>): this(values.toMutableList())

    fun add(key: String, value: ByteArray) {
        values.add(key to value)
    }

    fun add(key: String, value: String) {
        values.add(key to value.toByteArray())
    }
}

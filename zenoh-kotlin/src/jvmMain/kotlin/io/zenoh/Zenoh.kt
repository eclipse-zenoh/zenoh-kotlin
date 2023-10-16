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

package io.zenoh

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.Exception

/**
 * Static singleton class to load the Zenoh native library once and only once, as well as the logger in function of the
 * log level configuration.
 */
internal actual class Zenoh private actual constructor() {

    actual companion object {
        private const val ZENOH_LIB_NAME = "zenoh_jni"
        private const val ZENOH_LOGS_PROPERTY = "zenoh.logger"

        private var instance: Zenoh? = null

        actual fun load() {
            instance ?: Zenoh().also { instance = it }
        }

        fun loadZenohJNI(inputStream: InputStream) {
            val tempLib = File.createTempFile("tempLib", "")
            tempLib.deleteOnExit()

            FileOutputStream(tempLib).use { output ->
                inputStream.copyTo(output)
            }

            System.load(tempLib.absolutePath)
        }
    }

    init {
        val lib = ClassLoader.getSystemClassLoader().findLibraryStream(ZENOH_LIB_NAME)

        if (lib != null) {
            loadZenohJNI(lib)
        } else {
            throw Exception("Unable to load ZenohJNI.")
        }

        val logLevel = System.getProperty(ZENOH_LOGS_PROPERTY)
        if (logLevel != null) {
            Logger.start(logLevel)
        }
    }
}

private fun ClassLoader.findLibraryStream(libraryName: String): InputStream? {
    // TODO: look after targets of multiple architectures
    val libraryExtensions = listOf(".dylib", ".so", ".dll")
    for (extension in libraryExtensions) {
        val resourcePath = "lib$libraryName$extension"
        val inputStream = getResourceAsStream(resourcePath)
        if (inputStream != null) {
            return inputStream
        }
    }
    return null
}

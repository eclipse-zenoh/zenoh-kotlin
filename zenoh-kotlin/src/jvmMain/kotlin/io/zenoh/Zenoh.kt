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
        private const val ZENOH_LIB_NAME = "libzenoh_jni"
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

        fun determineLibrary(): String {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch")

            val libraryPath = when {
                osName.contains("win") -> when {
                    osArch.contains("x86_64") -> "${Target.WINDOWS_X86_64_MSVC}/release/$ZENOH_LIB_NAME.dll"
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch for $osName.")
                }
                osName.contains("mac") -> when {
                    osArch.contains("x86_64") -> "${Target.APPLE_X86_64}/release/$ZENOH_LIB_NAME.dylib"
                    osArch.contains("aarch64") -> "${Target.APPLE_AARCH64}/release/$ZENOH_LIB_NAME.dylib"
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch for $osName.")
                }
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> when {
                    osArch.contains("x86_64") -> "${Target.LINUX_X86_64}/release/$ZENOH_LIB_NAME.so"
                    osArch.contains("aarch64") -> "${Target.LINUX_AARCH64}/release/$ZENOH_LIB_NAME.so"
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch for $osName.")
                }
                else -> throw UnsupportedOperationException("Unsupported platform: $osName")
            }
            return libraryPath
        }

        fun loadLibrary(path: String): InputStream? {
            return ClassLoader.getSystemClassLoader().getResourceAsStream(path)
        }

        fun loadDefaultLibrary(): InputStream? {
            val libraryExtensions = listOf(".dylib", ".so", ".dll")
            for (extension in libraryExtensions) {
                val resourcePath = "$ZENOH_LIB_NAME$extension"
                val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourcePath)
                if (inputStream != null) {
                    return inputStream
                }
            }
            return null
        }
    }

    init {
        val lib: InputStream? = try {
            val libraryPath = determineLibrary()
            println("Loading $libraryPath...")
            loadLibrary(libraryPath)
        } catch (e: UnsupportedOperationException) {
            println("Attempting to load default library...")
            loadDefaultLibrary()
        }

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

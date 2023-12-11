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
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Static singleton class to load the Zenoh native library once and only once, as well as the logger in function of the
 * log level configuration.
 */
internal actual class Zenoh private actual constructor() {

    actual companion object {
        private const val ZENOH_LOGS_PROPERTY = "zenoh.logger"

        private var instance: Zenoh? = null

        actual fun load() {
            instance ?: Zenoh().also { instance = it }
        }

        fun determineTarget(): Target {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch")

            return when {
                osName.contains("win") -> when {
                    osArch.contains("x86_64") -> Target.WINDOWS_X86_64_MSVC
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch")
                }
                osName.contains("mac") -> when {
                    osArch.contains("x86_64") -> Target.APPLE_X86_64
                    osArch.contains("aarch64") -> Target.APPLE_AARCH64
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch")
                }
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> when {
                    osArch.contains("x86_64") -> Target.LINUX_X86_64
                    osArch.contains("aarch64") -> Target.LINUX_AARCH64
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch")
                }
                else -> throw UnsupportedOperationException("Unsupported platform: $osName")
            }
        }

        private fun unzipLibrary(compressedLibPath: String, uncompressedLibPath: String) {
            val zipInputStream = ZipInputStream(FileInputStream(compressedLibPath))
            val destDir = File(uncompressedLibPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val buffer = ByteArray(1024)
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                val newFile = File(destDir, zipEntry.name)

                val parent = newFile.parentFile
                if (!parent.exists()) {
                    parent.mkdirs()
                }

                val fileOutputStream = FileOutputStream(newFile)
                var len: Int
                while (zipInputStream.read(buffer).also { len = it } > 0) {
                    fileOutputStream.write(buffer, 0, len)
                }
                fileOutputStream.close()
                zipEntry = zipInputStream.nextEntry
            }

            zipInputStream.closeEntry()
            zipInputStream.close()
        }

        fun loadLibraryAsInputStream(target: Target): InputStream? {
            unzipLibrary("$target/$target.zip", target.toString())
            return ClassLoader.getSystemClassLoader().getResourceAsStream(target.toString())
        }

        @Suppress("UnsafeDynamicallyLoadedCode")
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
        val target = determineTarget()
        val lib: InputStream? = loadLibraryAsInputStream(target)

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

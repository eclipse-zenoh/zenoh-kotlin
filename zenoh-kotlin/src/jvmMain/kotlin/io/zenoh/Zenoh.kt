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
import java.io.FileInputStream
import java.util.zip.ZipInputStream

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

        /**
         * Determine target
         *
         * Determines the [Target] corresponding to the machine on top of which the native code will run.
         *
         * @return A result with the target.
         */
        private fun determineTarget(): Result<Target> = runCatching {
            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch")

            val target = when {
                osName.contains("win") -> when {
                    osArch.contains("x86_64") || osArch.contains("amd64") -> Target.WINDOWS_X86_64_MSVC
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch")
                }

                osName.contains("mac") -> when {
                    osArch.contains("x86_64") || osArch.contains("amd64") -> Target.APPLE_X86_64
                    osArch.contains("aarch64") -> Target.APPLE_AARCH64
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch")
                }

                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> when {
                    osArch.contains("x86_64") || osArch.contains("amd64") -> Target.LINUX_X86_64
                    osArch.contains("aarch64") -> Target.LINUX_AARCH64
                    else -> throw UnsupportedOperationException("Unsupported architecture: $osArch")
                }

                else -> throw UnsupportedOperationException("Unsupported platform: $osName")
            }
            return Result.success(target)
        }

        /**
         * Unzip library.
         *
         * The Zenoh libraries are stored within the JAR as compressed ZIP files.
         * The location of the zipped files is expected to be under target/target.zip.
         * It is expected that the zip file only contains the compressed library.
         *
         * The uncompressed library will be stored temporarily and deleted on exit.
         *
         * @param compressedLib Input stream pointing to the compressed library.
         * @return A result with the uncompressed library file.
         */
        private fun unzipLibrary(compressedLib: InputStream): Result<File> = runCatching {
            val zipInputStream = ZipInputStream(compressedLib)
            val buffer = ByteArray(1024)
            val zipEntry = zipInputStream.nextEntry

            val library = File.createTempFile(zipEntry!!.name, ".tmp")
            library.deleteOnExit()

            val parent = library.parentFile
            if (!parent.exists()) {
                parent.mkdirs()
            }

            val fileOutputStream = FileOutputStream(library)
            var len: Int
            while (zipInputStream.read(buffer).also { len = it } > 0) {
                fileOutputStream.write(buffer, 0, len)
            }
            fileOutputStream.close()

            zipInputStream.closeEntry()
            zipInputStream.close()
            return Result.success(library)
        }

        private fun loadLibraryAsInputStream(target: Target): Result<InputStream> = runCatching {
            val libUrl = ClassLoader.getSystemClassLoader().getResourceAsStream("$target/$target.zip")!!
            val uncompressedLibFile = unzipLibrary(libUrl)
            return Result.success(FileInputStream(uncompressedLibFile.getOrThrow()))
        }

        @Suppress("UnsafeDynamicallyLoadedCode")
        private fun loadZenohJNI(inputStream: InputStream) {
            val tempLib = File.createTempFile("tempLib", "")
            tempLib.deleteOnExit()

            FileOutputStream(tempLib).use { output ->
                inputStream.copyTo(output)
            }

            System.load(tempLib.absolutePath)
        }

        /**
         * Load library from jar package.
         *
         * Attempts to load the library corresponding to the `target` specified from the zenoh kotlin jar.
         *
         * @param target
         */
        private fun tryLoadingLibraryFromJarPackage(target: Target): Result<Unit> = runCatching {
            val lib: Result<InputStream> = loadLibraryAsInputStream(target)
            lib.onSuccess { loadZenohJNI(it) }.onFailure { throw Exception("Unable to load Zenoh JNI: $it") }
        }

        /**
         * Try loading local library.
         *
         * This function aims to load the default library that is usually included when building the zenoh kotlin library
         * locally.
         */
        private fun tryLoadingLocalLibrary(): Result<Unit> = runCatching {
            val lib = ClassLoader.getSystemClassLoader().findLibraryStream(ZENOH_LIB_NAME)
            if (lib != null) {
                loadZenohJNI(lib)
            } else {
                throw Exception("Unable to load local Zenoh JNI.")
            }
        }
    }

    init {
        // Try first to load the local native library for cases in which the module was built locally,
        // otherwise try to load from the JAR.
        if (tryLoadingLocalLibrary().isFailure) {
            val target = determineTarget().getOrThrow()
            tryLoadingLibraryFromJarPackage(target).getOrThrow()
        }

        val logLevel = System.getProperty(ZENOH_LOGS_PROPERTY)
        if (logLevel != null) {
            Logger.start(logLevel)
        }
    }
}

private fun ClassLoader.findLibraryStream(libraryName: String): InputStream? {
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

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

package io.zenoh.jni

import io.zenoh.config.Config
import io.zenoh.ZenohLoad
import io.zenoh.exceptions.ZError
import java.io.File
import java.nio.file.Path

internal class JNIConfig(internal val ptr: Long) {

    companion object {

        init {
            ZenohLoad
        }

        fun loadDefaultConfig(): Config {
            val cfgPtr = loadDefaultConfigViaJNI()
            return Config(JNIConfig(cfgPtr))
        }

        fun loadConfigFile(path: Path): Result<Config> = runCatching {
            val cfgPtr = loadConfigFileViaJNI(path.toString())
            Config(JNIConfig(cfgPtr))
        }

        fun loadConfigFile(file: File): Result<Config> = loadConfigFile(file.toPath())

        fun loadJsonConfig(rawConfig: String): Result<Config> = runCatching {
            val cfgPtr = loadJsonConfigViaJNI(rawConfig)
            Config(JNIConfig(cfgPtr))
        }

        fun loadJson5Config(rawConfig: String): Result<Config> = runCatching {
            val cfgPtr = loadJsonConfigViaJNI(rawConfig)
            Config(JNIConfig(cfgPtr))
        }

        fun loadYamlConfig(rawConfig: String): Result<Config> = runCatching {
            val cfgPtr = loadYamlConfigViaJNI(rawConfig)
            Config(JNIConfig(cfgPtr))
        }

        @Throws(ZError::class)
        private external fun loadDefaultConfigViaJNI(): Long

        @Throws(ZError::class)
        private external fun loadConfigFileViaJNI(path: String): Long

        @Throws(ZError::class)
        private external fun loadJsonConfigViaJNI(rawConfig: String): Long

        @Throws(ZError::class)
        private external fun loadYamlConfigViaJNI(rawConfig: String): Long

        @Throws(ZError::class)
        private external fun getIdViaJNI(ptr: Long): ByteArray

        @Throws(ZError::class)
        private external fun insertJson5ViaJNI(ptr: Long, key: String, value: String): Long

        /** Frees the underlying native config. */
        private external fun freePtrViaJNI(ptr: Long)

        @Throws(ZError::class)
        private external fun getJsonViaJNI(ptr: Long, key: String): String
    }

    fun close() {
        freePtrViaJNI(ptr)
    }

    fun getJson(key: String): Result<String> = runCatching {
        getJsonViaJNI(ptr, key)
    }

    fun insertJson5(key: String, value: String): Result<Unit> = runCatching {
        insertJson5ViaJNI(this.ptr, key, value)
    }
}

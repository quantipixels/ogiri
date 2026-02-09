/*
 * Copyright (c) 2025 Quanti Pixels
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.quantipixels.ogiri.security.testutil

import java.util.function.Supplier
import org.springframework.beans.factory.ObjectProvider

/**
 * Creates an ObjectProvider that always returns the fallback from getIfAvailable(Supplier).
 *
 * Useful in unit tests where no Spring context is available but OgiriTokenService requires
 * ObjectProvider constructor params.
 */
inline fun <reified T : Any> emptyObjectProvider(): ObjectProvider<T> =
    object : ObjectProvider<T> {
      override fun getObject(): T = throw NoSuchElementException("No bean available")

      override fun getObject(vararg args: Any?): T =
          throw NoSuchElementException("No bean available")

      override fun getIfAvailable(): T? = null

      override fun getIfAvailable(defaultSupplier: Supplier<T>): T = defaultSupplier.get()

      override fun getIfUnique(): T? = null

      override fun getIfUnique(defaultSupplier: Supplier<T>): T = defaultSupplier.get()
    }

/**
 * Creates an ObjectProvider that returns the given instance.
 *
 * Useful in unit tests to supply a specific hook implementation.
 */
inline fun <reified T : Any> objectProviderOf(instance: T): ObjectProvider<T> =
    object : ObjectProvider<T> {
      override fun getObject(): T = instance

      override fun getObject(vararg args: Any?): T = instance

      override fun getIfAvailable(): T = instance

      override fun getIfAvailable(defaultSupplier: Supplier<T>): T = instance

      override fun getIfUnique(): T = instance

      override fun getIfUnique(defaultSupplier: Supplier<T>): T = instance
    }

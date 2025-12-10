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
package com.quantipixels.ogiri.security.spi

import org.springframework.security.core.userdetails.UserDetails

interface OgiriUser : UserDetails {
  /** Stable Kotlin property that implementations must expose. */
  val userId: Long

  /** Java-friendly getter that delegates to the Kotlin property. */
  fun getOgiriUserId(): Long = userId
}

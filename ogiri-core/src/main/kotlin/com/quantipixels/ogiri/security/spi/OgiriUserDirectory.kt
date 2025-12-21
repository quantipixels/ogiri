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

import org.springframework.security.core.userdetails.UserDetailsService

interface OgiriUserDirectory : UserDetailsService {
  /**
   * Retrieves an OgiriUser by their numeric identifier.
   *
   * @param id The user's numeric ID.
   * @return The matching `OgiriUser` if found, `null` otherwise.
   */
  fun findById(id: Long): OgiriUser?

  /**
   * Finds an OgiriUser by their username.
   *
   * Unlike [loadUserByUsername] from [UserDetailsService], this method returns `null` when the user
   * is not found instead of throwing an exception.
   *
   * @param username The username to look up.
   * @return `OgiriUser` if a user with the given username exists, `null` otherwise.
   */
  fun findByEmail(email: String): OgiriUser?

  /**
   * Finds an OgiriUser by their username.
   *
   * @param username The username to look up.
   * @return `OgiriUser` if a user with the given username exists, `null` otherwise.
   */
  fun findByUsername(username: String): OgiriUser?

  /**
   * Records a successful login for the specified user.
   *
   * @param userId The numeric identifier of the user whose successful login should be recorded.
   */
  fun recordSuccessfulLogin(userId: Long)
}

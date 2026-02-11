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
package com.quantipixels.ogiri.samples.kotlin.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class HealthControllerTest {

  @Autowired private lateinit var mockMvc: MockMvc

  @Test
  fun `health endpoint returns UP status`() {
    mockMvc
        .get("/api/health") { accept = MediaType.APPLICATION_JSON }
        .andExpect {
          status { isOk() }
          content { contentType(MediaType.APPLICATION_JSON) }
          jsonPath("$.status") { value("UP") }
        }
  }

  @Test
  fun `me endpoint without authentication returns anonymous`() {
    mockMvc
        .get("/api/me") { accept = MediaType.APPLICATION_JSON }
        .andExpect {
          status { isOk() }
          jsonPath("$.authenticated") { value(false) }
          jsonPath("$.principal") { value("anonymous") }
        }
  }
}

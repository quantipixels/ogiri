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

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController {
  @GetMapping("/health")
  fun health(): ResponseEntity<Map<String, String>> {
    return ResponseEntity.ok(mapOf("status" to "UP"))
  }

  @GetMapping("/me")
  fun me(authentication: Authentication?): ResponseEntity<Map<String, Any>> {
    return ResponseEntity.ok(
        mapOf(
            "authenticated" to (authentication != null && authentication.isAuthenticated),
            "principal" to (authentication?.name ?: "anonymous"),
        ),
    )
  }
}

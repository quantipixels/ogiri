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

/**
 * No-op implementation of [OgiriAuditHook] used as the default when no audit hook bean is present.
 *
 * All interface methods have Kotlin default bodies (empty), so this object body is intentionally
 * empty. Follows the Null-Object pattern used throughout Spring Security (e.g.,
 * `NoOpPasswordEncoder`).
 *
 * **Usage in tests:** prefer referencing this object directly rather than implementing an anonymous
 * `object : OgiriAuditHook {}`, so the null-object pattern is visible and consistent.
 */
object NoOpOgiriAuditHook : OgiriAuditHook

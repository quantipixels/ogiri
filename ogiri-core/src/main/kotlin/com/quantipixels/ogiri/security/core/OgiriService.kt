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
package com.quantipixels.ogiri.security.core

/**
 * Meta-annotation that marks Ogiri service classes for the all-open compiler plugin.
 *
 * Classes annotated with @OgiriService have all methods compiled as non-final, ensuring CGLIB proxy
 * interception works for @Transactional methods. The ogiri-core build configures the all-open
 * plugin to recognize this annotation.
 *
 * This annotation intentionally does NOT include @Component to avoid interfering with Spring's
 * component scanning — OgiriTokenService is registered via auto-configuration, not component scan.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class OgiriService

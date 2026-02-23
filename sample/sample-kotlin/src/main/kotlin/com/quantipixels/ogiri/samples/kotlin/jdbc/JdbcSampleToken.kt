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
package com.quantipixels.ogiri.samples.kotlin.jdbc

import com.quantipixels.ogiri.jdbc.OgiriBaseTokenRow

/**
 * Sample JDBC token extending OgiriBaseTokenRow.
 *
 * No persistence annotations required — OgiriBaseTokenRow provides all token fields as plain
 * properties. Compare to [com.quantipixels.ogiri.samples.kotlin.entity.SampleToken] which extends
 * OgiriBaseTokenEntity (JPA).
 *
 * You can also use OgiriBaseTokenRow directly without subclassing.
 */
class JdbcSampleToken : OgiriBaseTokenRow()

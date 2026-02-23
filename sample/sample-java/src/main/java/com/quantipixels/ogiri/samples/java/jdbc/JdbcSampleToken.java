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
package com.quantipixels.ogiri.samples.java.jdbc;

import com.quantipixels.ogiri.jdbc.OgiriBaseTokenRow;
import java.time.Instant;

/**
 * Sample JDBC token for the Java sample.
 *
 * <p>Subclasses {@link OgiriBaseTokenRow} — no annotations required. You can also skip subclassing
 * and use {@link OgiriBaseTokenRow} directly if you have no custom fields to add.
 */
public class JdbcSampleToken extends OgiriBaseTokenRow {

  public JdbcSampleToken() {
    super(0L, 0L, "", "", "app", Instant.now(), Instant.now(), Instant.now(), Instant.now());
  }
}

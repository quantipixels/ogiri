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
package com.quantipixels.ogiri.samples.java.security;

import com.quantipixels.ogiri.security.routes.OgiriRoute;
import com.quantipixels.ogiri.security.routes.OgiriRouteRegistry;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Sample RouteRegistry implementation for Java.
 *
 * <p>Declares public/unauthenticated routes that the filter should allow.
 */
@Component
public class SampleRouteRegistry implements OgiriRouteRegistry {

  @Override
  public List<OgiriRoute> routes() {
    return List.of(
        new OgiriRoute(HttpMethod.POST, "/api/auth/login", true, false, null),
        new OgiriRoute(HttpMethod.GET, "/api/health", true, false, null),
        new OgiriRoute(HttpMethod.GET, "/api/docs/**", true, false, null)
        // Note: /api/auth/logout and /api/demo/** endpoints require authentication,
        // so they are NOT listed here. Only public routes should be registered.
        );
  }
}

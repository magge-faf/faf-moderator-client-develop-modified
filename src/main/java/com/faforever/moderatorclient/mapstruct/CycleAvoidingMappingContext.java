/**
 * Copyright 2012-2017 Gunnar Morling (http://www.gunnarmorling.de/)
 * and/or other contributors as indicated by the @authors tag. See the
 * copyright.txt file in the distribution for a full listing of all
 * contributors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.faforever.moderatorclient.mapstruct;

import org.mapstruct.BeforeMapping;
import org.mapstruct.Context;
import org.mapstruct.MappingTarget;
import org.mapstruct.TargetType;
import org.springframework.stereotype.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A type to be used as {@link Context} parameter to track cycles in graphs.
 * <p>
 * Depending on the actual use case, the two methods below could also be changed to only accept certain argument types,
 * e.g. base classes of graph nodes, avoiding the need to capture any other objects that wouldn't necessarily result in
 * cycles.
 *
 * @author Andreas Gudian
 */
@Component
public class CycleAvoidingMappingContext {
    // This bean is a singleton shared across every mapper and is cleared before each
    // FafApiCommunicationService request. Parallel page fetches (e.g. UserService.fetchRemainingAttributePages)
    // call clearCache() from multiple worker threads concurrently, and other in-flight mapping calls read/write
    // the same map, so plain IdentityHashMap access here is a genuine concurrent-corruption risk. Synchronizing
    // the three operations keeps them mutually exclusive without requiring per-caller isolation.
    private final Map<Object, Object> knownInstances = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    @BeforeMapping
    public synchronized <T> T getMappedInstance(Object source, @TargetType Class<T> targetType) {
        return (T) knownInstances.get(source);
    }

    @BeforeMapping
    public synchronized void storeMappedInstance(Object source, @MappingTarget Object target) {
        knownInstances.put(source, target);
    }

    public synchronized void clearCache() {
        knownInstances.clear();
    }
}

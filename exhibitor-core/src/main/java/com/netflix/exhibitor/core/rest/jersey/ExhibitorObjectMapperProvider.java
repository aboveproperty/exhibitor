/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.exhibitor.core.rest.jersey;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 * Produces JSON that matches what the Jersey 1 "natural" JAXB notation produced for the
 * <code>@XmlRootElement</code> entities: JAXB annotations are honored, null values are omitted,
 * properties without a setter are not written and unknown properties are ignored.
 */
@Provider
class ExhibitorObjectMapperProvider implements ContextResolver<ObjectMapper>
{
    private final ObjectMapper mapper = newMapper();

    static ObjectMapper newMapper()
    {
        ObjectMapper        mapper = new ObjectMapper();
        mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(new JacksonAnnotationIntrospector(), new JaxbAnnotationIntrospector(mapper.getTypeFactory())));
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Override
    public ObjectMapper getContext(Class<?> type)
    {
        return mapper;
    }
}

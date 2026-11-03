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

import com.google.common.collect.Sets;
import com.netflix.exhibitor.core.rest.ClusterResource;
import com.netflix.exhibitor.core.rest.ConfigResource;
import com.netflix.exhibitor.core.rest.ExplorerResource;
import com.netflix.exhibitor.core.rest.IndexResource;
import com.netflix.exhibitor.core.rest.UIContext;
import com.netflix.exhibitor.core.rest.UIContextResolver;
import com.netflix.exhibitor.core.rest.UIResource;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import java.util.Set;

@SuppressWarnings("UnusedDeclaration")
public class JerseySupport
{
    public static void      addSingletons(ResourceConfig config, UIContext context)
    {
        for ( Object singleton : getSingletons(context) )
        {
            config.register(singleton);
        }
    }

    public static void      addClasses(ResourceConfig config)
    {
        for ( Class<?> clazz : getClasses() )
        {
            config.register(clazz);
        }
    }

    /**
     * Return a new Jersey config instance that correctly supplies all needed Exhibitor
     * objects
     *
     * @param context the UIContext
     * @return new config
     */
    public static ResourceConfig newApplicationConfig(UIContext context)
    {
        ResourceConfig      config = new ResourceConfig();
        config.property(ServerProperties.WADL_FEATURE_DISABLE, true);
        addClasses(config);
        addSingletons(config, context);
        return config;
    }

    private static Set<Class<?>> getClasses()
    {
        final Set<Class<?>>     classes = Sets.newHashSet();
        classes.add(UIResource.class);
        classes.add(IndexResource.class);
        classes.add(ExplorerResource.class);
        classes.add(ClusterResource.class);
        classes.add(ConfigResource.class);
        classes.add(JacksonFeature.class);
        return classes;
    }

    private static Set<Object> getSingletons(UIContext context)
    {
        final Set<Object> singletons = Sets.newHashSet();
        singletons.add(new UIContextResolver(context));
        singletons.add(new ExhibitorObjectMapperProvider());
        return singletons;
    }

    private JerseySupport()
    {
    }
}

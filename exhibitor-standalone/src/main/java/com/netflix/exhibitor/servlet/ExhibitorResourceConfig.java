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

package com.netflix.exhibitor.servlet;

import com.netflix.exhibitor.core.Exhibitor;
import com.netflix.exhibitor.core.rest.UIContext;
import com.netflix.exhibitor.core.rest.jersey.JerseySupport;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;

public class ExhibitorResourceConfig extends ResourceConfig
{
    private final Logger        log = LoggerFactory.getLogger(getClass());

    public ExhibitorResourceConfig(@Context ServletContext context)
    {
        Exhibitor               exhibitor = (Exhibitor)context.getAttribute(ExhibitorServletContextListener.class.getName());
        if ( exhibitor != null )
        {
            log.info("Adding Exhibitor Jersey resources");
            JerseySupport.addClasses(this);
            JerseySupport.addSingletons(this, new UIContext(exhibitor));
        }
        else
        {
            log.info("Using empty ResourceConfig");
        }
    }
}

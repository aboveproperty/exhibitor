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

package com.netflix.exhibitor.core.automanage;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.exhibitor.core.RemoteConnectionConfiguration;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class RemoteInstanceRequestClientImpl implements RemoteInstanceRequestClient
{
    private final Client client;
    private final LoadingCache<URI, WebTarget> webTargets = CacheBuilder.newBuilder()
        .softValues()
        .build
            (
                new CacheLoader<URI, WebTarget>()
                {
                    @Override
                    public WebTarget load(URI remoteUri) throws Exception
                    {
                        return client.target(remoteUri);
                    }
                }
            );

    public RemoteInstanceRequestClientImpl(RemoteConnectionConfiguration configuration)
    {
        client = ClientBuilder.newBuilder()
            .connectTimeout(configuration.getConnectionTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(configuration.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
        for ( ClientRequestFilter filter : configuration.getFilters() )
        {
            client.register(filter);
        }
        for ( Feature feature : configuration.getFeatures() )
        {
            client.register(feature);
        }
    }

    @Override
    public <T> T getWebResource(URI remoteUri, MediaType type, Class<T> clazz) throws Exception
    {
        try
        {
            return webTargets.get(remoteUri).request(type).get(clazz);
        }
        catch ( Exception e )
        {
            if ( e.getCause() instanceof SocketException )
            {
                throw (SocketException)e.getCause();
            }

            throw e;
        }
    }

    @Override
    public void close() throws IOException
    {
        client.close();
    }
}

package com.netflix.exhibitor.core;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Feature;
import java.util.Collection;

public class RemoteConnectionConfiguration
{
    private final Collection<ClientRequestFilter> filters;
    private final Collection<Feature> features;
    private final int connectionTimeoutMs;
    private final int readTimeoutMs;

    private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 3000;

    public RemoteConnectionConfiguration()
    {
        this(Lists.<ClientRequestFilter>newArrayList(), DEFAULT_CONNECTION_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public RemoteConnectionConfiguration(Collection<ClientRequestFilter> filters)
    {
        this(filters, DEFAULT_CONNECTION_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public RemoteConnectionConfiguration(Collection<ClientRequestFilter> filters, Collection<Feature> features)
    {
        this(filters, features, DEFAULT_CONNECTION_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public RemoteConnectionConfiguration(Collection<ClientRequestFilter> filters, int connectionTimeoutMs, int readTimeoutMs)
    {
        this(filters, Lists.<Feature>newArrayList(), connectionTimeoutMs, readTimeoutMs);
    }

    /**
     * @param filters request filters to register on the remote client
     * @param features JAX-RS features to register on the remote client (for example, Jersey's <code>HttpAuthenticationFeature</code>, which also supports digest auth)
     * @param connectionTimeoutMs connection timeout
     * @param readTimeoutMs read timeout
     */
    public RemoteConnectionConfiguration(Collection<ClientRequestFilter> filters, Collection<Feature> features, int connectionTimeoutMs, int readTimeoutMs)
    {
        this.filters = ImmutableList.copyOf(Preconditions.checkNotNull(filters, "filters cannot be null"));
        this.features = ImmutableList.copyOf(Preconditions.checkNotNull(features, "features cannot be null"));
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public Collection<ClientRequestFilter> getFilters()
    {
        return filters;
    }

    public Collection<Feature> getFeatures()
    {
        return features;
    }

    public int getConnectionTimeoutMs()
    {
        return connectionTimeoutMs;
    }

    public int getReadTimeoutMs()
    {
        return readTimeoutMs;
    }
}

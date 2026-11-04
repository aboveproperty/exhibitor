/*
 *
 *  Copyright 2011 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.exhibitor.application;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.netflix.exhibitor.servlet.ExhibitorServletFilter;
import com.netflix.exhibitor.core.Exhibitor;
import com.netflix.exhibitor.core.ExhibitorArguments;
import com.netflix.exhibitor.core.RemoteConnectionConfiguration;
import com.netflix.exhibitor.core.backup.BackupProvider;
import com.netflix.exhibitor.core.config.ConfigProvider;
import com.netflix.exhibitor.core.rest.UIContext;
import com.netflix.exhibitor.core.rest.jersey.JerseySupport;
import com.netflix.exhibitor.standalone.ExhibitorCreator;
import com.netflix.exhibitor.standalone.ExhibitorCreatorExit;
import com.netflix.exhibitor.standalone.SecurityArguments;
import org.apache.curator.utils.CloseableUtils;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.PropertyUserStore;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.DispatcherType;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Feature;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExhibitorMain implements Closeable
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Server server;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Exhibitor exhibitor;
    private final AtomicBoolean shutdownSignaled = new AtomicBoolean(false);
    private final Map<String, String> users = Maps.newHashMap();

    public static void main(String[] args) throws Exception
    {
        ExhibitorCreator creator;
        try
        {
            creator = new ExhibitorCreator(args);
        }
        catch ( ExhibitorCreatorExit exit )
        {
            if ( exit.getError() != null )
            {
                System.err.println(exit.getError());
            }

            exit.getCli().printHelp();
            return;
        }

        SecurityArguments securityArguments = new SecurityArguments(creator.getSecurityFile(), creator.getRealmSpec(), creator.getRemoteAuthSpec());
        ExhibitorMain exhibitorMain = new ExhibitorMain
        (
            creator.getBackupProvider(),
            creator.getConfigProvider(),
            creator.getBuilder(),
            creator.getHttpPort(),
            creator.getListenAddress(),
            creator.getSecurityHandler(),
            securityArguments
        );
        setShutdown(exhibitorMain);

        try {
            exhibitorMain.start();
        }
        catch (Exception ex) {
            ex.printStackTrace(System.err);
            System.err.println(String.format("Failed to start HTTP server on address %s, port %d. Exiting", creator.getListenAddress(), creator.getHttpPort()));
            Runtime.getRuntime().exit(1);
        }

        try {
            exhibitorMain.join();
        }
        finally
        {
            exhibitorMain.close();

            for ( Closeable closeable : creator.getCloseables() )
            {
                CloseableUtils.closeQuietly(closeable);
            }
        }
    }

    public ExhibitorMain(BackupProvider backupProvider, ConfigProvider configProvider, ExhibitorArguments.Builder builder, int httpPort, String listenAddress, SecurityHandler security, SecurityArguments securityArguments) throws Exception
    {
        HashLoginService realm = makeRealm(securityArguments);
        if ( securityArguments.getRemoteAuthSpec() != null )
        {
            addRemoteAuth(builder, securityArguments.getRemoteAuthSpec());
        }

        builder.shutdownProc(makeShutdownProc(this));
        exhibitor = new Exhibitor(configProvider, null, backupProvider, builder.build());
        exhibitor.start();

        ResourceConfig          application = JerseySupport.newApplicationConfig(new UIContext(exhibitor));
        ServletContainer        container = new ServletContainer(application);
        server = new Server();
        ServerConnector http = new ServerConnector(server);
        http.setHost(listenAddress);
        http.setPort(httpPort);
        server.addConnector(http);

        ServletContextHandler root = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
        root.addFilter(ExhibitorServletFilter.class, "/", EnumSet.of(DispatcherType.REQUEST));
        root.addServlet(new ServletHolder(container), "/*");
        if ( security != null )
        {
            root.setSecurityHandler(security);
        }
        else if ( securityArguments.getSecurityFile() != null )
        {
            addSecurityFile(realm, securityArguments.getSecurityFile(), root);
        }
    }

    private void addRemoteAuth(ExhibitorArguments.Builder builder, String remoteAuthSpec)
    {
        String[] parts = remoteAuthSpec.split(":");
        Preconditions.checkArgument(parts.length == 2, "Badly formed remote client authorization: " + remoteAuthSpec);

        String type = parts[0].trim();
        String userName = parts[1].trim();

        String password = Preconditions.checkNotNull(users.get(userName), "Realm user not found: " + userName);

        Feature feature;
        if ( type.equals("basic") )
        {
            feature = HttpAuthenticationFeature.basic(userName, password);
        }
        else if ( type.equals("digest") )
        {
            feature = HttpAuthenticationFeature.digest(userName, password);
        }
        else
        {
            throw new IllegalStateException("Unknown remote client authorization type: " + type);
        }

        builder.remoteConnectionConfiguration(new RemoteConnectionConfiguration(new ArrayList<ClientRequestFilter>(), Collections.singletonList(feature)));
    }

    public void start() throws Exception
    {
        server.start();
    }

    public void join()
    {
        try
        {
            while ( !shutdownSignaled.get() && !Thread.currentThread().isInterrupted() )
            {
                Thread.sleep(5000);
            }
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException
    {
        if ( isClosed.compareAndSet(false, true) )
        {
            log.info("Shutting down");

            CloseableUtils.closeQuietly(exhibitor);
            try
            {
                server.stop();
            }
            catch ( Exception e )
            {
                log.error("Error shutting down Jetty", e);
            }
            server.destroy();
        }
    }

    private static void setShutdown(final ExhibitorMain exhibitorMain)
    {
        Runtime.getRuntime().addShutdownHook
        (
            new Thread
            (
                makeShutdownProc(exhibitorMain)
            )
        );
    }

    private static Runnable makeShutdownProc(final ExhibitorMain exhibitorMain)
    {
        return new Runnable()
        {
            @Override
            public void run()
            {
                exhibitorMain.shutdownSignaled.set(true);
            }
        };
    }

    private void addSecurityFile(HashLoginService realm, String securityFile, ServletContextHandler root) throws Exception
    {
        // parse the security portion of the web.xml file by starting a throw-away Jetty web app that only reads that descriptor

        WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath("/");
        context.setBaseResource(Resource.newResource(System.getProperty("java.io.tmpdir")));
        context.setDescriptor(securityFile);
        context.setDefaultsDescriptor(null);
        context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});

        // Jetty 9 requires a login service while the security handler starts. Without a realm, use an empty one so that all authentication fails.
        LoginService loginService = (realm != null) ? realm : new HashLoginService("exhibitor");
        ConstraintSecurityHandler parsed = (ConstraintSecurityHandler)context.getSecurityHandler();
        parsed.setLoginService(loginService);

        // stopping the web app clears what it parsed, so copy the security settings first
        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        try
        {
            context.start();

            securityHandler.setConstraintMappings(new ArrayList<ConstraintMapping>(parsed.getConstraintMappings()), new HashSet<String>(parsed.getRoles()));
            securityHandler.setAuthMethod(parsed.getAuthMethod());
            securityHandler.setRealmName(parsed.getRealmName());
            securityHandler.setLoginService(loginService);
        }
        finally
        {
            context.stop();
        }

        root.setSecurityHandler(securityHandler);
    }

    private HashLoginService makeRealm(SecurityArguments securityArguments) throws Exception
    {
        if ( securityArguments.getRealmSpec() == null )
        {
            return null;
        }

        String[] parts = securityArguments.getRealmSpec().split(":");
        if ( parts.length != 2 )
        {
            throw new Exception("Bad realm spec: " + securityArguments.getRealmSpec());
        }

        PropertyUserStore userStore = new PropertyUserStore()
        {
            @Override
            public void addUser(String userName, Credential credential, String[] roles)
            {
                users.put(userName, String.valueOf(credential));

                super.addUser(userName, credential, roles);
            }
        };
        userStore.setConfig(parts[1].trim());
        userStore.start();  // loads the users now; remote auth needs their passwords below

        HashLoginService realm = new HashLoginService(parts[0].trim());
        realm.setUserStore(userStore);
        return realm;
    }
}

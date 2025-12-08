/**
 * Copyright (C) 2011-2022 Red Hat, Inc. (https://github.com/Commonjava/indy-sidecar)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.util.sidecar.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.commonjava.util.sidecar.services.PreSeedConstants.EVENT_PROXY_CONFIG_CHANGE;

@Startup
@ApplicationScoped
@RegisterForReflection
public class ProxyConfiguration
{
    public static final String USER_DIR = System.getProperty( "user.dir" ); // where the JVM was invoked

    private static final String PROXY_YAML = "proxy.yaml";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final Set<ServiceConfig> services = Collections.synchronizedSet( new HashSet<>() );

    @Inject
    transient EventBus bus;

    @Inject
    SidecarConfig sidecarConfig;

    @JsonProperty( "read-timeout" )
    private String readTimeout;

    private volatile Retry retry;

    private transient String stateHash; // used to check whether the custom proxy.yaml has changed

    public String getReadTimeout()
    {
        return readTimeout;
    }

    public Set<ServiceConfig> getServices()
    {
        return services;
    }

    public Retry getRetry()
    {
        return retry;
    }

    @Override
    public String toString()
    {
        return "ProxyConfiguration{" + "readTimeout='" + readTimeout + '\'' + ", retry=" + retry + ", services="
                        + services + '}';
    }

    @PostConstruct
    void init()
    {
        load( true );
        logger.info( "Proxy config, {}", this );
    }

    /**
     * Load proxy config from '${user.dir}/config/proxy.yaml'. If not found, load from default classpath resource.
     */
    public void load( boolean init )
    {
        File file = new File( USER_DIR, "config/" + PROXY_YAML );
        if ( file.exists() )
        {
            logger.info( "Load proxy config from file, {}", file );
            try (FileInputStream fis = new FileInputStream( file ))
            {
                doLoad( fis );
            }
            catch ( IOException e )
            {
                logger.error( "Load failed", e );
            }
        }
        else if ( init )
        {
            logger.info( "Load proxy config from classpath resource, {}", PROXY_YAML );
            InputStream res = this.getClass().getClassLoader().getResourceAsStream( PROXY_YAML );
            if ( res != null )
            {
                doLoad( res );
            }
        }
        else
        {
            logger.info( "Skip loading proxy config - no such file: {}", file );
        }
    }

    private void doLoad( InputStream res )
    {
        try
        {
            String str = IOUtils.toString( res, UTF_8 );
            String nextStateHash = DigestUtils.sha256Hex( str ).toUpperCase();
            if ( nextStateHash.equals( stateHash ) )
            {
                logger.info( "Skip, NO_CHANGE" );
                return;
            }

            ProxyConfiguration parsed = parseConfig( str );
            logger.debug( "Loaded from proxy yaml: {}", parsed );

            if ( parsed.readTimeout != null )
            {
                this.readTimeout = parsed.readTimeout;
            }

            this.retry = parsed.retry;
            String countEnv = System.getenv( "retry_count" );
            String intervalEnv = System.getenv( "retry_interval" );
            String maxBackOffEnv = System.getenv( "retry_maxBackOff" );
            this.retry.count = ( countEnv != null && !countEnv.trim().isEmpty() ) ?
                            Integer.valueOf( countEnv ) :
                            parsed.retry.count;
            this.retry.interval = ( intervalEnv != null && !intervalEnv.trim().isEmpty() ) ?
                            Long.valueOf( intervalEnv ) :
                            parsed.retry.interval;
            this.retry.maxBackOff = ( maxBackOffEnv != null && !maxBackOffEnv.trim().isEmpty() ) ?
                            Long.valueOf( maxBackOffEnv ) :
                            parsed.retry.maxBackOff;

            if ( parsed.services != null )
            {
                parsed.services.forEach( this::overrideIfPresent );
            }

            if ( stateHash != null )
            {
                bus.publish( EVENT_PROXY_CONFIG_CHANGE, "" );
            }
            stateHash = nextStateHash;

            logger.info( "Config loaded: {}", this );
        }
        catch ( IOException e )
        {
            logger.error( "Load failed", e );
        }
    }

    private void overrideIfPresent( ServiceConfig sv )
    {
        this.services.remove( sv ); // remove first so it can replace the old one
        this.services.add( sv );
    }

    private ProxyConfiguration parseConfig( String str )
    {
        Yaml yaml = new Yaml();
        Map<String, Object> obj = yaml.load( str );
        Map<String, Object> proxy = (Map) obj.get( "proxy" );
        JsonObject jsonObject = JsonObject.mapFrom( proxy );
        ProxyConfiguration ret = jsonObject.mapTo( ProxyConfiguration.class );
        if ( ret.services != null )
        {
            ret.services.forEach( ServiceConfig::normalize );
        }
        return ret;
    }

    @RegisterForReflection
    public static class Retry
    {
        public int count;

        public long interval; // millis

        public long maxBackOff; // millis

        @Override
        public String toString()
        {
            return "Retry{" + "count=" + count + ", interval=" + interval + ", maxBackOff=" + maxBackOff + '}';
        }

    }

}
/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/indy-sidecar)
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
package org.commonjava.util.sidecar.services;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.commonjava.util.sidecar.config.ProxyConfiguration;
import org.commonjava.util.sidecar.exception.ServiceNotFoundException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@ApplicationScoped
public class Classifier
{
    @Inject
    Vertx vertx;

    @Inject
    ProxyConfiguration serviceConfiguration;

    private Map<ProxyConfiguration.ServiceConfig, WebClient> clientMap = new ConcurrentHashMap<>();

    public <R> R classifyAnd( String path, HttpServerRequest request,
                              BiFunction<WebClient, ProxyConfiguration.ServiceConfig, R> action ) throws Exception
    {
        ProxyConfiguration.ServiceConfig service = getServiceConfig( path, request.method() );
        if ( service == null )
        {
            throw new ServiceNotFoundException( "Service not found, path: " + path + ", method: " + request.method() );
        }
        return action.apply( getWebClient( service ), service );
    }

    public <R> R classifyAnd( String path, HttpMethod method,
                              BiFunction<WebClient, ProxyConfiguration.ServiceConfig, R> action ) throws Exception
    {
        ProxyConfiguration.ServiceConfig service = getServiceConfig( path, method );
        if ( service == null )
        {
            throw new ServiceNotFoundException( "Service not found, path: " + path + ", method: " + method );
        }
        return action.apply( getWebClient( service ), service );
    }

    private ProxyConfiguration.ServiceConfig getServiceConfig( String path, HttpMethod method ) throws Exception
    {
        ProxyConfiguration.ServiceConfig service = null;

        Set<ProxyConfiguration.ServiceConfig> services = serviceConfiguration.getServices();
        if ( services != null )
        {
            for ( ProxyConfiguration.ServiceConfig sv : services )
            {
                if ( path.matches( sv.pathPattern ) && ( sv.methods == null || sv.methods.contains( method.name() ) ) )
                {
                    service = sv;
                    break;
                }
            }
        }
        return service;
    }

    private WebClient getWebClient( ProxyConfiguration.ServiceConfig service ) throws Exception
    {
        return clientMap.computeIfAbsent( service, k -> {
            WebClientOptions options = new WebClientOptions().setDefaultHost( k.host ).setDefaultPort( k.port );
            if ( k.ssl )
            {
                options.setSsl( true ).setVerifyHost( false ).setTrustAll( true );
            }
            return WebClient.create( vertx, options );
        } );
    }
}
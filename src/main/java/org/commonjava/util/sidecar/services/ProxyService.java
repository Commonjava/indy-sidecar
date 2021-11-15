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

import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import org.apache.commons.io.IOUtils;
import org.commonjava.util.sidecar.config.ProxyConfiguration;
import org.commonjava.util.sidecar.interceptor.ExceptionHandler;
import org.commonjava.util.sidecar.interceptor.MetricsHandler;
import org.commonjava.util.sidecar.util.BufferStreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.vertx.core.http.impl.HttpUtils.normalizePath;
import static javax.ws.rs.core.HttpHeaders.HOST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.EXTERNAL_ID;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.TRACE_ID;
import static org.commonjava.util.sidecar.services.ProxyConstants.EVENT_PROXY_CONFIG_CHANGE;

@ApplicationScoped
@ExceptionHandler
@MetricsHandler
public class ProxyService
{
    public final static String HEADER_PROXY_TRACE_ID = "Proxy-Trace-Id";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final String PROXY_ORIGIN = "proxy-origin";

    private long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis( 30 ); // default 30 minutes

    private long DEFAULT_BACKOFF_MILLIS = Duration.ofSeconds( 3 ).toMillis();

    private long DEFAULT_MAX_BACKOFF_MILLIS = Duration.ofSeconds( 15 ).toMillis();

    private volatile long timeout;

    @Inject
    ProxyConfiguration proxyConfiguration;

    @Inject
    Classifier classifier;

    @PostConstruct
    void init()
    {
        timeout = readTimeout();
        logger.debug( "Init, timeout: {}", timeout );
    }

    long readTimeout()
    {
        long t = DEFAULT_TIMEOUT;
        String readTimeout = proxyConfiguration.getReadTimeout();
        if ( isNotBlank( readTimeout ) )
        {
            try
            {
                t = Duration.parse( "pt" + readTimeout ).toMillis();
            }
            catch ( Exception e )
            {
                logger.error( "Failed to parse proxy.read-timeout, use default " + DEFAULT_TIMEOUT, e );
            }
        }
        return t;
    }

    @ConsumeEvent( value = EVENT_PROXY_CONFIG_CHANGE )
    void handleConfigChange( String message )
    {
        timeout = readTimeout();
        logger.debug( "Handle event {}, refresh timeout: {}", EVENT_PROXY_CONFIG_CHANGE, timeout );
    }

    @GET
    public Uni<Response> doHead( String path, HttpServerRequest request ) throws Exception
    {
        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request,
                (client, service) -> wrapAsyncCall( client.head( p )
                        .putHeaders( getHeaders( request ) )
                        .timeout( timeout )
                        .send() ) ), request );
    }

    @GET
    public Uni<Response> doGet( String path, HttpServerRequest request ) throws Exception
    {
        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request,
                (client, service) ->  wrapAsyncCall( client.get( p )
                                .putHeaders( getHeaders( request ) )
                                .timeout( timeout )
                                .send()) ), request );
    }

    @GET
    public Uni<Response> doPost( String path, InputStream is, HttpServerRequest request ) throws Exception
    {
        Buffer buf = Buffer.buffer( IOUtils.toByteArray( is ) );

        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request,
                (client, service) -> wrapAsyncCall( client.post( p )
                        .putHeaders( getHeaders( request ) )
                        .timeout( timeout )
                        .sendBuffer( buf ) ) ), request );
    }

    @GET
    public Uni<Response> doPut( String path, InputStream is, HttpServerRequest request ) throws Exception
    {
        Buffer buf = Buffer.buffer( IOUtils.toByteArray( is ) );

        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request,
                (client, service) -> wrapAsyncCall( client.put( p )
                        .putHeaders( getHeaders( request ) )
                        .timeout( timeout )
                        .sendBuffer( buf ) ) ), request );
    }

    @GET
    public Uni<Response> doDelete( String path, HttpServerRequest request ) throws Exception
    {
        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request,
                (client, service) -> wrapAsyncCall( client.delete( p )
                        .putHeaders( getHeaders( request ) )
                        .timeout( timeout )
                        .send() ) ), request );
    }

    private Uni<Response> wrapAsyncCall( Uni<HttpResponse<Buffer>> asyncCall )
    {
        ProxyConfiguration.Retry retry = proxyConfiguration.getRetry();
        Uni<Response> ret = asyncCall.onItem().transform( this::convertProxyResp );

        if ( retry.count > 0 )
        {
            long backOff = retry.interval;
            long maxBackOff = retry.maxBackOff;
            if ( retry.interval <= 0 )
            {
                backOff = DEFAULT_BACKOFF_MILLIS;
            }
            if ( retry.maxBackOff <= 0 )
            {
                maxBackOff = DEFAULT_MAX_BACKOFF_MILLIS;
            }
            logger.info( "Retry in use: retry count={}, interval={}, maxBackOff={}",
                         retry.count, Duration.ofMillis( backOff ), Duration.ofMillis( maxBackOff ) );
            ret = ret.onFailure( t -> ( t instanceof IOException || t instanceof VertxException ) )
                    .retry()
                    .withBackOff( Duration.ofMillis( backOff ), Duration.ofMillis( maxBackOff ) )
                    .atMost( retry.count );
        }
        return ret.onFailure().recoverWithItem( this::handleProxyException );
    }

    /**
     * Send status 500 with error message body.
     * @param t error
     */
    Response handleProxyException( Throwable t )
    {
        logger.error( "Proxy error", t );
        return Response.status( INTERNAL_SERVER_ERROR ).entity( t + ". Caused by: " + t.getCause() ).build();
    }

    /**
     * Read status and headers from proxy resp and set them to direct response.
     * @param resp proxy resp
     */
    private Response convertProxyResp( HttpResponse<Buffer> resp )
    {
        logger.debug( "Proxy resp: {} {}", resp.statusCode(), resp.statusMessage() );
        logger.trace( "Raw resp headers:\n{}", resp.headers() );
        Response.ResponseBuilder builder = Response.status( resp.statusCode(), resp.statusMessage() );
        resp.headers().forEach( header -> {
            if ( respHeaderAllowed( header ) )
            {
                builder.header( header.getKey(), header.getValue() );
            }
        } );
        if ( resp.body() != null )
        {
            StreamingOutput so = new BufferStreamingOutput( resp );
            builder.entity( so );
        }
        return builder.build();
    }

    /**
     * Raw content-length/connection header breaks http2 protocol. It is safe to exclude them.
     */
    private boolean respHeaderAllowed( Map.Entry<String, String> header )
    {
        String key = header.getKey();
        return !( key.equalsIgnoreCase( "content-length" ) || key.equalsIgnoreCase( "connection" ) );
    }

    private io.vertx.mutiny.core.MultiMap getHeaders( HttpServerRequest request )
    {
        MultiMap headers = request.headers();
        io.vertx.mutiny.core.MultiMap ret = io.vertx.mutiny.core.MultiMap.newInstance( headers )
                .remove( HOST )
                .add( TRACE_ID, getTraceId( headers ) );
        try
        {
            URL url = new URL( request.absoluteURI() );
            String protocol = url.getProtocol();
            String authority = url.getAuthority();
            ret.add( PROXY_ORIGIN, String.format( "%s://%s", protocol, authority ) );
        }
        catch ( MalformedURLException e )
        {
            logger.error( "Failed to parse URI", e );
        }
        logger.trace( "Req headers:\n{}", ret );
        return ret;
    }

    /**
     * Get 'trace-id'. If client specify an 'external-id', use it. Otherwise, use an generated uuid. Services under the hook
     * should use the hereby created 'trace-id', rather than to generate their own.
     */
    private String getTraceId( MultiMap headers )
    {
        String externalID = headers.get( EXTERNAL_ID );
        return isNotBlank( externalID ) ? externalID : UUID.randomUUID().toString();
    }

    @FunctionalInterface
    private interface Function<T, R>
    {
        R apply( T t ) throws Exception;
    }

    private Uni<Response> normalizePathAnd( String path, Function<String, Uni<Response>> action, HttpServerRequest request ) throws Exception
    {
        String traceId = UUID.randomUUID().toString();
        request.headers().set( HEADER_PROXY_TRACE_ID, traceId );
        return action.apply( normalizePath( path ) );
    }

}
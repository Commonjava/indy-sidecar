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
import org.commonjava.util.sidecar.model.*;
import org.commonjava.util.sidecar.util.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import static org.commonjava.util.sidecar.services.ProxyConstants.CONTENT_REST_BASE_PATH;
import static org.commonjava.util.sidecar.services.ProxyConstants.EVENT_PROXY_CONFIG_CHANGE;
import static org.commonjava.util.sidecar.util.SidecarUtils.getBuildConfigId;

@ApplicationScoped
@ExceptionHandler
@MetricsHandler
public class ProxyService
{
    public final static String HEADER_PROXY_TRACE_ID = "Proxy-Trace-Id";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis( 30 ); // default 30 minutes

    private long DEFAULT_BACKOFF_MILLIS = Duration.ofSeconds( 5 ).toMillis();

    private volatile long timeout;

    @Inject
    ProxyConfiguration proxyConfiguration;

    @Inject
    Classifier classifier;

    @Inject
    ReportService reportService;

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

    public Uni<Response> doHead( String packageType, String type, String name, String path, HttpServerRequest request )
                    throws Exception
    {
        String contentPath = UrlUtils.buildUrl( CONTENT_REST_BASE_PATH, packageType, type, name, path );
        return doHead( contentPath, request );
    }

    public Uni<Response> doHead( String path, HttpServerRequest request ) throws Exception
    {
        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request, ( client, service ) -> wrapAsyncCall(
                        client.head( p ).putHeaders( getHeaders( request ) ).timeout( timeout ).send(), null ) ), request );
    }

    public Uni<Response> doGet( String packageType, String type, String name, String path, HttpServerRequest request )
                    throws Exception
    {
        String contentPath = UrlUtils.buildUrl( CONTENT_REST_BASE_PATH, packageType, type, name, path );
        return doGet( contentPath, request );
    }

    public Uni<Response> doGet( String path, HttpServerRequest request ) throws Exception
    {
        TrackedContentEntry entry;
        if ( getBuildConfigId() != null && path.split( "/" )[2].equals( "maven" ) )
        {
            entry = new TrackedContentEntry(
                            new TrackingKey( getBuildConfigId() ),
                            generateStoreKey( path ), AccessChannel.NATIVE,
                            "", "/" + path.replaceFirst( "^\\/?(\\w+\\/){5}","" ), StoreEffect.DOWNLOAD, (long) 0,
                            "", "", "" );
        }
        else {
            entry = null;
        }
        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request, ( client, service ) -> wrapAsyncCall(
                        client.get( p ).putHeaders( getHeaders( request ) ).timeout( timeout ).send(), entry ) ),
                                 request );
    }

    public Uni<Response> doPost( String path, InputStream is, HttpServerRequest request ) throws Exception
    {
        byte[] bytes = IOUtils.toByteArray( is );
        Buffer buf = Buffer.buffer( bytes );

        if ( getBuildConfigId() != null && path.split( "/" )[2].equals( "maven" ) )
        {
            TrackedContentEntry entry = new TrackedContentEntry(
                            new TrackingKey( getBuildConfigId() ),
                            generateStoreKey( path ), AccessChannel.NATIVE,
                            "http://" + proxyConfiguration.getServices().iterator().next().host + "/" + path, path, StoreEffect.UPLOAD, (long) 0,
                            "", "", "" );

            entry.setSize( (long) bytes.length );
            MessageDigest message;
            try
            {
                message = MessageDigest.getInstance( "MD5" );
                message.update( bytes );
                entry.setMd5( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                message = MessageDigest.getInstance( "SHA-1" );
                message.update( bytes );
                entry.setSha1( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                message = MessageDigest.getInstance( "SHA-256" );
                message.update( bytes );
                entry.setSha256( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                reportService.appendUpload( entry );
            }
            catch ( NoSuchAlgorithmException e )
            {
                logger.warn( "Bytes hash calculation failed for request" );
            }
        }

        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request, ( client, service ) -> wrapAsyncCall(
                        client.post( p ).putHeaders( getHeaders( request ) ).timeout( timeout ).sendBuffer( buf ), null ) ),
                                 request );
    }

    public Uni<Response> doPut( String packageType, String type, String name, String path, HttpServerRequest request )
                    throws Exception
    {
        String contentPath = UrlUtils.buildUrl( CONTENT_REST_BASE_PATH, packageType, type, name, path );
        return normalizePathAnd( contentPath, p -> classifier.classifyAnd( p, request,
                                                                           ( client, service ) -> wrapAsyncCall(
                                                                                           client.put( p )
                                                                                                 .putHeaders( getHeaders(
                                                                                                                 request ) )
                                                                                                 .timeout( timeout )
                                                                                                 .send(), null ) ), request );
    }

    public Uni<Response> doPut( String path, InputStream is, HttpServerRequest request ) throws Exception
    {

        byte[] bytes = IOUtils.toByteArray( is );
        Buffer buf = Buffer.buffer( bytes );

        if ( getBuildConfigId() != null && path.split( "/" )[2].equals( "maven" ) )
        {
            TrackedContentEntry entry = new TrackedContentEntry(
                            new TrackingKey( getBuildConfigId() ),
                            generateStoreKey( path ), AccessChannel.NATIVE,
                            "http://" + proxyConfiguration.getServices().iterator().next().host + "/" + path, path, StoreEffect.UPLOAD, (long) 0,
                            "", "", "" );

            entry.setSize( (long) bytes.length );
            MessageDigest message;
            try
            {
                message = MessageDigest.getInstance( "MD5" );
                message.update( bytes );
                entry.setMd5( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                message = MessageDigest.getInstance( "SHA-1" );
                message.update( bytes );
                entry.setSha1( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                message = MessageDigest.getInstance( "SHA-256" );
                message.update( bytes );
                entry.setSha256( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                reportService.appendUpload( entry );
            }
            catch ( NoSuchAlgorithmException e )
            {
                logger.warn( "Bytes hash calculation failed for request" );
            }
        }

        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request, ( client, service ) -> wrapAsyncCall(
                        client.put( p ).putHeaders( getHeaders( request ) ).timeout( timeout ).sendBuffer( buf ), null ) ),
                                 request );
    }

    public Uni<Response> doDelete( String path, HttpServerRequest request ) throws Exception
    {
        return normalizePathAnd( path, p -> classifier.classifyAnd( p, request,
                (client, service) -> wrapAsyncCall( client.delete( p )
                        .putHeaders( getHeaders( request ) )
                        .timeout( timeout )
                        .send(), null ) ), request );
    }

    private Uni<Response> wrapAsyncCall( Uni<HttpResponse<Buffer>> asyncCall, TrackedContentEntry entry)
    {
        ProxyConfiguration.Retry retry = proxyConfiguration.getRetry();
        Uni<Response> ret = asyncCall.onItem().transform( buf -> convertProxyResp( buf, entry ) );
        if ( retry.count > 0 )
        {
            long backOff = retry.interval;
            if ( retry.interval <= 0 )
            {
                backOff = DEFAULT_BACKOFF_MILLIS;
            }
            ret = ret.onFailure( t -> ( t instanceof IOException || t instanceof VertxException ) )
                     .retry()
                     .withBackOff( Duration.ofMillis( backOff ) )
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
    private Response convertProxyResp( HttpResponse<Buffer> resp , TrackedContentEntry entry)
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
            byte[] bytes = resp.body().getBytes();
            if( entry != null)
            {
                entry.setSize( (long) bytes.length );
                String[] headers = resp.getHeader( "indy-origin" ).split( ":" ) ;
                entry.setOriginUrl( "http://" + proxyConfiguration.getServices().iterator().next().host
                                                    + "/api/content/" + headers[0]+ "/" + headers[1]+ "/" + headers[2]
                                                    + entry.getPath() );
                logger.info( entry.getOriginUrl() );
                MessageDigest message;
                try
                {
                    message = MessageDigest.getInstance( "MD5" );
                    message.update( bytes );
                    entry.setMd5( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                    message = MessageDigest.getInstance( "SHA-1" );
                    message.update( bytes );
                    entry.setSha1( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                    message = MessageDigest.getInstance( "SHA-256" );
                    message.update( bytes );
                    entry.setSha256( DatatypeConverter.printHexBinary( message.digest() ).toLowerCase() );
                    reportService.appendDownload( entry );
                }
                catch ( NoSuchAlgorithmException e )
                {
                    logger.warn( "Bytes hash calculation failed for request" );
                }
            }
            builder.entity( bytes );
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

    private StoreKey generateStoreKey ( String path ){
        String[] elements = path.split("/");
        return new StoreKey(elements[2],StoreType.valueOf( elements[3] ),elements[4]);
    }

}
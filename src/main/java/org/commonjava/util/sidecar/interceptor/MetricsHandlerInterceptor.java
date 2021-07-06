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
package org.commonjava.util.sidecar.interceptor;

import io.honeycomb.beeline.tracing.Span;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import org.commonjava.util.sidecar.config.SidecarHoneycombConfiguration;
import org.commonjava.util.sidecar.metrics.SidecarHoneycombManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.currentTimeMillis;
import static org.commonjava.util.sidecar.metrics.MetricFieldsConstants.FUNCTION;
import static org.commonjava.util.sidecar.metrics.MetricFieldsConstants.SERVICE;

@Interceptor
@MetricsHandler
public class MetricsHandlerInterceptor
{

    public final static String HEADER_PROXY_SPAN_ID = "Proxy-Span-Id";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    SidecarHoneycombConfiguration honeycombConfiguration;

    @Inject
    SidecarHoneycombManager honeycombManager;

    @AroundInvoke
    public Object handle( InvocationContext invocationContext ) throws Exception
    {
        Object ret = invocationContext.proceed();
        if ( ret instanceof Uni )
        {
            Object[] params = invocationContext.getParameters();
            if ( params != null )
            {
                for ( Object param : params )
                {
                    if ( param instanceof HttpServerRequest )
                    {
                        ret = wrapWithMetric( (HttpServerRequest) param, (Uni) ret );
                        break;
                    }
                }
            }
        }
        return ret;
    }

    private Object wrapWithMetric( HttpServerRequest request, Uni<Object> uni )
    {
        if ( honeycombConfiguration.isEnabled() )
        {
            AtomicLong t = new AtomicLong();
            return uni.onSubscribe()
                      .invoke( () -> startTrace( t, request ) )
                      .onItemOrFailure()
                      .invoke( ( item, err ) -> endTrace( t, request, item, err ) );
        }
        return uni;
    }

    /**
     * Do not really start span but only record the start time.
     */
    private void startTrace( AtomicLong t, HttpServerRequest request )
    {
        if ( honeycombManager != null )
        {
            t.set( currentTimeMillis() );
            logger.debug( "Subscribe, path: {}", request.path() );
        }
    }

    /**
     * Close trace and add fields. We start and close span in this method because the onSubscribe happens in different
     * thread which is not in line with the default Honeycomb tracer.
     */
    private void endTrace( AtomicLong t, HttpServerRequest request, Object item, Throwable err )
    {
        if ( honeycombManager != null )
        {
            logger.debug( "Done, item: {}, err: {}", item, err );
            String path = request.path();
            String funcName = honeycombConfiguration.getFunctionName( path );
            if ( funcName == null )
            {
                return;
            }
            Span span = honeycombManager.startRootTracer( "sidecar-" + honeycombConfiguration.getFunctionName( path ) ); //
            if ( span != null )
            {
                request.headers().set( HEADER_PROXY_SPAN_ID, span.getSpanId() );
            }
            long elapse = currentTimeMillis() - t.get();
            honeycombManager.addSpanField( SERVICE, honeycombConfiguration.getServiceName() );
            honeycombManager.addSpanField( FUNCTION, funcName);
            honeycombManager.addFields( elapse, request, item, err );
            honeycombManager.addRootSpanFields();
            honeycombManager.endTrace();
        }
    }
}

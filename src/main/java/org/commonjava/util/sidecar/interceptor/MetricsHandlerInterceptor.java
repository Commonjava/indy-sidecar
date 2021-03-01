package org.commonjava.util.sidecar.interceptor;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.System.currentTimeMillis;

@Interceptor
@MetricsHandler
public class MetricsHandlerInterceptor
{
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
            honeycombManager.startRootTracer( "sidecar-" + honeycombConfiguration.getFunctionName( path ) ); //
            long elapse = currentTimeMillis() - t.get();
            honeycombManager.addFields( elapse, request, item, err );
            honeycombManager.addRootSpanFields();
            honeycombManager.endTrace();
        }
    }
}

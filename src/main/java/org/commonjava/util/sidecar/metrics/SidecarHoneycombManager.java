package org.commonjava.util.sidecar.metrics;

import io.vertx.core.http.HttpServerRequest;
import org.commonjava.o11yphant.honeycomb.HoneycombManager;
import org.commonjava.o11yphant.honeycomb.SimpleTraceSampler;
import org.commonjava.util.sidecar.config.SidecarHoneycombConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.commonjava.o11yphant.metrics.RequestContextConstants.CLIENT_ADDR;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.EXTERNAL_ID;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.HTTP_METHOD;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.HTTP_STATUS;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.REQUEST_LATENCY_MILLIS;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.REST_ENDPOINT_PATH;
import static org.commonjava.o11yphant.metrics.RequestContextConstants.TRACE_ID;
import static org.commonjava.util.sidecar.config.SidecarHoneycombConfiguration.ERROR_CLASS;
import static org.commonjava.util.sidecar.config.SidecarHoneycombConfiguration.ERROR_MESSAGE;

@ApplicationScoped
public class SidecarHoneycombManager
                extends HoneycombManager
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    public SidecarHoneycombManager( SidecarHoneycombConfiguration honeycombConfiguration )
    {
        super( honeycombConfiguration, new SimpleTraceSampler( honeycombConfiguration ) );
    }

    @PostConstruct
    public void init()
    {
        super.init();
//        registerRootSpanFields( GoldenSignalsRootSpanFields.getInstance() );
    }

    public void addFields( long elapse, HttpServerRequest request, Object item, Throwable err )
    {
        configuration.getFieldSet().forEach( field -> {
            Object value = getContext( elapse, field, request, item, err );
            if ( value != null )
            {
                logger.trace( "Add field, {}={}", field, value );
                addSpanField( field, value );
            }
        } );
    }

    private Object getContext( long elapse, String field, HttpServerRequest request, Object item, Throwable err )
    {
        Response resp = null;
        if ( item instanceof Response )
        {
            resp = (Response) item;
        }

        Object ret = null;
        switch ( field )
        {
            case HTTP_METHOD:
                ret = request.rawMethod();
                break;
            case HTTP_STATUS:
                ret = ( resp != null ? resp.getStatus() : null );
                break;
            case TRACE_ID:
                ret = request.getHeader( EXTERNAL_ID );
                break;
            case CLIENT_ADDR:
                ret = request.remoteAddress().host();
                break;
            case REST_ENDPOINT_PATH:
                ret = request.path();
                break;
            case REQUEST_LATENCY_MILLIS:
                ret = elapse;
                break;
            case ERROR_MESSAGE:
                ret = ( err != null ? err.getMessage() : null );
                break;
            case ERROR_CLASS:
                ret = ( err != null ? err.getClass().getName() : null );
                break;
            default:
                break;
        }
        return ret;
    }

}

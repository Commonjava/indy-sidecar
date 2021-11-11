package org.commonjava.util.sidecar.util;

import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.commonjava.util.sidecar.model.TrackedContentEntry;
import org.commonjava.util.sidecar.services.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BufferStreamingOutput
                implements StreamingOutput
{
    private static final String MD5 = "MD5";
    private static final String SHA1 = "SHA-1";
    private static final String SHA256 = "SHA-256";
    private static final String[] DIGESTS = { MD5, SHA1, SHA256 };

    private static final int bufSize = 10 * 1024 * 1024;

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private HttpResponse<Buffer> response;

    private TrackedContentEntry entry;

    private final String serviceOrigin;

    private final String indyOrigin;

    private ReportService reportService;

    private Map<String, MessageDigest> digests = new HashMap<>();

    private Supplier<OutputStream> cacheStreamSupplier;

    public BufferStreamingOutput( HttpResponse<Buffer> response, TrackedContentEntry entry, String serviceOrigin,
                                  String indyOrigin, ReportService reportService )
    {
        this.response = response;
        this.entry = entry;
        this.serviceOrigin = serviceOrigin;
        this.indyOrigin = indyOrigin;
        this.reportService = reportService;
        for ( String key : DIGESTS )
        {
            try
            {
                digests.put( key, MessageDigest.getInstance( key ) );
            }
            catch ( NoSuchAlgorithmException e )
            {
                logger.warn( "Bytes hash calculation failed for request. Cannot get digest of type: {}", key );
            }
        }
    }

    @Override
    public void write( OutputStream output ) throws IOException, WebApplicationException
    {
        OutputStream cacheStream = null;
        try(CountingOutputStream cout = new CountingOutputStream( output ))
        {
            OutputStream out = cout;
            if ( cacheStreamSupplier != null )
            {
                cacheStream = cacheStreamSupplier.get();
                if ( cacheStream != null )
                {
                    out = new TeeOutputStream( cacheStream, output );
                }
            }

            Buffer buffer = response.bodyAsBuffer();
            int total = buffer.length();
            int transferred = 0;
            while ( transferred < total )
            {
                int next = bufSize < total ? bufSize : total;
                byte[] bytes = buffer.getBytes( transferred, next );
                out.write( bytes );
                if ( entry != null )
                {
                    digests.values().forEach( d->d.update( bytes ) );
                }

                transferred = next;
            }
            out.flush();

            if ( entry != null )
            {
                entry.setSize( cout.getByteCount() );
                String[] headers = indyOrigin.split( ":" );
                entry.setOriginUrl(
                                serviceOrigin + "/api/content/"
                                                + headers[0] + "/" + headers[1] + "/" + headers[2] + entry.getPath() );
                if ( digests.containsKey( MD5 ))
                    entry.setMd5( DatatypeConverter.printHexBinary( digests.get(MD5).digest() ).toLowerCase() );

                if ( digests.containsKey( SHA1 ))
                    entry.setSha1( DatatypeConverter.printHexBinary( digests.get( SHA1 ).digest() ).toLowerCase() );

                if ( digests.containsKey( SHA256 ))
                    entry.setSha256( DatatypeConverter.printHexBinary( digests.get(SHA256).digest() ).toLowerCase() );
                
                reportService.appendDownload( entry );
            }
        }
        finally
        {
            if ( cacheStream != null )
            {
                try
                {
                    cacheStream.close();
                }
                catch ( Exception e )
                {
                    logger.error( "Failed to close cache stream: " + e.getMessage(), e );
                }
            }
        }
    }

    public void setCacheStreamSupplier( Supplier<OutputStream> cacheStreamSupplier )
    {
        this.cacheStreamSupplier = cacheStreamSupplier;
    }
}

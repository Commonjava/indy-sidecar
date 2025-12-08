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
package org.commonjava.util.sidecar.util;

import io.opentelemetry.api.trace.Span;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.apache.commons.io.output.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

public class ProxyStreamingOutput
                implements StreamingOutput
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private static final long bufSize = 10 * 1024 * 1024;

    private final ResponseBody responseBody;

    private final OtelAdapter otel;

    public ProxyStreamingOutput( ResponseBody responseBody, OtelAdapter otel )
    {
        this.responseBody = responseBody;
        this.otel = otel;
    }

    @Override
    public void write( OutputStream output ) throws IOException
    {
        if ( responseBody != null )
        {
            try (CountingOutputStream cout = new CountingOutputStream( output ))
            {
                OutputStream out = cout;
                BufferedSource peek = responseBody.source().peek();
                while ( !peek.exhausted() )
                {
                    byte[] bytes;
                    if ( peek.request( bufSize ) )
                    {
                        bytes = peek.readByteArray(
                                        bufSize ); // byteCount bytes will be removed from current buffer after read
                    }
                    else
                    {
                        bytes = peek.readByteArray();
                    }
                    out.write( bytes );
                }
                out.flush();
                peek.close();
                if ( otel.enabled() )
                {
                    Span.current().setAttribute( "response.content_length", cout.getByteCount() );
                }
            }
            finally
            {
                if ( responseBody == null )
                {
                    return;
                }
                responseBody.close();
            }
        }
        else
        {
            if ( otel.enabled() )
            {
                Span.current().setAttribute( "response.content_length", 0 );
            }
        }
    }
}
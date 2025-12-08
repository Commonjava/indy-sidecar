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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import okhttp3.Request;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OtelAdapter
{
    private static final TextMapSetter<? super Request.Builder> OKHTTP_CONTEXT_SETTER =
            ( rb, key, value ) -> rb.header( key, value );

    @ConfigProperty( name = "quarkus.opentelemetry.enabled" )
    Boolean enabled;

    @Inject
    OpenTelemetry otel;

    public boolean enabled()
    {
        return enabled == Boolean.TRUE;
    }

    public Span newClientSpan( String adapterName, String name )
    {
        if ( !enabled )
        {
            return Span.getInvalid();
        }
        return otel.getTracer( adapterName ).spanBuilder( name ).setSpanKind( SpanKind.CLIENT ).startSpan();
    }

    public void injectContext( Request.Builder requestBuilder )
    {
        if ( !enabled )
        {
            return;
        }

        otel.getPropagators().getTextMapPropagator().inject( Context.current(), requestBuilder, OKHTTP_CONTEXT_SETTER );
    }
}
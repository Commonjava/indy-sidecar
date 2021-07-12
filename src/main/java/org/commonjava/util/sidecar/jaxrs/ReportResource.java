package org.commonjava.util.sidecar.jaxrs;

import io.smallrye.mutiny.Uni;
import org.commonjava.util.sidecar.model.TrackedContent;
import org.commonjava.util.sidecar.services.ReportService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

//Change path when Newcastle build complete signal API is integrated
@Path( "/api/folo/track/{id}/record" )
public class ReportResource
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    ReportService reportService;

    @Operation( description = "Retrieve tracking report content from memory" )
    @APIResponse( responseCode = "200", description = "Tracking report content" )
    @APIResponse( responseCode = "500", description = "Tracking report generation failed" )
    @Produces( APPLICATION_JSON )
    @GET
    public TrackedContent get (){
        return reportService.getTrackedContent(); //this will automatically serialized by jackson
    }

    @Operation( description = "export tracking report content to configured indy" )
    @APIResponse( responseCode = "200", description = "Tracking report exported" )
    @APIResponse( responseCode = "404", description = "No tracking report found" )
    @Path( "/export" )
    @Produces( TEXT_PLAIN )
    @GET
    public Uni<Response> exportGet () throws Exception
    {
        return reportService.exportReport();
    }

    @Operation( description = "Delete tracking report content from memory" )
    @APIResponse( responseCode = "200", description = "Tracking report deleted" )
    @APIResponse( responseCode = "404", description = "No tracking report found" )
    @Produces( APPLICATION_JSON )
    @DELETE
    public TrackedContent delete (){
        reportService.clearReport();
        return reportService.getTrackedContent();
    }
}

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
package org.commonjava.util.sidecar.ftest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.commonjava.test.http.expect.ExpectationServer;
import org.commonjava.test.http.quarkus.InjectExpected;
import org.commonjava.util.sidecar.ftest.profile.SidecarFunctionProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;
import static io.restassured.parsing.Parser.JSON;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
@TestProfile( SidecarFunctionProfile.class )
@Tag( "function" )
public class TrackedContentDownloadTest
        extends AbstractSidecarFuncTest
{
    @InjectExpected(port = 10028)
    ExpectationServer server;
    /**
     * <b>GIVEN:</b>
     * <ul>
     *     <li>The tracked file exists in local FS</li>
     * </ul>
     *
     * <br/>
     * <b>WHEN:</b>
     * <ul>
     *     <li>Request folo content downloading</li>
     * </ul>
     *
     * <br/>
     * <b>THEN:</b>
     * <ul>
     *     <li>The tracked content can be retrieved successfully and respond with correct tracked json</li>
     * </ul>
     */
    @Test
    public void testTrackedFileDownloadContent()
            throws Exception
    {
        final String path = "/api/folo/track/2021/maven/hosted/shared-imports/9000";
        server.expect( path, OK.getStatusCode(), "" );
        RestAssured.registerParser( "application/octet-stream", JSON );
        given().when()
               .get( "/api/folo/track/2021/maven/hosted/shared-imports/9000" )
               .then()
               .body( containsString( "/org/apache/maven/maven-core/3.0/maven-core-3.0.jar" ) )
               .statusCode( OK.getStatusCode() )
               .contentType( MediaType.APPLICATION_OCTET_STREAM );
    }

    /**
     * <b>GIVEN:</b>
     * <ul>
     *     <li>The tracked file doesn't exist in local FS</li>
     * </ul>
     *
     * <br/>
     * <b>WHEN:</b>
     * <ul>
     *     <li>Request folo content downloading</li>
     * </ul>
     *
     * <br/>
     * <b>THEN:</b>
     * <ul>
     *     <li>The tracked content can not be found</li>
     * </ul>
     */
    @Test
    public void testMissingTrackedFileDownloadContent()
    {
        given().when()
               .get( "/api/folo/track/2021/maven/hosted/shared-imports/9001" )
               .then()
               .statusCode( NOT_FOUND.getStatusCode() );
    }
}

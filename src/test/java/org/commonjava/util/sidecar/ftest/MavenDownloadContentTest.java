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
import org.commonjava.test.http.expect.ExpectationServer;
import org.commonjava.test.http.quarkus.InjectExpected;
import org.commonjava.util.sidecar.ftest.profile.SidecarFunctionProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;

@QuarkusTest
@TestProfile( SidecarFunctionProfile.class )
@Tag( "function" )
public class MavenDownloadContentTest
        extends AbstractSidecarFuncTest
{
    @InjectExpected(port = 10028)
    ExpectationServer server;
    /**
     * <b>GIVEN:</b>
     * <ul>
     *     <li>The artifact exists in local FS</li>
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
     *     <li>The artifact can be downloaded successfully</li>
     * </ul>
     */
    @Test
    public void testArtifactDownloadContent()
            throws Exception
    {
        final String path =
                "/api/folo/track/2021/maven/hosted/shared-imports/org/apache/maven/maven-core/3.0/maven-core-3.0.jar";
        server.expect( path, OK.getStatusCode(), "" );
        given().when().get( path ).then().statusCode( OK.getStatusCode() );
    }

    /**
     * <b>GIVEN:</b>
     * <ul>
     *     <li>The artifact doesn't exist in local FS</li>
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
     *     <li>The artifact can not be found</li>
     * </ul>
     */
    @Test
    public void testMissingArtifactDownloadContent()
    {
        given().when()
               .get( "/api/folo/track/2021/maven/hosted/shared-imports/org/apache/maven/maven-core/3.1/maven-core-3.1.jar" )
               .then()
               .statusCode( NOT_FOUND.getStatusCode() );
    }
}

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
package org.commonjava.util.sidecar.services;

import org.commonjava.util.sidecar.config.SidecarConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.util.Optional;

import static org.commonjava.util.sidecar.services.PreSeedConstants.DEFAULT_REPO_PATH;

@ApplicationScoped
public class ArchiveRetrieveService
{
    private final static String BUILD_CONFIG_ID = "BUILD_CONFIG_ID";

    private final static String MAVEN_META = "maven-metadata.xml";

    private final static String NPM_META = "package.json";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    SidecarConfig sidecarConfig;

    public Optional<File> getLocally( final String path )
    {
        File download = new File( sidecarConfig.localRepository().orElse( DEFAULT_REPO_PATH ) + File.separator + path );
        if ( !download.exists() )
        {
            return Optional.empty();
        }
        return Optional.of( download );
    }

    public boolean shouldProxy( final String path )
    {
        return getBuildConfigId() == null || getBuildConfigId().trim().isEmpty() || path.endsWith( MAVEN_META )
                        || path.endsWith( NPM_META );
    }

    /**
     * This is used for mock testing of BuildConfigIdEnvMock
     */
    public String getBuildConfigId()
    {
        return System.getenv( BUILD_CONFIG_ID );
    }
}

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
package org.commonjava.util.sidecar.jaxrs.mock;

import org.apache.commons.io.FileUtils;
import org.commonjava.util.sidecar.services.ArchiveRetrieveService;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.io.File;
import java.io.IOException;

import static org.commonjava.util.sidecar.services.PreSeedConstants.DEFAULT_REPO_PATH;
import static org.commonjava.util.sidecar.util.TestUtil.SIZE_50K;
import static org.commonjava.util.sidecar.util.TestUtil.getBytes;

@ApplicationScoped
@Alternative
public class MockArchiveRetrieveService
                extends ArchiveRetrieveService
{
    @PostConstruct
    public void init()
    {
        try
        {
            File tracked = new File( DEFAULT_REPO_PATH, "1000" );
            FileUtils.write( tracked, new String( getBytes( SIZE_50K ) ), "UTF-8" );

            String mavenPath = "/org/apache/maven/maven-core/3.0/maven-core-3.0.jar";
            File jar = new File( DEFAULT_REPO_PATH + mavenPath );
            new File( jar.getParent() ).mkdirs();
            FileUtils.write( jar, new String( getBytes( SIZE_50K ) ), "UTF-8" );

            String mavenMetaPath = "/org/apache/maven/maven-core/3.0/maven-metadata.xml";
            File mavenMetadata = new File( DEFAULT_REPO_PATH + mavenMetaPath );
            new File( mavenMetadata.getParent() ).mkdirs();
            FileUtils.write( mavenMetadata, new String( getBytes( SIZE_50K ) ), "UTF-8" );

            String npmPath = "/@babel/code-frame/-/code-frame-7.tgz";
            File tgz = new File( DEFAULT_REPO_PATH + npmPath );
            new File( tgz.getParent() ).mkdirs();
            FileUtils.write( tgz, new String( getBytes( SIZE_50K ) ), "UTF-8" );

            String npmMetaPath = "/@babel/code-frame/package.json";
            File npmMetadata = new File( DEFAULT_REPO_PATH + npmMetaPath );
            new File( npmMetadata.getParent() ).mkdirs();
            FileUtils.write( npmMetadata, new String( getBytes( SIZE_50K ) ), "UTF-8" );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }

    @Override
    public String getBuildConfigId()
    {
        return "1000";
    }
}

/**
 * Copyright (C) 2011-2021 Red Hat, Inc. (https://github.com/Commonjava/indy-sidecar)
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

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.vertx.ConsumeEvent;
import com.google.gson.stream.JsonReader;
import org.commonjava.util.sidecar.model.StoreKey;
import org.commonjava.util.sidecar.model.StoreType;
import org.commonjava.util.sidecar.model.TrackedContent;
import org.commonjava.util.sidecar.model.TrackedContentEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import static org.commonjava.util.sidecar.services.ProxyConstants.ARCHIVE_DECOMPRESS_COMPLETE;
import static org.commonjava.util.sidecar.util.SidecarUtils.getBuildConfigId;

@RegisterForReflection
@ApplicationScoped
public class ReportService
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    public TrackedContent trackedContent;

    @PostConstruct
    void init(){
        this.trackedContent = new TrackedContent();
    }

    public void appendUpload(TrackedContentEntry upload){
        this.trackedContent.appendUpload(upload);
    }

    public void appendDownload(TrackedContentEntry download){
        this.trackedContent.appendDownload(download);
    }


    @ConsumeEvent(value = ARCHIVE_DECOMPRESS_COMPLETE)
    public void readReport(String path) throws FileNotFoundException
    {
        String filePath = path + "/" + getBuildConfigId();
        logger.info( "Loading build content history:" + filePath );
        BufferedReader reader = new BufferedReader( new FileReader( filePath ) );
        load( reader );
    }

    private boolean load( BufferedReader input)
    {
        JsonReader reader = new JsonReader( input);
        try (input)
        {
            reader.beginObject();
            while ( reader.hasNext() )
            {
                String jsonKey = reader.nextName();

                if ( "downloads".equals( jsonKey ) )
                {
                    reader.beginArray();
                    while ( reader.hasNext() )
                    {
                        trackedContent.appendDownload( loadTrackedContentEntry( reader ) );
                    }
                    reader.endArray();
                }
                else
                {
                    reader.skipValue();
                }
            }
            logger.info( "Load complete" );
        }
        catch ( IOException e )
        {
            logger.warn( "Load build content history failed" );
            return false;
        }
        return true;
    }

    public TrackedContentEntry loadTrackedContentEntry(JsonReader reader){
        TrackedContentEntry entry = new TrackedContentEntry();
        try {
            reader.beginObject();
            while (reader.hasNext()) {
                String jsonKey = reader.nextName();

                switch (jsonKey) {
                    case "path":
                        entry.setPath(reader.nextString());
                        break;
                    case "originUrl":
                        try
                        {
                            entry.setOriginUrl( reader.nextString() );
                        }
                        catch ( IllegalStateException e ){
                            reader.skipValue();
                            logger.debug( "originUrl has empty value" );
                        }
                        break;
                    case "localUrl":
                        if ( entry.getOriginUrl().equals( "" ) ){
                            entry.setOriginUrl(reader.nextString());
                        } else {
                            reader.skipValue();
                        }
                        break;
                    case "md5":
                        entry.setMd5(reader.nextString());
                        break;
                    case "sha256":
                        entry.setSha256(reader.nextString());
                        break;
                    case "sha1":
                        entry.setSha1(reader.nextString());
                        break;
                    case "size":
                        entry.setSize(reader.nextLong());
                        break;
                    case "storeKey":
                        reader.beginObject();
                        StoreKey storeKey = new StoreKey();
                        while ( reader.hasNext() ){
                            String key = reader.nextName();
                            switch ( key ){
                                case "packageType":
                                    storeKey.setPackageType( reader.nextString() );
                                    break;
                                case "type":
                                    storeKey.setType( StoreType.valueOf( reader.nextString() ) );
                                    break;
                                case "name":
                                    storeKey.setName( reader.nextString() );
                                    break;
                                default:
                                    reader.skipValue();
                                    break;
                            }
                        }
                        entry.setStoreKey( storeKey );
                        reader.endObject();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
        } catch (IOException e) {
            logger.warn( "load failed for tracking content entry" );
        }
        entry.setAccessChannel( "NATIVE" );
        return entry;
    }


}

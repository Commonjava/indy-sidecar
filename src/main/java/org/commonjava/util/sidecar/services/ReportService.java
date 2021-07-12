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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.vertx.ConsumeEvent;
import org.commonjava.util.sidecar.model.AccessChannel;
import org.commonjava.util.sidecar.model.StoreEffect;
import org.commonjava.util.sidecar.model.TrackedContent;
import org.commonjava.util.sidecar.model.TrackedContentEntry;
import org.commonjava.util.sidecar.model.TrackingKey;
import org.commonjava.util.sidecar.model.dto.HistoricalContentDTO;
import org.commonjava.util.sidecar.model.dto.HistoricalEntryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.commonjava.util.sidecar.services.PreSeedConstants.FOLO_BUILD;
import static org.commonjava.util.sidecar.util.SidecarUtils.getBuildConfigId;

@RegisterForReflection
@ApplicationScoped
public class ReportService
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private TrackedContent trackedContent;

    private HistoricalContentDTO historicalContentDTO;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    void init(){
        this.trackedContent = new TrackedContent();
    }

    public void appendUpload(TrackedContentEntry upload){
        this.trackedContent.appendUpload(upload);
        logger.info(upload.toString());
    }

    public void appendDownload(TrackedContentEntry download){
        this.trackedContent.appendDownload(download);
        logger.info(download.toString());
    }


    @ConsumeEvent(value = FOLO_BUILD)
    public void readReport(String path)
    {
        HistoricalContentDTO content;
        Path filePath = Path.of(  path , "" + getBuildConfigId() );
        logger.info( "Loading build content history:" + filePath );
        try
        {
            String json = Files.readString( filePath );
            this.historicalContentDTO = objectMapper.readValue( json, HistoricalContentDTO.class );
            if ( historicalContentDTO == null ){
                logger.error( "Failed to read historical content which is empty." );
            }
            /*else {
                for ( HistoricalEntryDTO download:content.getDownloads() )
                {
                     this.trackedContent.appendDownload( new TrackedContentEntry( new TrackingKey(getBuildConfigId()),
                                                                                  download.getStoreKey(),
                                                                                  AccessChannel.NATIVE,
                                                                                  download.getOriginUrl(), download.getPath(), StoreEffect.DOWNLOAD, download.getSize(),
                                                                                  download.getMd5(), download.getSha1(), download.getSha256() ));
                }
            }*/
        }
        catch ( IOException e)
        {
            logger.error( "convert file " + filePath + " to object failed" );
        }

    }

}

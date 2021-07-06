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
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
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
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.commonjava.util.sidecar.services.PreSeedConstants.FOLO_BUILD;
import static org.commonjava.util.sidecar.services.PreSeedConstants.STARTUP_INIT;
import static org.commonjava.util.sidecar.util.SidecarUtils.getBuildConfigId;

@ApplicationScoped
public class ReportService
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    public TrackedContent trackedContent;

    private HashMap<String,HistoricalEntryDTO> historicalContentMap;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Classifier classifier;

    @Inject
    ProxyService proxyService;

    @PostConstruct
    void init(){
        this.trackedContent = new TrackedContent();
        this.historicalContentMap = new HashMap<>();
    }

    public void appendUpload(TrackedContentEntry upload){
        this.trackedContent.appendUpload(upload);
        logger.info(upload.toString());
    }

    public void appendDownload(TrackedContentEntry download){
        this.trackedContent.appendDownload(download);
        logger.info(download.toString());
    }

    public TrackedContent getTrackedContent()
    {
        return trackedContent;
    }

    @ConsumeEvent(value = STARTUP_INIT)
    public void loadReport(String path)
    {
        if (getBuildConfigId() != null){
            HistoricalContentDTO content;
            Path filePath = Path.of(  path , File.separator, getBuildConfigId() );
            logger.info( "Loading build content history:" + filePath );
            try
            {
                String json = Files.readString( filePath );
                content = objectMapper.readValue( json, HistoricalContentDTO.class );
                if ( content == null ){
                    logger.warn( "Failed to read historical content which is empty." );
                }
                else {
                    for (HistoricalEntryDTO download:content.getDownloads()){
                        this.historicalContentMap.put(download.getPath(),download);
                    }
                }
            }
            catch ( IOException e)
            {
                logger.error( "convert file " + filePath + " to object failed" );
            }
        }
    }

    @ConsumeEvent(value = FOLO_BUILD)
    public void logFoloDownload(String path)
    {
        HistoricalEntryDTO entryDTO = historicalContentMap.get(path);
        this.trackedContent.appendDownload(new TrackedContentEntry(
                new TrackingKey(getBuildConfigId()),
                entryDTO.getStoreKey(),
                AccessChannel.NATIVE,
                entryDTO.getOriginUrl(), entryDTO.getPath(), StoreEffect.DOWNLOAD, entryDTO.getSize(),
                entryDTO.getMd5(), entryDTO.getSha1(), entryDTO.getSha256() ));
    }

    public Uni<Response> exportReport() throws Exception
    {
        //Change here when we decide indy import API
        String path = "/api/folo/admin/report/import";
        return classifier.classifyAnd( path, HttpMethod.PUT, ( client, service ) ->
                        proxyService.wrapAsyncCall(
                                        client.put( path ).sendJsonObject( exportReportJson() ), null ));
    }

    public JsonObject exportReportJson()
    {
        return JsonObject.mapFrom( trackedContent );
    }

}

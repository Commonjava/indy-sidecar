package org.commonjava.util.sidecar.model.dto;

public class HistoricalContentDTO
{
    private String buildConfigId;

    private HistoricalEntryDTO[] downloads;

    public HistoricalContentDTO()
    {
    }

    public HistoricalContentDTO( String buildConfigId, HistoricalEntryDTO[] downloads ) {
        this.buildConfigId = buildConfigId;
        this.downloads = downloads;
    }

    public String getBuildConfigId() {
        return buildConfigId;
    }

    public void setBuildConfigId( String buildConfigId ) {
        this.buildConfigId = buildConfigId;
    }

    public HistoricalEntryDTO[] getDownloads()
    {
        return downloads;
    }

    public void setDownloads( final HistoricalEntryDTO[] downloads )
    {
        this.downloads = downloads;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        String content = String.format( "HistoricalContentDTO [\n  buildConfigId=%s\n]\n", buildConfigId );
        builder.append( content );
        for ( HistoricalEntryDTO entry : downloads )
        {
            builder.append( entry.toString() );
            builder.append( "\n" );
        }
        return builder.toString();
    }
}


package org.commonjava.util.sidecar.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class TrackingKey
                implements Externalizable
{

    private String id;

    public TrackingKey()
    {
        id = "";
    }

    public TrackingKey( final String id )
    {
        setId( id );
    }

    public String getId()
    {
        return id;
    }

    protected void setId( final String id )
    {
        if ( id == null )
        {
            throw new NullPointerException( "tracking id cannot be null." );
        }

        this.id = id;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( id == null ) ? 0 : id.hashCode() );
        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null )
        {
            return false;
        }
        if ( getClass() != obj.getClass() )
        {
            return false;
        }
        final TrackingKey other = (TrackingKey) obj;
        if ( id == null )
        {
            return other.id == null;
        }
        else
            return id.equals( other.id );
    }

    @Override
    public String toString()
    {
        return String.format( "TrackingKey [%s]", id );
    }

    @Override
    public void writeExternal( final ObjectOutput out ) throws IOException
    {
        out.writeObject( id );
    }

    @Override
    public void readExternal( final ObjectInput in ) throws IOException, ClassNotFoundException
    {
        id = (String) in.readObject();
    }
}

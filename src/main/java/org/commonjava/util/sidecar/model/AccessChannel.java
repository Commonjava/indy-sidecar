package org.commonjava.util.sidecar.model;

/**
 * Enumeration to distinguish between different access channels to stores.
 *
 * @author pkocandr
 */
public enum AccessChannel
{

    /** Used when the store is accessed via httprox addon. */
    GENERIC_PROXY,

    /** Used to signify content coming from normal repositories and groups. */
    NATIVE,

    /** Used when the store is accessed via regular Maven repo.
     *  NOTE: This has been changed to {@link #NATIVE} in our tracking code. It is included for historical purposes. */
    @Deprecated
    MAVEN_REPO;

}
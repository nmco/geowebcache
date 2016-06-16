package org.geowebcache.service.wmts;

import org.geowebcache.config.meta.ServiceInformation;
import org.geowebcache.io.XMLBuilder;

import java.io.IOException;

public interface WMTSExtension {

    String[] getSchemaLocations();

    void registerNamespaces(XMLBuilder xmlBuilder) throws IOException;

    void encodedMetadata(XMLBuilder xmlBuilder) throws IOException;

    ServiceInformation getServiceInformation();
}

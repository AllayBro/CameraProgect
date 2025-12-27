package org.example.camera.catalog.soap;

import javax.xml.ws.Endpoint;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CxfSoapConfig {

    @Bean
    public Endpoint catalogSoapEndpoint(Bus bus, CatalogSoapApiImpl impl) {
        EndpointImpl endpoint = new EndpointImpl(bus, impl);
        endpoint.publish("/catalog");   // ВАЖНО: строка в кавычках
        return endpoint;
    }
}

package org.example.camera.analytics.soap;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.xml.ws.Endpoint;

@Configuration
public class CxfSoapConfig {

    @Bean
    public Endpoint analyticsEndpoint(Bus bus, AnalyticsSoapApiImpl impl) {
        EndpointImpl endpoint = new EndpointImpl(bus, impl);
        endpoint.publish("/analytics");
        return endpoint;
    }
}

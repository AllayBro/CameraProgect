package org.example.camera.penalty.soap;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.xml.ws.Endpoint;


@Configuration
public class CxfSoapConfig {
    @Bean
    public Endpoint penaltyEndpoint(Bus bus, PenaltySoapApiImpl impl) {
        EndpointImpl endpoint = new EndpointImpl(bus, impl);
        endpoint.publish("/penalty");
        return endpoint;
    }
}

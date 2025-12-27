package org.example.camera.penalty.soap;

import org.springframework.stereotype.Service;

import javax.jws.WebService;

@Service
@WebService(
        serviceName = "PenaltySoapService",
        portName = "PenaltySoapPort",
        endpointInterface = "org.example.camera.penalty.soap.PenaltySoapApi",
        targetNamespace = "http://camera.example.org/penalty"
)
public class PenaltySoapApiImpl implements PenaltySoapApi {
    @Override
    public String checkSpeed(String recordId, double speed) {
        return speed > 60.0 ? "SPEEDING for " + recordId : "OK for " + recordId;
    }
}

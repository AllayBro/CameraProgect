package org.example.camera.penalty.soap;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

@WebService(name = "PenaltySoapApi", targetNamespace = "http://camera.example.org/penalty")
public interface PenaltySoapApi {

    @WebMethod
    @WebResult(name = "result")
    String checkSpeed(
            @WebParam(name = "recordId") String recordId,
            @WebParam(name = "speed") double speed
    );
}

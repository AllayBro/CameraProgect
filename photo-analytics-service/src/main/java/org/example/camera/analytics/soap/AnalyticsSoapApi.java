package org.example.camera.analytics.soap;

import jakarta.jws.WebService;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;


@WebService(name = "AnalyticsSoapApi", targetNamespace = "http://camera.example.org/analytics")
public interface AnalyticsSoapApi {

    @WebMethod
    @WebResult(name = "sessionId")
    String startSession(
            @WebParam(name = "droneId") String droneId,
            @WebParam(name = "operatorId") String operatorId
    );

    @WebMethod
    @WebResult(name = "status")
    String getSessionStatus(@WebParam(name = "sessionId") String sessionId);
}

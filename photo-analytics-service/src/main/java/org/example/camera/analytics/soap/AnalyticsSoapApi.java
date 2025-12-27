package org.example.camera.analytics.soap;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

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

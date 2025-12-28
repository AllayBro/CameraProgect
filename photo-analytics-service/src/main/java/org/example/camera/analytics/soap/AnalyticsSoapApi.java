package org.example.camera.analytics.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import org.example.camera.common.dto.StartSessionRequestDto;

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

    // “классика”: запрос — SOAP XML, внутри complex type с drone + operatorId
    @WebMethod
    @WebResult(name = "sessionId")
    String startSessionEx(@WebParam(name = "request") StartSessionRequestDto request);
}

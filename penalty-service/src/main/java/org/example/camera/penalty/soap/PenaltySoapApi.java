package org.example.camera.penalty.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import org.example.camera.common.dto.*;

@WebService(name = "PenaltySoapApi", targetNamespace = "http://camera.example.org/penalty")
public interface PenaltySoapApi {

    @WebMethod
    @WebResult(name = "response")
    PenaltyCheckResponseDto runCheck(@WebParam(name = "request") PenaltyCheckRequestDto request);

    @WebMethod
    @WebResult(name = "response")
    PenaltyCheckResponseDto getCheckStatus(@WebParam(name = "recordId") String recordId);

    @WebMethod
    @WebResult(name = "violation")
    ViolationDto getViolation(@WebParam(name = "recordId") String recordId);

    @WebMethod
    @WebResult(name = "response")
    PenaltyCheckResponseDto submitReview(@WebParam(name = "request") PenaltyReviewRequestDto request);
}

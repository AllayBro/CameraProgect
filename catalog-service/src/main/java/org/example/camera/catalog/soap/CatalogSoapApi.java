package org.example.camera.catalog.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import org.example.camera.common.dto.CatalogImportRequestDto;
import org.example.camera.common.dto.CatalogImportResponseDto;

@WebService(
        targetNamespace = "http://camera.example.org/catalog",
        name = "CatalogSoapApi"
)
public interface CatalogSoapApi {

    @WebMethod
    @WebResult(name = "importResponse")
    CatalogImportResponseDto importCatalog(@WebParam(name = "request") CatalogImportRequestDto request);

    // “получение записи” — по классике отдаём XML (DOM можно показать)
    @WebMethod
    @WebResult(name = "recordXml")
    String getRecordXml(@WebParam(name = "recordId") String recordId);

    // rules.xml: импорт/экспорт/поиск (XPath)
    @WebMethod
    @WebResult(name = "result")
    String importRules(@WebParam(name = "rulesXml") byte[] rulesXml);

    @WebMethod
    @WebResult(name = "rulesXml")
    byte[] exportRules();

    @WebMethod
    @WebResult(name = "values")
    String[] searchRules(@WebParam(name = "xpath") String xpath);
}

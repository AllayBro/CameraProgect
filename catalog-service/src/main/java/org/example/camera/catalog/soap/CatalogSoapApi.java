package org.example.camera.catalog.soap;

import javax.jws.WebMethod;
import javax.jws.WebService;

import org.example.camera.common.dto.CatalogImportRequestDto;
import org.example.camera.common.dto.CatalogImportResponseDto;

@WebService(
        targetNamespace = "http://camera.example.org/catalog",
        name = "CatalogSoapApi"
)
public interface CatalogSoapApi {

    @WebMethod
    CatalogImportResponseDto importCatalog(CatalogImportRequestDto request);
}

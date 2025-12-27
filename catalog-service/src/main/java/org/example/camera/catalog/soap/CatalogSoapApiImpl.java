package org.example.camera.catalog.soap;

import javax.jws.WebService;

import org.example.camera.common.dto.CatalogImportRequestDto;
import org.example.camera.common.dto.CatalogImportResponseDto;
import org.springframework.stereotype.Service;

@Service
@WebService(
        endpointInterface = "org.example.camera.catalog.soap.CatalogSoapApi",
        targetNamespace = "http://camera.example.org/catalog",
        serviceName = "CatalogSoapService",
        portName = "CatalogSoapPort"
)
public class CatalogSoapApiImpl implements CatalogSoapApi {

    @Override
    public CatalogImportResponseDto importCatalog(CatalogImportRequestDto request) {
        int count = (request.manifest != null && request.manifest.photos != null)
                ? request.manifest.photos.size()
                : 0;

        CatalogImportResponseDto resp = new CatalogImportResponseDto();
        resp.status = "IMPORTED";
        resp.photosImported = count;
        resp.sessionId = request.sessionId;
        return resp;
    }
}

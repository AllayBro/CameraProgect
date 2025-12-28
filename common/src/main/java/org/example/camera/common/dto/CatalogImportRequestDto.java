package org.example.camera.common.dto;

import java.util.ArrayList;
import java.util.List;

public class CatalogImportRequestDto {

    public String sessionId;

    // основной блок импорта
    public ManifestDto manifest;

    // опционально: результаты аналитики, привязка по fileKey
    public List<AnalysisResultDto> analysisResults = new ArrayList<>();

    // оставлено, чтобы не сломать текущий код сервисов (уберём после миграции на manifest.*)
    public String droneId;
    public String operatorId;
}

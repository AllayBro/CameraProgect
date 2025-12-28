package org.example.camera.common.dto;

public class AnalysisResultDto {

    // ключ привязки к конкретному фото (берём fileKey, чтобы не плодить recordId в manifest)
    public String fileKey;

    // поля результата анализа (из схемы)
    public String objectType;
    public double confidence;
    public double speed;
    public double distance;
    public String modelVersion;
}

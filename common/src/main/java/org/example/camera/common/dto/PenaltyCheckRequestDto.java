package org.example.camera.common.dto;

public class PenaltyCheckRequestDto {

    public String recordId;

    // единицы: км/ч (см. примечание внизу про AnalyticsService)
    public double speed;

    // "lat,lon"
    public String location;

    // ISO-8601 (как приходит из manifest)
    public String time;

    // 0..1
    public double confidence;

    // дополнительные поля (чтобы не восстанавливать из recordId)
    public String droneId;
    public String operatorId;
    public String fileKey;
}

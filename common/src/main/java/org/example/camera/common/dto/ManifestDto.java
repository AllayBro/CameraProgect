package org.example.camera.common.dto;

import java.util.ArrayList;
import java.util.List;

public class ManifestDto {

    // session-level (из схемы)
    public String droneId;
    public String operatorId;
    public String startTime;       // ISO-8601 строка
    public String endTime;         // ISO-8601 строка
    public String packageChecksum; // если появится в manifest/расчёте

    // photo-level
    public List<PhotoDto> photos = new ArrayList<>();
}

package org.example.camera.common.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class StartSessionRequestDto {

    @XmlElement(required = true)
    public DroneDto drone;

    @XmlElement(required = true)
    public String operatorId;
}

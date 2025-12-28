package org.example.camera.common.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.FIELD)
public class DroneDto {

    @XmlElement(required = true)
    public String droneId;

    public String model;
    public String serialNumber;
    public String owner;
}

package com.calmasalud.hubi.core.model;

public class Supply {
    private String code;
    private String name;
    private String tipoFilamento;
    private String colorFilamento;
    private double cantidadDisponible; // En gramos
    private double umbralAlerta; // (Para RF7)

    // Constructores, Getters y Setters
    public Supply(String code, String name) {
        this.code = code;
        this.name = name;
    }
    public Supply(String colorFilamento) {
        this.colorFilamento = colorFilamento;
    }

    public String getColorFilamento() {
        return colorFilamento;
    }
}
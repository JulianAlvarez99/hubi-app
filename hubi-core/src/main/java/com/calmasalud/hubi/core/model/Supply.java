package com.calmasalud.hubi.core.model;

public class Supply {
    private long id; // Agregado para la persistencia en DB (Cumple con ISupplyRepository)
    private String code;
    private String name;
    private String tipoFilamento;
    private String colorFilamento;
    private double cantidadDisponible; // En gramos
    private double umbralAlerta; // (Para RF7)

    // Constructor por defecto (Buena práctica para Java Beans)
    public Supply() {
    }

    // Constructores existentes
    public Supply(String code, String name) {
        this.code = code;
        this.name = name;
    }
    public Supply(String colorFilamento) {
        this.colorFilamento = colorFilamento;
    }

    // Constructor completo (útil para mapeo desde DB)
    public Supply(long id, String code, String name, String tipoFilamento, String colorFilamento, double cantidadDisponible, double umbralAlerta) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.tipoFilamento = tipoFilamento;
        this.colorFilamento = colorFilamento;
        this.cantidadDisponible = cantidadDisponible;
        this.umbralAlerta = umbralAlerta;
    }

    // Getters y Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTipoFilamento() {
        return tipoFilamento;
    }

    public void setTipoFilamento(String tipoFilamento) {
        this.tipoFilamento = tipoFilamento;
    }

    public String getColorFilamento() {
        return colorFilamento;
    }

    public void setColorFilamento(String colorFilamento) {
        this.colorFilamento = colorFilamento;
    }

    public double getCantidadDisponible() {
        return cantidadDisponible;
    }

    public void setCantidadDisponible(double cantidadDisponible) {
        this.cantidadDisponible = cantidadDisponible;
    }

    public double getUmbralAlerta() {
        return umbralAlerta;
    }

    public void setUmbralAlerta(double umbralAlerta) {
        this.umbralAlerta = umbralAlerta;
    }
}
package com.calmasalud.hubi.core.model;

public class Product {
    private String code;        // Código único (RF8)
    private String name;        // Nombre
    private String fileExtension;

    // Más atributos (id, descripción, etc.)

    public Product(String code, String name,String fileExtension) {
        this.code = code;
        this.name = name;
        this.fileExtension = fileExtension;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
    public String getFileExtension() {
        return fileExtension;
    }
}
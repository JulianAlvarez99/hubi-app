package com.calmasalud.hubi.core.model;

public class Product {
    private String code;
    private String name;
    private String fileExtension;
    private double weightGrams; // Peso Total
    private String usageDetail; // 🚨 NUEVO: Detalle de pesos (ej: "10.5;2.3")

    public Product(String code, String name, String fileExtension) {
        this.code = code;
        this.name = name;
        this.fileExtension = fileExtension;
        this.weightGrams = 0.0;
        this.usageDetail = "";
    }

    // Constructor completo actualizado
    public Product(String code, String name, String fileExtension, double weightGrams, String usageDetail) {
        this.code = code;
        this.name = name;
        this.fileExtension = fileExtension;
        this.weightGrams = weightGrams;
        this.usageDetail = (usageDetail != null) ? usageDetail : "";
    }

    // Getters y Setters
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getFileExtension() { return fileExtension; }
    public double getWeightGrams() { return weightGrams; }
    public void setWeightGrams(double weightGrams) { this.weightGrams = weightGrams; }

    public String getUsageDetail() { return usageDetail; }
    public void setUsageDetail(String usageDetail) { this.usageDetail = usageDetail; }
}
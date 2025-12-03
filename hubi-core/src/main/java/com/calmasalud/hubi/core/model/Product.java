package com.calmasalud.hubi.core.model;

public class Product {
    private String code;
    private String name;
    private String fileExtension;
    private double weightGrams;
    private String usageDetail;
    // --- NUEVO CAMPO ---
    private double cost; // Costo unitario guardado

    public Product(String code, String name, String fileExtension, double cost) {
        this(code, name, fileExtension, 0.0, "", 0.0);
    }

    // Constructor completo actualizado
    public Product(String code, String name, String fileExtension, double weightGrams, String usageDetail, double cost) {
        this.code = code;
        this.name = name;
        this.fileExtension = fileExtension;
        this.weightGrams = weightGrams;
        this.usageDetail = (usageDetail != null) ? usageDetail : "";
        this.cost = cost;
    }

    // Constructor de compatibilidad
    public Product(String code, String name, String fileExtension, double weightGrams, String usageDetail) {
        this(code, name, fileExtension, weightGrams, usageDetail, 0.0);
    }

    // Getters y Setters
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getFileExtension() { return fileExtension; }
    public double getWeightGrams() { return weightGrams; }
    public void setWeightGrams(double weightGrams) { this.weightGrams = weightGrams; }
    public String getUsageDetail() { return usageDetail; }
    public void setUsageDetail(String usageDetail) { this.usageDetail = usageDetail; }

    // --- Getter y Setter para Costo ---
    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }
}
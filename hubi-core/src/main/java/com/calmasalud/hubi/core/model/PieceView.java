package com.calmasalud.hubi.core.model;

/**
 * Representa una Pieza Específica (Archivo G-code/STL/3MF) para la vista de Inventario.
 * Usada como nodo hijo de MasterProductView.
 */
public class PieceView {
    private final String pieceCode;       // SOPROJ001
    private final String originalName;    // Nombre original del archivo (Ej: base_llave.gcode)
    private final String extension;
    // Agregamos campos relevantes para la gestión de stock/producción de piezas (futuro)
    private final boolean isPrinted;
    private final double filamentConsumedG;

    public PieceView(String pieceCode, String originalName, String extension, boolean isPrinted, double filamentConsumedG) {
        this.pieceCode = pieceCode;
        this.originalName = originalName;
        this.extension = extension;
        this.isPrinted = isPrinted;
        this.filamentConsumedG = filamentConsumedG;
    }

    // --- Getters ---
    public String getPieceCode() {
        return pieceCode;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isPrinted() {
        return isPrinted;
    }

    public double getFilamentConsumedG() {
        return filamentConsumedG;
    }
}

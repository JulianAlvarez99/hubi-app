package com.calmasalud.hubi.core.model;

/**
 * DTO para la vista de Inventario. Representa el stock real de una pieza
 * para un color específico (una fila de la tabla 'piece_stock').
 */
public class PieceStockColorView {

    private final String pieceNameBase; // Ej: Llave_base
    private final String colorName;     // Ej: ROJO PLA
    private final int quantityAvailable; // Stock disponible para este color

    public PieceStockColorView(String pieceNameBase, String colorName, int quantityAvailable) {
        this.pieceNameBase = pieceNameBase;
        this.colorName = colorName;
        this.quantityAvailable = quantityAvailable;
    }

    // Getters necesarios para la persistencia y la interfaz
    public String getPieceNameBase() {
        return pieceNameBase;
    }

    public String getColorName() {
        return colorName;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }
}

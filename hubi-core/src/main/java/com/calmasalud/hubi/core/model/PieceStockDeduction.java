package com.calmasalud.hubi.core.model;

/**
 * Representa una deducción de stock de una pieza específica (base name + color)
 * utilizada para descontar del inventario de piezas al eliminar un producto.
 */
public class PieceStockDeduction {
    public final String pieceNameBase;
    public final String color;
    public final int quantity; // Cantidad a descontar

    public PieceStockDeduction(String pieceNameBase, String color, int quantity) {
        this.pieceNameBase = pieceNameBase;
        this.color = color;
        this.quantity = quantity;
    }

    public String getPieceNameBase() { return pieceNameBase; }
    public String getColor() { return color; }
    public int getQuantity() { return quantity; }
}
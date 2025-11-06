package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField; // Para la cantidad
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Controlador para el modal de eliminación/descuento de stock de piezas.
 * Permite al usuario descontar una cantidad específica de una pieza y un color dado.
 */
public class RemovePieceStockController {

    // --- DEPENDENCIAS ---
    // Instancia directa del repositorio para la persistencia del stock de piezas
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    // --- COLORES DUMMY (Ajustar cuando se implemente la tabla de insumos) ---
    private static final List<String> DUMMY_COLORS = Arrays.asList("ROJO PLA", "AZUL ABS", "NEGRO PETG", "BLANCO PLA");

    @FXML private Label lblPieceCode;
    @FXML private TextField txtCantidad; // Campo para la cantidad a ELIMINAR
    @FXML private ComboBox<String> cmbColor1;
    @FXML private ComboBox<String> cmbColor2;
    @FXML private ComboBox<String> cmbColor3;
    @FXML private ComboBox<String> cmbColor4;

    private String pieceCode;
    private String pieceName; // Nombre original de la pieza (Ej: Llave Olla x 8 V3_PLA_1h59m.gcode)
    private boolean removalConfirmed = false;

    // Campos a devolver tras la confirmación
    private int quantityToRemove = 0;
    private String selectedColor = null;


    /**
     * Establece los datos iniciales de la pieza seleccionada.
     */
    public void setPieceData(String pieceCode, String pieceName) {
        this.pieceCode = pieceCode;
        this.pieceName = pieceName;
        lblPieceCode.setText("Pieza: " + pieceCode + " (" + pieceName + ")");
        if (txtCantidad != null) {
            txtCantidad.setText("1"); // Valor por defecto
        }
    }

    /**
     * Devuelve true si el usuario confirmó la eliminación.
     */
    public boolean isRemovalConfirmed() {
        return removalConfirmed;
    }

    // Getters para que el controlador padre obtenga los valores
    public int getQuantityToRemove() { return quantityToRemove; }
    public String getSelectedColor() { return selectedColor; }

    @FXML
    public void initialize() {
        // Inicializar los ComboBox con colores disponibles (incluyendo opción vacía)
        ObservableList<String> colors = FXCollections.observableArrayList(DUMMY_COLORS);

        ObservableList<String> colorsWithNone = FXCollections.observableArrayList("");
        colorsWithNone.addAll(DUMMY_COLORS);

        cmbColor1.setItems(colors); // Color 1 no incluye la opción vacía para forzar selección

        // El FXML asegura que estos ComboBoxes existen
        if (cmbColor2 != null) cmbColor2.setItems(colorsWithNone);
        if (cmbColor3 != null) cmbColor3.setItems(colorsWithNone);
        if (cmbColor4 != null) cmbColor4.setItems(colorsWithNone);
    }

    /**
     * Maneja el clic en el botón "ELIMINAR PRODUCCIÓN".
     */
    @FXML
    private void handleConfirmRemoval() {
        // ... (Validación de Color 1 y cantidad) ...

        int quantity;
        try {
            // 1. Leer y validar cantidad
            quantity = Integer.parseInt(txtCantidad.getText());
            // ... (Validación de cantidad > 0) ...

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Validación", "Por favor, ingrese un número entero válido para la cantidad.");
            return;
        }

        if (cmbColor1.getValue() == null || cmbColor1.getValue().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validación", "Debe seleccionar el Color 1 del stock a eliminar.");
            return;
        }

        try {
            // 2. OBTENER DATOS Y LLAMAR AL REPOSITORIO
            String pieceNameBase = this.pieceName.substring(0, this.pieceName.lastIndexOf('.'));
            String colorUsed = cmbColor1.getValue();

            // Llama al método que verifica el stock antes de restar
            productRepository.decreasePieceStockQuantity(pieceNameBase, colorUsed, quantity);

            // 3. Si la persistencia fue exitosa, confirmamos el resultado
            this.quantityToRemove = quantity;
            this.selectedColor = colorUsed;
            this.removalConfirmed = true;
            showAlert(Alert.AlertType.INFORMATION, "Éxito", "Descuento de " + quantity + " unidades de " + pieceNameBase + " (" + colorUsed + ") confirmado.");
            closeStage();

        } catch (RuntimeException e) {
            // Captura el error de 'Stock insuficiente' de la capa de persistencia (Lógica RF4)
            showAlert(Alert.AlertType.ERROR, "Error de Stock", e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        this.removalConfirmed = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) lblPieceCode.getScene().getWindow();
        stage.close();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }
}
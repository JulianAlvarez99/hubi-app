package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite; // Necesaria para instanciar el repositorio
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField; // Asumo que se añadió
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddPieceStockController {

    // --- DEPENDENCIAS ---
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    // --- COLORES DUMMY (Máximo 4) ---
    private static final List<String> DUMMY_COLORS = Arrays.asList("ROJO PLA", "AZUL ABS", "NEGRO PETG", "BLANCO PLA");

    @FXML private Label lblPieceCode;
    @FXML private TextField txtCantidad; // Asumido desde el FXML
    @FXML private ComboBox<String> cmbColor1;
    @FXML private ComboBox<String> cmbColor2;
    @FXML private ComboBox<String> cmbColor3;
    @FXML private ComboBox<String> cmbColor4;

    private String pieceCode;
    private String pieceName; // Contiene el nombre original (Ej: Llave Olla x 8 V3_PLA_1h59m)
    private boolean productionRegistered = false;

    public void setPieceData(String pieceCode, String pieceName) {
        this.pieceCode = pieceCode;
        this.pieceName = pieceName;
        lblPieceCode.setText("Pieza: " + pieceCode + " (" + pieceName + ")");
        // Inicializar cantidad por defecto a 1
        if (txtCantidad != null) {
            txtCantidad.setText("1");
        }
    }

    public boolean isProductionRegistered() {
        return productionRegistered;
    }

    // Método para obtener los colores seleccionados
    public List<String> getSelectedColors() {
        List<String> colors = new ArrayList<>();
        if (cmbColor1.getValue() != null && !cmbColor1.getValue().isEmpty()) colors.add(cmbColor1.getValue());
        if (cmbColor2.getValue() != null && !cmbColor2.getValue().isEmpty()) colors.add(cmbColor2.getValue());
        if (cmbColor3.getValue() != null && !cmbColor3.getValue().isEmpty()) colors.add(cmbColor3.getValue());
        if (cmbColor4.getValue() != null && !cmbColor4.getValue().isEmpty()) colors.add(cmbColor4.getValue());
        return colors;
    }

    @FXML
    public void initialize() {
        ObservableList<String> colors = FXCollections.observableArrayList(DUMMY_COLORS);

        // Las casillas opcionales deben incluir la opción vacía para multi-color
        ObservableList<String> colorsWithNone = FXCollections.observableArrayList("");
        colorsWithNone.addAll(DUMMY_COLORS);

        cmbColor1.setItems(colors);
        if (cmbColor2 != null) cmbColor2.setItems(colorsWithNone);
        if (cmbColor3 != null) cmbColor3.setItems(colorsWithNone);
        if (cmbColor4 != null) cmbColor4.setItems(colorsWithNone);
    }

    @FXML
    private void handleRegister() {
        if (cmbColor1.getValue() == null || cmbColor1.getValue().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validación", "Debe seleccionar el Color 1.");
            return;
        }

        int quantity;
        try {
            // Leer la cantidad del TextField
            quantity = Integer.parseInt(txtCantidad.getText());
            if (quantity <= 0) {
                showAlert(Alert.AlertType.ERROR, "Validación", "La cantidad debe ser mayor a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Validación", "Por favor, ingrese un número entero válido para la cantidad.");
            return;
        }

        try {
            // LÓGICA DE PERSISTENCIA:
            // 1. Obtener el nombre base de la pieza (Ej: Llave Olla x 8 V3_PLA_1h59m)
            String pieceNameBase = this.pieceName.substring(0, this.pieceName.lastIndexOf('.'));
            String colorUsed = cmbColor1.getValue();

            // 2. AUMENTAR STOCK REAL de la pieza (en la tabla piece_stock)
            productRepository.increasePieceStockQuantity(pieceNameBase, quantity);

            // 3. LA CANTIDAD DE PIEZAS DISPONIBLES POR COLOR (LÓGICA FUTURA)
            // Nota: La información de color (cmbColor1) se almacenaría en una tabla de
            // Producción/Log para el descuento de insumos (RF9), que aún no está implementada.

            this.productionRegistered = true;
            showAlert(Alert.AlertType.INFORMATION, "Registro Exitoso",
                    quantity + " unidades de '" + pieceNameBase + "' registradas.");
            closeStage();

        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Error de Persistencia", "Fallo al guardar el stock de la pieza: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        this.productionRegistered = false;
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
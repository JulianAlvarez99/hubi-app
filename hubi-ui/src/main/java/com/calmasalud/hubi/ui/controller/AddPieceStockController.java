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

    // Dentro de AddPieceStockController.java

// NOTA: El campo txtCantidad debe existir en el FXML.

    @FXML
    private void handleRegister() {
        if (cmbColor1.getValue() == null || cmbColor1.getValue().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validación", "Debe seleccionar el Color 1 (principal).");
            return;
        }

        int quantity;
        try {
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
            // LÓGICA CLAVE: Obtener nombre base y COLOR
            String pieceNameBase = this.pieceName.substring(0, this.pieceName.lastIndexOf('.'));
            String colorUsed = cmbColor1.getValue();

            // LLAMADA AL REPOSITORIO CON EL COLOR Y LA CANTIDAD
            // (Esto resuelve la falla de lógica y el problema de las 6 piezas totales.)
            productRepository.increasePieceStockQuantity(pieceNameBase, colorUsed, quantity);

            // NOTA: Se ignora cmbColor2, 3, 4 por ahora, ya que la lógica de multi-color es para el descuento (RF9).

            this.productionRegistered = true;
            showAlert(Alert.AlertType.INFORMATION, "Registro Exitoso",
                    quantity + " unidades de '" + pieceNameBase + "' (Color: " + colorUsed + ") registradas.");
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
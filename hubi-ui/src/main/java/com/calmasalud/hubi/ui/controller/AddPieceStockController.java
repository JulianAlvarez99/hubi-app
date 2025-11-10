package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// NOTA: Se asume que la utilidad showAlert está delegada correctamente a UISettings.

public class AddPieceStockController {

    // --- DEPENDENCIAS ---
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    // --- COLORES DUMMY (Máximo 4) ---
    private static final List<String> DUMMY_COLORS = Arrays.asList("ROJO PLA", "AZUL ABS", "NEGRO PETG", "BLANCO PLA");

    @FXML private Label lblPieceCode;
    @FXML private TextField txtCantidad;
    @FXML private ComboBox<String> cmbColor1;
    @FXML private ComboBox<String> cmbColor2;
    @FXML private ComboBox<String> cmbColor3;
    @FXML private ComboBox<String> cmbColor4;

    private String pieceCode;
    private String pieceName;
    private boolean productionRegistered = false;

    public void setPieceData(String pieceCode, String pieceName) {
        this.pieceCode = pieceCode;
        this.pieceName = pieceName;
        lblPieceCode.setText("Pieza: " + pieceCode + " (" + pieceName + ")");
        if (txtCantidad != null) {
            txtCantidad.setText("1");
        }
    }

    public boolean isProductionRegistered() {
        return productionRegistered;
    }

    /**
     * Recolecta todos los colores seleccionados (no nulos ni vacíos).
     */
    public List<String> getSelectedColors() {
        // Obtenemos todos los valores, incluso si son nulos o vacíos.
        List<String> allValues = new ArrayList<>();
        // Nos aseguramos de que los ComboBoxes opcionales se añadan a la lista.
        if (cmbColor1 != null) allValues.add(cmbColor1.getValue());
        if (cmbColor2 != null) allValues.add(cmbColor2.getValue());
        if (cmbColor3 != null) allValues.add(cmbColor3.getValue());
        if (cmbColor4 != null) allValues.add(cmbColor4.getValue());

        // Filtramos, quitando nulos y vacíos, y eliminando duplicados si el usuario eligió el mismo color dos veces.
        return allValues.stream()
                .filter(color -> color != null && !color.trim().isEmpty())
                .map(String::trim)
                .distinct() // Asegura que "ROJO PLA" solo aparezca una vez
                .collect(Collectors.toList());
    }

    @FXML
    public void initialize() {
        ObservableList<String> colors = FXCollections.observableArrayList(DUMMY_COLORS);

        ObservableList<String> colorsWithNone = FXCollections.observableArrayList("");
        colorsWithNone.addAll(DUMMY_COLORS);

        cmbColor1.setItems(colors);
        // cmbColor1 debe requerir selección (o por defecto la primera opción)
        cmbColor1.getSelectionModel().selectFirst();

        if (cmbColor2 != null) cmbColor2.setItems(colorsWithNone);
        if (cmbColor3 != null) cmbColor3.setItems(colorsWithNone);
        if (cmbColor4 != null) cmbColor4.setItems(colorsWithNone);
    }

    /**
     * Genera una clave única a partir de una lista de colores seleccionados.
     */
    private String generateColorCombinationKey(List<String> selectedColors) {
        // La clave de combinación para un solo color es el nombre del color (ej: "ROJO PLA")
        if (selectedColors.isEmpty()) {
            return "";
        }

        // *** ELIMINACIÓN DE LA LÓGICA DE ORDENAMIENTO ALFABÉTICO ***
        // La clave se forma en el orden en que los ComboBoxes fueron leídos (cmbColor1, cmbColor2, etc.).

        // Nota: Asumimos que la lista 'selectedColors' ya está filtrada y solo contiene valores únicos y no vacíos
        // (Lógica que debe estar en getSelectedColors()).

        return String.join("|", selectedColors); // Ahora la clave será: Color1|Color2
    }


    @FXML
    private void handleRegister() {
        // Se valida la cantidad antes de la lógica de color
        int quantity;
        try {
            quantity = Integer.parseInt(txtCantidad.getText());
            if (quantity <= 0) {
                showAlert(AlertType.ERROR, "Validación", "La cantidad debe ser mayor a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(AlertType.ERROR, "Validación", "Por favor, ingrese un número entero válido para la cantidad.");
            return;
        }

        // 1. Obtener la clave de combinación
        List<String> selectedColors = getSelectedColors();
        String colorCombinationKey = generateColorCombinationKey(selectedColors);
        // *** PUNTO CRÍTICO DE DEBUGGING ***
        System.out.println("--- DEBUG REGISTRO DE STOCK ---");
        System.out.println("Colores Seleccionados (Lista): " + selectedColors);
        System.out.println("Clave de Combinación Final (Key): " + colorCombinationKey);
        // 2. Validación: Asegurar que se seleccionó al menos un color.
        if (colorCombinationKey.isEmpty()) {
            showAlert(AlertType.ERROR, "Validación", "Debe seleccionar al menos el Color 1 (principal).");
            return;
        }

        try {
            // 1. Obtener la clave de combinación
            String pieceNameBase = this.pieceName.substring(0, this.pieceName.lastIndexOf('.'));



            // 2. Validación: Asegurar que se seleccionó al menos un color.
            if (colorCombinationKey.isEmpty()) {
                showAlert(AlertType.ERROR, "Validación", "Debe seleccionar al menos el Color 1 (principal).");
                return;
            }

            // 3. LLAMADA AL REPOSITORIO con la CLAVE DE COMBINACIÓN
            productRepository.increasePieceStockQuantity(pieceNameBase, colorCombinationKey, quantity);

            this.productionRegistered = true;
            showAlert(AlertType.INFORMATION, "Registro Exitoso",
                    quantity + " unidades de '" + pieceNameBase +
                            "' registradas bajo la combinación: " + colorCombinationKey + ".");
            closeStage();

        } catch (RuntimeException e) {
            showAlert(AlertType.ERROR, "Error de Persistencia", "Fallo al guardar el stock: " + e.getMessage());
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

    // Método local de ayuda (asumimos que usa el método estático de UISettings con el header nulo)
    private void showAlert(AlertType type, String title, String content) {
        // La implementación real debe estar en UISettings.java
    }
}
package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.service.CatalogService;
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
import java.util.List;
import java.util.stream.Collectors;

public class AddPieceStockController {

    // --- DEPENDENCIAS ---
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    private CatalogService catalogService; // Se inyecta desde InventarioController

    // --- COLORES DUMMY (Solo como fallback inicial antes de cargar DB) ---
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

    @FXML
    public void initialize() {
        // Inicialización básica por si se abre sin servicio (fallback)
        ObservableList<String> colors = FXCollections.observableArrayList(DUMMY_COLORS);
        cmbColor1.setItems(colors);
        cmbColor1.getSelectionModel().selectFirst();

        // Los opcionales pueden estar vacíos al inicio
        if (cmbColor2 != null) cmbColor2.setItems(FXCollections.observableArrayList(""));
        // ...
    }

    public void setCatalogService(CatalogService service) {
        this.catalogService = service;
        loadColors(); // Cargar colores reales de la BD al recibir el servicio
    }

    /**
     * Carga la lista de insumos disponibles en los ComboBoxes.
     */
    private void loadColors() {
        if (catalogService == null) return;

        List<String> realColors = catalogService.getAvailableFilamentColors();
        ObservableList<String> items = FXCollections.observableArrayList(realColors);

        // Crear lista con opción vacía para los opcionales
        ObservableList<String> itemsWithNone = FXCollections.observableArrayList("");
        itemsWithNone.addAll(realColors);

        // Configurar ComboBox 1 (Obligatorio)
        cmbColor1.setItems(items);
        if (!items.isEmpty()) {
            cmbColor1.getSelectionModel().selectFirst();
        }

        // Configurar ComboBoxes Opcionales
        if (cmbColor2 != null) cmbColor2.setItems(itemsWithNone);
        if (cmbColor3 != null) cmbColor3.setItems(itemsWithNone);
        if (cmbColor4 != null) cmbColor4.setItems(itemsWithNone);
    }

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
        List<String> allValues = new ArrayList<>();
        if (cmbColor1 != null) allValues.add(cmbColor1.getValue());
        if (cmbColor2 != null) allValues.add(cmbColor2.getValue());
        if (cmbColor3 != null) allValues.add(cmbColor3.getValue());
        if (cmbColor4 != null) allValues.add(cmbColor4.getValue());

        return allValues.stream()
                .filter(color -> color != null && !color.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    @FXML
    private void handleRegister() {
        // 1. VALIDACIÓN DE CANTIDAD
        int quantity;
        try {
            quantity = Integer.parseInt(txtCantidad.getText());
            if (quantity <= 0) {
                showAlert(AlertType.ERROR, "Validación", "La cantidad debe ser mayor a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(AlertType.ERROR, "Validación", "Por favor, ingrese un número entero válido.");
            return;
        }

        // 2. VALIDACIÓN DE COLORES
        List<String> selectedColors = getSelectedColors();
        if (selectedColors.isEmpty()) {
            showAlert(AlertType.ERROR, "Validación", "Debe seleccionar al menos el Color 1.");
            return;
        }

        if (catalogService == null) {
            showAlert(AlertType.ERROR, "Error", "Servicio no inicializado.");
            return;
        }

        // 3. PROCESAMIENTO CENTRALIZADO (Llamada única al servicio)
        try {
            // Esta función descuenta el insumo Y aumenta el stock de la pieza
            List<String> reportMessages = catalogService.registerPieceProduction(this.pieceCode, selectedColors, quantity);

            this.productionRegistered = true;

            // Construir mensaje de éxito
            StringBuilder msg = new StringBuilder("Producción registrada con éxito.\n\nDetalle de consumo:\n");
            for (String line : reportMessages) {
                msg.append(line).append("\n");
            }

            showAlert(AlertType.INFORMATION, "Registro Exitoso", msg.toString());
            closeStage();

        } catch (RuntimeException e) {
            // Errores de negocio (Stock insuficiente)
            showAlert(AlertType.ERROR, "Stock Insuficiente", e.getMessage());
        } catch (Exception e) {
            // Otros errores
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Error", "Ocurrió un error: " + e.getMessage());
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

    // Method local de ayuda mejorado para alertas grandes
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);

        // 🚨 MEJORA 1: Permitir redimensionar la ventana manualmente
        alert.setResizable(true);

        // 🚨 MEJORA 2: Forzar que el alto se ajuste al contenido
        // Usamos la constante USE_PREF_SIZE de la clase Region
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        // Opcional: Darle un ancho mínimo para que no quede muy angosto
        alert.getDialogPane().setMinWidth(400);

        alert.showAndWait();
    }
}
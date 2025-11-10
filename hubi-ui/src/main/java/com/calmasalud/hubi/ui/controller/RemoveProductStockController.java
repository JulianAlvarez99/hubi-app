package com.calmasalud.hubi.ui.controller;

import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.PieceStockColorView;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.model.PieceStockDeduction;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.service.CatalogService;
import com.calmasalud.hubi.ui.util.UISettings;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RemoveProductStockController {

    @FXML private Label productNameLabel;
    @FXML private TextField quantityField;
    @FXML private TableView<PieceInstanceRow> deductionTable;
    @FXML private TableColumn<PieceInstanceRow, Integer> colPieceIndex;
    @FXML private TableColumn<PieceInstanceRow, String> colPieceName;
    @FXML private TableColumn<PieceInstanceRow, String> colMasterCode;
    @FXML private TableColumn<PieceInstanceRow, String> colSelectedColor;
    @FXML private TableColumn<PieceInstanceRow, Integer> colStockAvailable;

    // Dependencias Inyectadas
    private CatalogService catalogService;
    private IProductCompositionRepository compositionRepository;
    private IProductRepository productRepository;

    // Datos de la Sesión
    private MasterProduct selectedMasterProduct;
    private ObservableList<PieceInstanceRow> masterDeductionList;

    public void setDependencies(CatalogService catalogService, IProductCompositionRepository compositionRepository, IProductRepository productRepository) {
        this.catalogService = catalogService;
        this.compositionRepository = compositionRepository;
        this.productRepository = productRepository;
    }

    public void initData(MasterProduct masterProduct) {
        this.selectedMasterProduct = masterProduct;
        productNameLabel.setText("Producto Seleccionado: " + masterProduct.getProductName() + " (" + masterProduct.getMasterCode() + ")");

        setupTable();
        loadCompositionData();
        deductionTable.refresh();
    }

    private void setupTable() {
        // --- ENLACES CRÍTICOS (Usando .Property() explícitamente para máxima estabilidad) ---
        colPieceIndex.setCellValueFactory(cellData -> cellData.getValue().indexProperty().asObject());
        colPieceName.setCellValueFactory(cellData -> cellData.getValue().pieceNameBaseProperty());
        colMasterCode.setCellValueFactory(cellData -> cellData.getValue().masterCodeProperty());
        colSelectedColor.setCellValueFactory(cellData -> cellData.getValue().selectedColorProperty());
        // ----------------------------------------------------------------------------------

        colSelectedColor.setCellFactory(column -> new ComboBoxEditingCell());
        colSelectedColor.setOnEditCommit(event -> {
            PieceInstanceRow row = event.getRowValue();
            row.setSelectedColor(event.getNewValue());
            deductionTable.refresh(); // VITAL: Sincroniza la memoria después del commit
        });

        // Columna de Stock Disponible (dinámica, depende del color seleccionado)
        colStockAvailable.setCellValueFactory(data -> {
            PieceInstanceRow row = data.getValue();
            return new SimpleIntegerProperty(
                    row.getStockByColor().getOrDefault(row.getSelectedColor(), 0)
            ).asObject();
        });

        deductionTable.setEditable(true);
        masterDeductionList = FXCollections.observableArrayList();
        deductionTable.setItems(masterDeductionList);
    }

    /**
     * Carga la composición (BOM) del producto maestro y la EXPANDI a piezas individuales.
     */
    private void loadCompositionData() {
        if (selectedMasterProduct == null) return;

        List<ProductComposition> composition = compositionRepository.getComposition(selectedMasterProduct.getMasterCode());
        int instanceIndex = 1;

        for (ProductComposition comp : composition) {
            String pieceNameBase = comp.getPieceNameBase();
            int requiredQuantity = comp.getRequiredQuantity();

            List<PieceStockColorView> stockByColorList = productRepository.getStockByPieceNameBase(pieceNameBase);

            Map<String, Integer> stockByColorMap = stockByColorList.stream()
                    .filter(s -> s.getQuantityAvailable() > 0)
                    .collect(Collectors.toMap(
                            PieceStockColorView::getColorName,
                            PieceStockColorView::getQuantityAvailable,
                            Integer::sum
                    ));

            List<String> availableColors = new ArrayList<>(stockByColorMap.keySet());

            // 3. LÓGICA CLAVE: EXPANDIR LA COMPOSICIÓN Y AISLAR EL VALOR POR DEFECTO
            for (int i = 0; i < requiredQuantity; i++) {
                PieceInstanceRow row = new PieceInstanceRow(
                        instanceIndex++,
                        pieceNameBase,
                        comp.getMasterCode(),
                        availableColors,
                        stockByColorMap
                );

                if (availableColors.isEmpty()) {
                    row.setSelectedColor("SIN STOCK");
                } else {
                    // --- SOLUCIÓN DE AISLAMIENTO: Inicializar solo la PRIMERA instancia (i=0) ---
                    // Esto rompe el ciclo de contaminación de datos y obliga a la segunda pieza a ser única.
                    if (i == 0) {
                        row.setSelectedColor(availableColors.get(0));
                    } else {
                        // Las instancias sucesivas (i > 0) se inicializan a NULL.
                        row.setSelectedColor(null);
                    }
                }

                masterDeductionList.add(row);
            }
        }
    }

    @FXML
    private void handleConfirmDeduction() {
        int quantityProductsToRemove = 0;
        try {
            quantityProductsToRemove = Integer.parseInt(quantityField.getText());
            if (quantityProductsToRemove <= 0) {
                UISettings.showAlert(Alert.AlertType.ERROR, "Error de Entrada", "Cantidad Inválida", "La cantidad debe ser mayor a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            UISettings.showAlert(Alert.AlertType.ERROR, "Error de Entrada", "Cantidad Inválida", "La cantidad debe ser un número entero.");
            return;
        }

        // 1. Verificación de selección de color faltante (se mantiene)
        for (PieceInstanceRow row : masterDeductionList) {
            if (row.getSelectedColor().equals("SIN STOCK") || row.getSelectedColor() == null) {
                UISettings.showAlert(Alert.AlertType.ERROR, "Error de Selección", "Selección Faltante",
                        "Debe seleccionar un color para la Pieza #" + row.getIndex() + " (" + row.getPieceNameBase() + ").");
                return;
            }
        }

        // 2. Acumular las deducciones por (Pieza Base + Color) para la validación global
        Map<String, Integer> instanceCountByColor = masterDeductionList.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getPieceNameBase() + "|" + row.getSelectedColor(),
                        Collectors.summingInt(row -> 1)
                ));

        // 3. VALIDACIÓN CRÍTICA DEL STOCK TOTAL
        for (Map.Entry<String, Integer> entry : instanceCountByColor.entrySet()) {

            String key = entry.getKey();
            int separatorIndex = key.indexOf("|");
            String pieceNameBase = key.substring(0, separatorIndex);
            String color = key.substring(separatorIndex + 1); // conserva toda la combinación de colores
            int instancesNeeded = entry.getValue();

            int totalQuantityToDeduct = instancesNeeded * quantityProductsToRemove;

            PieceInstanceRow sampleRow = masterDeductionList.stream()
                    .filter(r -> r.getPieceNameBase().equals(pieceNameBase) && r.getSelectedColor().equals(color))
                    .findFirst().orElse(null);

            int availableStock = (sampleRow != null) ? sampleRow.getStockByColor().getOrDefault(color, 0) : 0;

            if (totalQuantityToDeduct > availableStock) {
                UISettings.showAlert(Alert.AlertType.ERROR, "Error de Stock",
                        String.format("El stock total de la combinación Pieza '%s' / Color '%s' es insuficiente. Se necesitan %d unidades y solo hay %d disponibles.",
                                pieceNameBase, color, totalQuantityToDeduct, availableStock));
                return;
            }
        }

        // 4. Deducción (si la validación pasa)
        int finalQuantityProductsToRemove = quantityProductsToRemove;
        List<PieceStockDeduction> finalDeductions = instanceCountByColor.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    int separatorIndex = key.indexOf("|");
                    String pieceNameBase = key.substring(0, separatorIndex);
                    String color = key.substring(separatorIndex + 1);
                    int totalQuantityToDeduct = entry.getValue() * finalQuantityProductsToRemove;
                    return new PieceStockDeduction(pieceNameBase, color, totalQuantityToDeduct);
                })
                .collect(Collectors.toList());

        try {
            catalogService.deleteProductStockByComposition(selectedMasterProduct.getMasterCode(), finalDeductions);
            catalogService.decreaseMasterProductStock(selectedMasterProduct.getMasterCode(), quantityProductsToRemove);

            UISettings.showAlert(Alert.AlertType.INFORMATION, "Éxito", "Stock Eliminado", "Se ha descontado el stock.");
            // Cerrar la ventana actual
            ((Stage) productNameLabel.getScene().getWindow()).close();
        } catch (IOException | RuntimeException e) {
            UISettings.showAlert(Alert.AlertType.ERROR, "Error de Inventario", "Fallo al descontar el Stock: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) productNameLabel.getScene().getWindow()).close();
    }

    // --- CLASES ANIDADAS ---

    public static class PieceInstanceRow {
        private final IntegerProperty index;
        private final StringProperty pieceNameBase;
        private final StringProperty masterCode;
        private final ObservableList<String> availableColors;
        private final StringProperty selectedColor;
        private final Map<String, Integer> stockByColor;

        public PieceInstanceRow(int index, String pieceNameBase, String masterCode, List<String> availableColors, Map<String, Integer> stockByColor) {
            this.index = new SimpleIntegerProperty(index);
            this.pieceNameBase = new SimpleStringProperty(pieceNameBase);
            this.masterCode = new SimpleStringProperty(masterCode);
            this.availableColors = FXCollections.observableArrayList(availableColors);
            this.selectedColor = new SimpleStringProperty(null);
            this.stockByColor = stockByColor;
        }

        // --- PROPERTY GETTERS (CRÍTICOS PARA LA ESTABILIDAD) ---
        public IntegerProperty indexProperty() { return index; }
        public StringProperty pieceNameBaseProperty() { return pieceNameBase; }
        public StringProperty masterCodeProperty() { return masterCode; }
        public StringProperty selectedColorProperty() { return selectedColor; }

        public int getIndex() { return index.get(); }
        public String getPieceNameBase() { return pieceNameBase.get(); }
        public String getMasterCode() { return masterCode.get(); }
        public ObservableList<String> getAvailableColors() { return availableColors; }
        public String getSelectedColor() { return selectedColor.get(); }
        public Map<String, Integer> getStockByColor() { return stockByColor; }

        public void setSelectedColor(String selectedColor) { this.selectedColor.set(selectedColor); }
    }

    private class ComboBoxEditingCell extends TableCell<PieceInstanceRow, String> {
        private ComboBox<String> comboBox;

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createComboBox(getTableView().getItems().get(getIndex()));
                setText(null);
                setGraphic(comboBox);
                comboBox.requestFocus();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getString());
            setGraphic(null);
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (comboBox == null) {
                        createComboBox(getTableView().getItems().get(getIndex()));
                    }
                    comboBox.setValue(item);
                    setText(null);
                    setGraphic(comboBox);
                } else {
                    if (item.equals("SIN STOCK")) {
                        setText("SIN STOCK DISPONIBLE");
                    } else {
                        setText(item);
                    }
                    setGraphic(null);
                }
            }
        }

        private void createComboBox(PieceInstanceRow row) {
            comboBox = new ComboBox<>(row.getAvailableColors());
            comboBox.valueProperty().setValue(row.getSelectedColor());

            // Listener CRÍTICO: Captura el cambio de valor del ComboBox y fuerza el commit.
            comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null && !newValue.equals(oldValue)) {
                    // 1. Escribe el valor seleccionado en el modelo de la fila
                    row.setSelectedColor(newValue);

                    // 2. Ejecuta el commit de la celda (Cierra la edición y dispara setOnEditCommit)
                    commitEdit(newValue);
                }
            });

            comboBox.setMaxWidth(Double.MAX_VALUE);
        }

        private String getString() {
            return getItem() == null ? "" : getItem().toString();
        }
    }
}
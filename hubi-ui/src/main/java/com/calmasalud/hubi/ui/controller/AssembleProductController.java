package com.calmasalud.hubi.ui.controller; // Asegúrate que el package sea el correcto


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
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AssembleProductController {

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

    public void initData(MasterProduct product) {
        // 1. Configurar la tabla antes de cargar datos
        setupTable();

        this.selectedMasterProduct = product;
        productNameLabel.setText("Producto: " + product.getProductName());

        // 2. Traer la receta desde el repositorio
        List<ProductComposition> composition = compositionRepository.getComposition(product.getMasterCode());

        masterDeductionList.clear();

        int visualIndex = 1;

        for (ProductComposition compItem : composition) {
            // CORREGIDO: Usamos el método real de tu clase (getRequiredQuantity)
            int cantidad = compItem.getRequiredQuantity();

            // 3. EXPLOSIÓN DE FILAS: Una fila por cada unidad física necesaria
            for (int i = 0; i < cantidad; i++) {
                PieceInstanceRow row = new PieceInstanceRow(
                        visualIndex++, // Índice 1, 2, 3...
                        compItem.getPieceNameBase(), // CORREGIDO: Usamos getPieceNameBase
                        compItem.getPieceNameBase(), // CORREGIDO: Usamos getPieceNameBase como código
                        new ArrayList<>(),
                        new HashMap<>()
                );

                // Cargar stocks disponibles
                List<PieceStockColorView> stocks = productRepository.getStockByPieceNameBase(compItem.getPieceNameBase());

                Map<String, Integer> stockMap = new HashMap<>();
                List<String> colors = new ArrayList<>();

                for (PieceStockColorView s : stocks) {
                    stockMap.put(s.getColorName(), s.getQuantityAvailable());
                    if (s.getQuantityAvailable() > 0) {
                        colors.add(s.getColorName());
                    }
                }

                row.getStockByColor().putAll(stockMap);
                row.getAvailableColors().setAll(colors);

                masterDeductionList.add(row);
            }
        }

        deductionTable.setItems(masterDeductionList);
    }

    private void setupTable() {
        masterDeductionList = FXCollections.observableArrayList();

        colPieceIndex.setCellValueFactory(cellData -> cellData.getValue().indexProperty().asObject());
        colPieceName.setCellValueFactory(cellData -> cellData.getValue().pieceNameBaseProperty());
        colMasterCode.setCellValueFactory(cellData -> cellData.getValue().masterCodeProperty());
        colSelectedColor.setCellValueFactory(cellData -> cellData.getValue().selectedColorProperty());

        // Configuración del ComboBox en la celda
        colSelectedColor.setCellFactory(column -> new ComboBoxEditingCell());
        colSelectedColor.setOnEditCommit(event -> {
            PieceInstanceRow row = event.getRowValue();
            row.setSelectedColor(event.getNewValue());
            deductionTable.refresh();
        });

        // Stock disponible dinámico
        colStockAvailable.setCellValueFactory(data -> {
            PieceInstanceRow row = data.getValue();
            return new SimpleIntegerProperty(
                    row.getStockByColor().getOrDefault(row.getSelectedColor(), 0)
            ).asObject();
        });

        deductionTable.setEditable(true);
        deductionTable.setItems(masterDeductionList);
    }

    @FXML
    private void handleConfirmAssembly() {
        int quantityProductsToRemove = 0;
        try {
            quantityProductsToRemove = Integer.parseInt(quantityField.getText());
            if (quantityProductsToRemove <= 0) {
                UISettings.showAlert(Alert.AlertType.ERROR, "Error", "La cantidad debe ser mayor a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            UISettings.showAlert(Alert.AlertType.ERROR, "Error", "La cantidad debe ser un número entero.");
            return;
        }

        // ========================================================================
        // PASO 1: VALIDACIÓN MATEMÁTICA CONTRA LA RECETA ORIGINAL
        // ========================================================================

        List<ProductComposition> originalRecipe = compositionRepository.getComposition(selectedMasterProduct.getMasterCode());

        // Contamos cuántas unidades de cada pieza ha configurado realmente el usuario en la tabla
        Map<String, Long> userSelectionCounts = masterDeductionList.stream()
                .filter(row -> row.getSelectedColor() != null && !row.getSelectedColor().equals("SIN STOCK") && !row.getSelectedColor().isEmpty())
                .collect(Collectors.groupingBy(PieceInstanceRow::getMasterCode, Collectors.counting())); // Usamos masterCode que guarda el pieceNameBase

        // Verificamos que coincidan los números
        for (ProductComposition recipeItem : originalRecipe) {
            String code = recipeItem.getPieceNameBase(); // CORREGIDO
            int requiredPerUnit = recipeItem.getRequiredQuantity(); // CORREGIDO

            long selectedByUser = userSelectionCounts.getOrDefault(code, 0L);
            System.out.println("--- REVISIÓN DE RECETA ---");
            System.out.println("Pieza: " + code);
            System.out.println("La BD dice que necesitas: " + requiredPerUnit);
            System.out.println("Tú seleccionaste en la tabla: " + selectedByUser);

            if (selectedByUser < requiredPerUnit) {
                UISettings.showAlert(Alert.AlertType.ERROR, "Selección Incompleta",
                        "Faltan piezas para el componente: " + code,
                        "La receta exige " + requiredPerUnit + " unidades por producto.\n" +
                                "Solo has seleccionado color para " + selectedByUser + " unidades.");
                return;
            }
        }

        // ========================================================================
        // PASO 2: VALIDACIÓN DE STOCK DISPONIBLE
        // ========================================================================

        // Agrupar por (Pieza + Color) para ver consumo total de material
        Map<String, Integer> instanceCountByColor = masterDeductionList.stream()
                .filter(row -> row.getSelectedColor() != null)
                .collect(Collectors.groupingBy(
                        row -> row.getPieceNameBase() + "|" + row.getSelectedColor(),
                        Collectors.summingInt(row -> 1)
                ));

        for (Map.Entry<String, Integer> entry : instanceCountByColor.entrySet()) {
            String key = entry.getKey();
            int separatorIndex = key.indexOf("|");
            String color = key.substring(separatorIndex + 1);
            int instancesNeeded = entry.getValue();
            int totalQuantityToDeduct = instancesNeeded * quantityProductsToRemove;

            // Buscar stock disponible para ese color
            int availableStock = 0;
            for(PieceInstanceRow r : masterDeductionList) {
                if(r.getSelectedColor() != null && r.getSelectedColor().equals(color)) {
                    availableStock = r.getStockByColor().getOrDefault(color, 0);
                    break;
                }
            }

            if (totalQuantityToDeduct > availableStock) {
                UISettings.showAlert(Alert.AlertType.ERROR, "Stock Insuficiente", "Falta Material",
                        String.format("Necesitas %d unidades de color '%s', pero solo tienes %d disponibles.",
                                totalQuantityToDeduct, color, availableStock));
                return;
            }
        }

        // ========================================================================
        // PASO 3: EJECUTAR
        // ========================================================================
        int finalQuantity = quantityProductsToRemove;
        List<PieceStockDeduction> finalDeductions = instanceCountByColor.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    int separatorIndex = key.indexOf("|");
                    String pieceNameBase = key.substring(0, separatorIndex);
                    String color = key.substring(separatorIndex + 1);
                    int totalQuantityToDeduct = entry.getValue() * finalQuantity;
                    return new PieceStockDeduction(pieceNameBase, color, totalQuantityToDeduct);
                })
                .collect(Collectors.toList());

        try {
            catalogService.registerProductAssembly(selectedMasterProduct.getMasterCode(), finalDeductions, finalQuantity);
            UISettings.showAlert(Alert.AlertType.INFORMATION, "Éxito", "Producción Registrada correctamente.");
            ((Stage) productNameLabel.getScene().getWindow()).close();
        } catch (Exception e) {
            UISettings.showAlert(Alert.AlertType.ERROR, "Error", "Fallo al registrar: " + e.getMessage());
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
        private final StringProperty masterCode; // Aquí guardamos el ID de la pieza
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
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (comboBox != null) comboBox.setValue(item);
                    setText(null);
                    setGraphic(comboBox);
                } else {
                    setText(item == null || item.equals("SIN STOCK") ? "SELECCIONAR COLOR" : item);
                    setGraphic(null);
                }
            }
        }

        private void createComboBox(PieceInstanceRow row) {
            comboBox = new ComboBox<>(row.getAvailableColors());
            comboBox.valueProperty().setValue(row.getSelectedColor());
            comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null && !newValue.equals(oldValue)) {
                    row.setSelectedColor(newValue);
                    commitEdit(newValue);
                }
            });
            comboBox.setMaxWidth(Double.MAX_VALUE);
        }

        private String getString() {
            return getItem() == null ? "" : getItem();
        }
    }
}
package com.calmasalud.hubi.ui.controller;

// --- IMPORTS MODELOS Y REPOSITORIOS ---
import com.calmasalud.hubi.core.model.*;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.repository.ISupplyRepository;
import com.calmasalud.hubi.core.service.CatalogService;
import com.calmasalud.hubi.persistence.repository.MasterProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.ProductCompositionRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.SupplyRepositorySQLite;

// --- IMPORTS JAVAFX ---
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InventarioController {

    // =====================================================================
    // 1. DEPENDENCIAS
    // =====================================================================
    private final IMasterProductRepository masterProductRepository = new MasterProductRepositorySQLite();
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    private final IProductCompositionRepository productCompositionRepository = new ProductCompositionRepositorySQLite();
    private final ISupplyRepository supplyRepository = new SupplyRepositorySQLite();
    public StackPane contentArea;

    private CatalogService catalogService; // Se inyecta desde MainController

    // Lista observable para la tabla de insumos
    private final ObservableList<Supply> supplyData = FXCollections.observableArrayList();

    // =====================================================================
    // 2. CAMPOS FXML (VISTAS Y CONTENEDORES)
    // =====================================================================
    @FXML private VBox productContentArea;      // Contenedor de Productos
    @FXML private VBox supplyContentArea; // Contenedor de Insumos

    // --- TABLA DE PRODUCTOS (ORIGINAL) ---
    @FXML private TreeTableView<MasterProduct> productStockTable;
    @FXML private TreeTableColumn<MasterProduct, String> colProductoNombre;
    @FXML private TreeTableColumn<MasterProduct, String> colProductoCodigo;
    @FXML private TreeTableColumn<MasterProduct, Number> colProductoDisponible;
    @FXML private TreeTableColumn<MasterProduct, Number> colProductoPrecio;

    // --- TABLA DE INSUMOS (NUEVA) ---
    @FXML private TableView<Supply> tablaInsumos;
    @FXML private TableColumn<Supply, String> colInsumoCodigo;
    @FXML private TableColumn<Supply, String> colInsumoNombre;
    @FXML private TableColumn<Supply, String> colInsumoTipo;
    @FXML private TableColumn<Supply, String> colInsumoColor;
    @FXML private TableColumn<Supply, Double> colInsumoStock;
    @FXML private TableColumn<Supply, Double> colInsumoUmbral;

    // --- BOTONES Y UI INSUMOS ---
    @FXML private Button btnAgregarInsumo;
    @FXML private Button btnModificarInsumo;
    @FXML private Button btnEliminarInsumo;
    @FXML private Label lblSelectionHint;


    // =====================================================================
    // 3. INICIALIZACIÓN
    // =====================================================================
    @FXML
    public void initialize() {
        System.out.println("Controlador de Inventario inicializado.");

        // --- A. CONFIGURACIÓN DE COLUMNAS PRODUCTOS (Lógica Original) ---
        colProductoNombre.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item == null) return null;

            // Lógica para mostrar nombre de pieza vs producto
            if (item.getMasterCode() != null && item.getMasterCode().length() > 5) {
                String originalFileName = item.getProductName();
                int dotIndex = originalFileName.lastIndexOf('.');
                return new SimpleStringProperty(dotIndex != -1 ? originalFileName.substring(0, dotIndex) : originalFileName);
            }
            return new SimpleStringProperty(item.getProductName());
        });

        colProductoCodigo.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            return (item == null) ? null : new SimpleStringProperty(item.getMasterCode());
        });

        colProductoDisponible.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item instanceof MasterProductView) {
                return new SimpleObjectProperty<>(((MasterProductView) item).getQuantityAvailable());
            }
            return new SimpleObjectProperty<>(0);
        });

        colProductoPrecio.setCellValueFactory(cellData -> {
            MasterProduct item = cellData.getValue().getValue();
            if (item instanceof MasterProductView) {
                return new SimpleObjectProperty<>(((MasterProductView) item).getPrice());
            }
            return new SimpleObjectProperty<>(null);
        });

        // --- B. CONFIGURACIÓN DE COLUMNAS INSUMOS (Nueva Lógica) ---
        if (tablaInsumos != null) {
            colInsumoCodigo.setCellValueFactory(new PropertyValueFactory<>("code"));
            colInsumoNombre.setCellValueFactory(new PropertyValueFactory<>("name"));
            colInsumoTipo.setCellValueFactory(new PropertyValueFactory<>("tipoFilamento"));
            colInsumoColor.setCellValueFactory(new PropertyValueFactory<>("colorFilamento"));
            colInsumoStock.setCellValueFactory(new PropertyValueFactory<>("cantidadDisponible"));
            colInsumoUmbral.setCellValueFactory(new PropertyValueFactory<>("umbralAlerta"));

            tablaInsumos.setItems(supplyData);

            // Doble clic para editar
            tablaInsumos.setOnMouseClicked(event -> {
                if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                    handleManageSupplyStock(null);
                }
            });
        }
    }

    /**
     * Inyección de dependencia y Configuración Inicial.
     * Llamado desde MainController.
     */
    public void setCatalogService(CatalogService catalogService) {
        this.catalogService = catalogService;

        // Al inyectar el servicio, forzamos que se muestre la vista de Productos por defecto
        // y cargamos los datos.
        updateViewVisibility(true);
    }

    /**
     * Method Público llamado por MainController para cambiar la vista activa.
     * @param showProducts true = Productos, false = Insumos
     */
    public void setActiveView(boolean showProducts) {
        updateViewVisibility(showProducts);
    }

    /**
     * Lógica central de visibilidad y carga de datos.
     */
    private void updateViewVisibility(boolean showProducts) {
        if (productContentArea != null && supplyContentArea != null) {
            // 1. Alternar visibilidad de contenedores
            productContentArea.setVisible(showProducts);
            productContentArea.setManaged(showProducts);

            supplyContentArea.setVisible(!showProducts);
            supplyContentArea.setManaged(!showProducts);

            // 2. Cargar los datos correspondientes a la vista activa
            if (catalogService != null) {
                if (showProducts) {
                    loadProductStockData();
                } else {
                    loadSupplyData();
                }
            }
        }
    }

    // =====================================================================
    // 4. MethodS DE CARGA DE DATOS
    // =====================================================================

    /**
     * Carga datos de Productos (Árbol jerárquico) con filtro .gcode.
     */
    /**
     * Carga datos de Productos (Árbol jerárquico) con filtro .gcode.
     * MODIFICADO: Oculta piezas y colores con stock 0.
     */
    private void loadProductStockData() {
        if (productStockTable == null) return;

        try {
            List<MasterProduct> masterProductsWithStock = masterProductRepository.findAll();
            TreeItem<MasterProduct> rootItem = new TreeItem<>(null);

            for (MasterProduct masterProduct : masterProductsWithStock) {
                // 1. Obtener la Receta (BOM) para saber cuántas de cada pieza se usan
                List<ProductComposition> composition = productCompositionRepository.getComposition(masterProduct.getMasterCode());

                // Mapa rápido: NombrePieza -> Cantidad Requerida
                java.util.Map<String, Integer> quantitiesMap = composition.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ProductComposition::getPieceNameBase,
                                ProductComposition::getRequiredQuantity
                        ));

                List<Product> pieces = productRepository.findPiecesByMasterPrefix(masterProduct.getProductPrefix());
                List<TreeItem<MasterProduct>> pieceNodes = new java.util.ArrayList<>();

                double totalMasterCost = 0.0; // Acumulador para el costo del producto final

                for (Product piece : pieces) {
                    // Solo nos interesan los archivos GCODE (piezas físicas)
                    if (piece.getFileExtension() == null || !piece.getFileExtension().equalsIgnoreCase(".gcode")) {
                        continue;
                    }

                    String pieceNameBase = piece.getName().substring(0, piece.getName().lastIndexOf('.'));
                    int totalPieceStock = productRepository.getPieceStockQuantity(pieceNameBase);

                    // Obtener costo unitario y cantidad requerida por receta
                    double unitCost = piece.getCost();
                    int qtyRequired = quantitiesMap.getOrDefault(pieceNameBase, 0); // Si no está en receta, es 0

                    // SUMAR AL TOTAL DEL PADRE (Costo Unitario * Cantidad en Receta)
                    totalMasterCost += (unitCost * qtyRequired);

                    // Nodo Nivel 2: La PIEZA (Mostramos su Costo Unitario)
                    MasterProductView pieceNodeData = new MasterProductView(
                            piece.getCode(),
                            masterProduct.getProductPrefix(),
                            piece.getName(),
                            "Pieza Individual",
                            totalPieceStock,
                            unitCost // <--- AQUI VA EL PRECIO DE LA PIEZA
                    );
                    TreeItem<MasterProduct> pieceNode = new TreeItem<>(pieceNodeData);

                    // --- Cargar Nivel 3: Colores (Sin cambios, precio 0 visualmente) ---
                    List<PieceStockColorView> colorStocks = productRepository.getStockByPieceNameBase(pieceNameBase);
                    for (PieceStockColorView colorStock : colorStocks) {
                        if (colorStock.getQuantityAvailable() > 0) {
                            MasterProductView colorNodeData = new MasterProductView(
                                    piece.getCode() + "-" + colorStock.getColorName(),
                                    masterProduct.getProductPrefix(),
                                    colorStock.getColorName(),
                                    "Stock por Color",
                                    colorStock.getQuantityAvailable(),
                                    0.0 // El color hereda el precio de la pieza, mostramos 0 o nada
                            );
                            pieceNode.getChildren().add(new TreeItem<>(colorNodeData));
                        }
                    }

                    // AGREGAR EL NODO DE PIEZA A LA LISTA TEMPORAL
                    pieceNodes.add(pieceNode);
                }

                // 2. Crear el Nodo Nivel 1: PRODUCTO MAESTRO con el Costo Total Calculado
                MasterProductView displayMasterNode = new MasterProductView(
                        masterProduct.getMasterCode(),
                        masterProduct.getProductPrefix(),
                        masterProduct.getProductName(),
                        masterProduct.getDescription(),
                        ((MasterProductView) masterProduct).getQuantityAvailable(),
                        totalMasterCost // <--- TOTAL SUMADO
                );

                TreeItem<MasterProduct> masterNode = new TreeItem<>(displayMasterNode);

                // 3. ¡IMPORTANTE! AGREGAR LOS HIJOS AL PADRE
                masterNode.getChildren().addAll(pieceNodes);

                rootItem.getChildren().add(masterNode);
            }

            productStockTable.setRoot(rootItem);
            productStockTable.setShowRoot(false);
            // No expandir por defecto para no saturar, o true si prefieres
            rootItem.setExpanded(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Carga datos de Insumos (Lista simple).
     */
    private void loadSupplyData() {
        if (catalogService == null) return;

        try {
            List<Supply> supplies = catalogService.listAllSupplies();
            supplyData.setAll(supplies);

            boolean isEmpty = supplyData.isEmpty();
            if (btnModificarInsumo != null) btnModificarInsumo.setDisable(isEmpty);
            if (btnEliminarInsumo != null) btnEliminarInsumo.setDisable(isEmpty);
            if (lblSelectionHint != null) lblSelectionHint.setVisible(!isEmpty);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Error", "No se pudieron cargar los insumos.");
        }
    }

    // =====================================================================
    // 5. HANDLERS DE ACCIÓN (PRODUCTOS - LÓGICA ORIGINAL)
    // =====================================================================

    @FXML
    private void handleAddProductStock(ActionEvent event) {
        // 1. Obtener y validar selección
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        if (selectedItem == null || selectedItem.getValue() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione un Producto Maestro (Línea Padre) para armar stock.");
            return;
        }

        // Validación jerárquica
        if (selectedItem.getParent() == null || selectedItem.getParent().getValue() != null) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo puede armar productos seleccionando la línea principal (Producto Maestro).");
            return;
        }

        MasterProduct selectedProduct = selectedItem.getValue();
        String masterCode = selectedProduct.getMasterCode();

        try {
            // =================================================================================
            // PASO 1: VERIFICAR/DEFINIR LA COMPOSICIÓN (RECETA)
            // =================================================================================
            boolean compositionExists = productCompositionRepository.compositionExists(masterCode);
            boolean openCompositionFlow = !compositionExists;

            // Si ya existe, preguntamos si quiere editarla (opcional, pero útil)
            // O simplemente asumimos que si no existe, la creamos.
            if (!compositionExists) {
                Alert alert = new Alert(AlertType.CONFIRMATION);
                alert.setTitle("Definir Receta");
                alert.setHeaderText("Este producto no tiene definida su composición.");
                alert.setContentText("Antes de armar, necesitas definir qué piezas lo componen. ¿Deseas hacerlo ahora?");
                alert.getDialogPane().getStyleClass().add("hubi-dialog");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != ButtonType.OK) {
                    return; // Cancelar si el usuario no quiere definir la receta
                }
            }

            // Abrimos el editor de composición si no existe o si el usuario quisiera editar (lógica simplificada aquí para forzar si no existe)
            if (openCompositionFlow) {
                System.out.println("ℹ️ Abriendo editor de composición (BOM).");

                String productPrefix = selectedProduct.getProductPrefix();
                List<Product> rawPieces = productRepository.findPiecesByMasterPrefix(productPrefix);

                if (rawPieces.isEmpty()) {
                    showAlert(AlertType.ERROR, "Error de Catálogo", "No hay archivos de piezas cargados para este producto. Agregue piezas primero.");
                    return;
                }

                // Filtrado de piezas únicas (Gcode > 3mf > Stl)
                List<Product> uniquePieces = new java.util.ArrayList<>(
                        rawPieces.stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        p -> {
                                            String name = p.getName();
                                            return name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                                        },
                                        p -> p,
                                        (existing, replacement) -> {
                                            if (replacement.getFileExtension() != null && replacement.getFileExtension().equalsIgnoreCase(".gcode")) {
                                                return replacement;
                                            }
                                            return existing;
                                        }
                                ))
                                .values()
                );

                // Cargar modal de Composición
                FXMLLoader loaderComp = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/CompositionModal.fxml"));
                Parent rootComp = loaderComp.load();
                CompositionController compController = loaderComp.getController();

                compController.setCompositionData(masterCode, selectedProduct.getProductName(), uniquePieces);

                Stage modalStageComp = new Stage();
                modalStageComp.setTitle("Definir Receta (BOM) - " + selectedProduct.getProductName());
                modalStageComp.initModality(Modality.APPLICATION_MODAL);
                modalStageComp.setScene(new Scene(rootComp));
                modalStageComp.showAndWait();

                // Si al cerrar no se guardó la composición, cancelamos el armado
                if (!productCompositionRepository.compositionExists(masterCode)) {
                    return;
                }
            }

            // =================================================================================
            // PASO 2: ABRIR VENTANA DE ARMADO (AssembleProductModal)
            // =================================================================================
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AssembleProductModal.fxml"));
            Parent root = loader.load();

            AssembleProductController controller = loader.getController();
            controller.setDependencies(catalogService, productCompositionRepository, productRepository);
            controller.initData(selectedProduct);

            Stage modalStage = new Stage();
            modalStage.setTitle("Armar Producto - " + selectedProduct.getProductName());
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.setScene(new Scene(root));
            modalStage.showAndWait();

            loadProductStockData(); // Recargar tabla final

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error", "Error al abrir la ventana: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeleteProductStock(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        // --- VALIDACIÓN CORREGIDA ---
        // 1. Debe haber selección.
        // 2. No debe ser la raíz (getParent() == null).
        // 3. El padre NO debe tener valor (si el padre tiene valor, es porque seleccionaste una PIEZA hija).
        if (selectedItem == null || selectedItem.getValue() == null ||
                selectedItem.getParent() == null || selectedItem.getParent().getValue() != null) {

            showAlert(AlertType.WARNING, "Selección", "Seleccione un Producto Maestro (Línea Padre) para eliminar stock.");
            return;
        }

        MasterProduct selectedProduct = selectedItem.getValue();

        // Lógica de eliminación simple (Venta/Pérdida)
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Eliminar Stock (Venta/Pérdida)");
        dialog.setHeaderText("Producto: " + selectedProduct.getProductName());
        dialog.setContentText("Cantidad a eliminar:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                int qty = Integer.parseInt(result.get());
                if (qty <= 0) throw new NumberFormatException();

                // Llamamos directo a disminuir stock maestro (SIN tocar piezas, ya se gastaron al armar)
                catalogService.decreaseMasterProductStock(selectedProduct.getMasterCode(), qty);

                showAlert(AlertType.INFORMATION, "Éxito", "Stock eliminado correctamente.");
                loadProductStockData(); // Recargar tabla
            } catch (NumberFormatException e) {
                showAlert(AlertType.ERROR, "Error", "Ingrese una cantidad válida mayor a 0.");
            } catch (Exception e) {
                showAlert(AlertType.ERROR, "Error", e.getMessage());
            }
        }
    }
    @FXML
    private void handleModifyPieceStock(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();
        // Validar selección: Debe ser una pieza (nodo hijo directo de raíz o intermedio)
        if (selectedItem == null || selectedItem.getValue().getMasterCode().length() <= 5) {
            showAlert(AlertType.WARNING, "Selección", "Seleccione una Pieza (Línea Hija) para agregar stock individual.");
            return;
        }
        // Evitar selección de nivel color (si aplica)
        if (!selectedItem.getChildren().isEmpty() && selectedItem.getValue().getProductName().equals("Stock por Color")) {
            return;
        }

        try {
            MasterProduct piece = selectedItem.getValue();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AddPieceStockModal.fxml"));
            Parent root = loader.load();
            AddPieceStockController controller = loader.getController();
            controller.setCatalogService(catalogService);
            controller.setPieceData(piece.getMasterCode(), piece.getProductName());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            loadProductStockData();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    private void handleDeleteStockColorUnit(ActionEvent event) {
        TreeItem<MasterProduct> selectedItem = productStockTable.getSelectionModel().getSelectedItem();

        // Validaciones iniciales de selección
        if (selectedItem == null || selectedItem.getValue() == null || selectedItem.getParent() == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una fila de Color (Nivel 3) para descontar.");
            return;
        }

        MasterProduct item = selectedItem.getValue();

        // 1. Validar que es un NODO NIETO (Nivel 3: El color) y extraer el stock actual
        if (!(item instanceof MasterProductView)) {
            showAlert(AlertType.WARNING, "Acción Inválida", "Solo se puede descontar de una línea de Color.");
            return;
        }

        MasterProductView colorNode = (MasterProductView) item;
        int currentAvailable = colorNode.getQuantityAvailable(); // Stock actual del color

        if (currentAvailable <= 0) {
            showAlert(AlertType.WARNING, "Stock Insuficiente", "El color seleccionado no tiene stock disponible para descontar.");
            return;
        }

        // 2. Extracción de datos CRÍTICOS
        // --- CORRECCIÓN DEL NPE ---
        TreeItem<MasterProduct> pieceNodeItem = selectedItem.getParent();

        if (pieceNodeItem == null || pieceNodeItem.getValue() == null) {
            showAlert(AlertType.ERROR, "Error de Datos", "No se pudo identificar la pieza base. Seleccione un Color válido.");
            return;
        }
        // --------------------------

        MasterProduct pieceNode = pieceNodeItem.getValue(); // Nodo Padre: La pieza genérica
        String pieceNameOriginal = pieceNode.getProductName();
        String pieceNameBase = pieceNameOriginal.substring(0, pieceNameOriginal.lastIndexOf('.'));
        String colorName = colorNode.getProductName().replaceFirst("\\s*\\(\\d+ uds\\)", ""); // Limpiar el nombre del nodo para obtener solo el color

        // 3. SOLICITAR LA CANTIDAD A ELIMINAR (usando TextInputDialog)
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Eliminar Stock de Pieza");
        dialog.setHeaderText("Pieza: " + pieceNameBase + " | Color: " + colorName);
        dialog.setContentText("Ingrese la cantidad de unidades a eliminar (Disponibles: " + currentAvailable + "):");
        dialog.getDialogPane().getStyleClass().add("hubi-dialog");

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            int quantityToRemove;
            try {
                // Validación y Conversión
                quantityToRemove = Integer.parseInt(result.get());

                if (quantityToRemove <= 0) {
                    showAlert(AlertType.ERROR, "Error de Entrada", "La cantidad debe ser mayor a 0.");
                    return;
                }

                if (quantityToRemove > currentAvailable) {
                    showAlert(AlertType.ERROR, "Error de Stock",
                            "La cantidad a eliminar (" + quantityToRemove + ") no puede exceder las unidades disponibles (" + currentAvailable + ").");
                    return;
                }

                // 4. Lógica de Persistencia: Llamar al Method de disminución de stock por color
                productRepository.decreasePieceStockQuantity(pieceNameBase, colorName, quantityToRemove);

                showAlert(AlertType.INFORMATION, "Éxito", quantityToRemove + " unidades de " + pieceNameBase + " (" + colorName + ") descontadas.");
                loadProductStockData(); // Recargar datos para actualizar la vista

            } catch (NumberFormatException e) {
                showAlert(AlertType.ERROR, "Error de Entrada", "Por favor, ingrese un número entero válido.");
            } catch (RuntimeException e) {
                // Captura el error de 'Stock insuficiente' o la falla SQL
                showAlert(AlertType.ERROR, "Error de Stock", "Fallo al eliminar stock: " + e.getMessage());
            }
        }
    }


    // =====================================================================
    // 6. HANDLERS DE ACCIÓN (INSUMOS - NUEVA LÓGICA)
    // =====================================================================

    @FXML
    private void handleManageSupplyStock(ActionEvent event) {
        // 1. Detectar si el clic viene del botón de "Agregar"
        boolean esAgregar = (event.getSource() == btnAgregarInsumo);

        Supply selectedSupply = tablaInsumos.getSelectionModel().getSelectedItem();

        // 2. Validación CONDICIONAL
        // Si NO es agregar (es decir, es Modificar) Y no hay nada seleccionado -> Error
        if (!esAgregar && selectedSupply == null) {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione un insumo de la lista para modificar sus datos.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AddSupplyModal.fxml"));
            Parent parent = loader.load();

            AddSupplyController controller = loader.getController();
            controller.setCatalogService(catalogService);
            controller.setOnStockUpdated(this::loadSupplyData);

            // 3. Configuración según el modo
            if (esAgregar) {
                // Si es agregar, nos aseguramos de que no haya nada seleccionado visualmente
                // para evitar confusiones, y NO le pasamos datos al controller.
                tablaInsumos.getSelectionModel().clearSelection();
            } else {
                // Si es modificar, le pasamos los datos del insumo seleccionado
                controller.setSupplyToEdit(selectedSupply);
            }

            Stage stage = new Stage();
            // Título dinámico para que quede más prolijo
            stage.setTitle(esAgregar ? "Registrar Nuevo Insumo" : "Modificar Insumo Existente");
            stage.setScene(new Scene(parent));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error", "No se pudo abrir la ventana de gestión: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @FXML
    private void handleRemoveSupplyStock(ActionEvent event) {
        Supply selectedSupply = tablaInsumos.getSelectionModel().getSelectedItem();
        if (selectedSupply == null) {
            showAlert(AlertType.WARNING, "Selección", "Seleccione un insumo de la lista.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/RemoveSupplyModal.fxml"));
            Parent parent = loader.load();

            RemoveSupplyController controller = loader.getController();
            controller.setCatalogService(catalogService);
            controller.setSupply(selectedSupply);
            controller.setOnStockUpdated(this::loadSupplyData);

            Stage stage = new Stage();
            stage.setTitle("Descartar Stock");
            stage.setScene(new Scene(parent));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error", "No se pudo abrir la ventana de descarte.");
            e.printStackTrace();
        }
    }
    /**
     * Handler para eliminar permanentemente un insumo del catálogo (Requiere stock cero).
     * Este método debe ser vinculado a un nuevo botón "Eliminar Insumo" en el FXML.
     */
    @FXML
    private void handleDeleteSupplyPermanently(ActionEvent event) {
        Supply selectedSupply = tablaInsumos.getSelectionModel().getSelectedItem();

        if (selectedSupply == null) {
            showAlert(Alert.AlertType.WARNING, "Selección Requerida", "Seleccione un insumo de la lista para eliminarlo permanentemente.");
            return;
        }

        double stock = selectedSupply.getCantidadDisponible();
        boolean requiresForce = stock > 0.01;

        // --- PRIMERA CONFIRMACIÓN: Eliminación General ---
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar Eliminación");
        alert.setHeaderText("Eliminar Insumo: " + selectedSupply.getName());
        alert.setContentText("¿Está seguro de que desea eliminar permanentemente este insumo del catálogo?");

        // --- SEGUNDA CONFIRMACIÓN (CONDICIONAL): Advertencia de Stock ---
        if (requiresForce) {
            alert.setContentText(alert.getContentText() +
                    "\n\n🚨 ADVERTENCIA: El insumo tiene un stock de " + String.format("%.2f", stock) + "g. \n\n" +
                    "Al eliminarlo, este stock se perderá permanentemente del sistema. ¿Desea continuar?");
        }

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Llama al nuevo método del CatalogService, pasando true si se requirió la confirmación forzada
                catalogService.deleteSupplyPermanently(selectedSupply.getId(), requiresForce);

                showAlert(Alert.AlertType.INFORMATION, "Éxito", "Insumo eliminado permanentemente del catálogo.");
                loadSupplyData(); // Recargar la tabla

            } catch (IllegalArgumentException e) {
                showAlert(Alert.AlertType.ERROR, "Error de Eliminación", e.getMessage());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Ocurrió un error al intentar eliminar el insumo.");
                e.printStackTrace();
            }
        }
    }
    // =====================================================================
    // 7. UTILIDADES AUXILIARES
    // =====================================================================

    private void openCompositionModal(MasterProduct product) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/CompositionModal.fxml"));
        Parent root = loader.load();
        CompositionController controller = loader.getController();
        List<Product> rawPieces = productRepository.findPiecesByMasterPrefix(product.getProductPrefix());
        controller.setCompositionData(product.getMasterCode(), product.getProductName(), rawPieces);

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private void openAddStockModal(MasterProduct product) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/AddStockModal.fxml"));
        Parent root = loader.load();
        AddStockController controller = loader.getController();
        controller.setProductName(product.getProductName());

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(root));
        stage.showAndWait();

        if (controller.isAccepted()) {
            int qty = controller.getCantidad();
            catalogService.verifyPieceAvailability(product.getMasterCode(), qty);
            masterProductRepository.increaseStock(product.getMasterCode(), qty);
            showAlert(AlertType.INFORMATION, "Éxito", "Stock agregado.");
            loadProductStockData();
        }
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }

}
package com.calmasalud.hubi.ui.controller;


import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.service.FileParameterExtractor;
import com.calmasalud.hubi.persistence.repository.MasterProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import com.calmasalud.hubi.core.service.CatalogService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import javafx.scene.control.*;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import javafx.scene.control.*;


public class CatalogManagerController {

    // --- CONSTANTE DE COSTO POR DEFECTO ---
    private static final double DEFAULT_COSTO_POR_GRAMO = 18.5;

    // --- Inyección de Dependencia ---
    private final IProductRepository productSqliteRepository = new ProductRepositorySQLite();

    // 1. Instanciar el nuevo Repositorio Maestro
    private final IMasterProductRepository masterProductRepository = new MasterProductRepositorySQLite(); // NUEVA INSTANCIA

    // 2. Usar el constructor actualizado del servicio, pasando ambas dependencias
    private final CatalogService catalogoService = new CatalogService(productSqliteRepository, masterProductRepository); // MODIFICADO
    private final FileParameterExtractor extractor = new FileParameterExtractor();
    // ---------------------------------

    // Campo para mantener el directorio seleccionado
    private File currentSelectedDirectory = null;

    // --- Panel Izquierdo (Explorador) ---
    @FXML private TextField txtBusqueda;
    @FXML private Button btnActualizar;

    // --- Componentes del explorador actualizados ---
    @FXML private TreeView<File> folderTreeView;
    @FXML private TableView<File> fileTableView;
    @FXML private TableColumn<File, String> colNombre;
    @FXML private TableColumn<File, String> colTamaño; // Representa Código Único
    @FXML private TableColumn<File, String> colFechaMod;

    @FXML private Button btnAgregar;
    @FXML private Button btnEliminar;

    // --- Panel Derecho (Parámetros) ---
    @FXML private Label lblNombreArchivo;
    @FXML private StackPane visor3DPlaceholder;

    // NUEVO: ComboBox para seleccionar el perfil de filamento (Tool)
    @FXML private ComboBox<Map.Entry<Integer, FileParameterExtractor.FilamentProfile>> cmbFilamento;

    // CAMPOS DE FILAMENTO ESPECÍFICO (Actualizados al cambiar cmbFilamento)
    @FXML private TextField paramPeso;
    @FXML private TextField paramLargo;
    @FXML private TextField paramTipoFilamento;
    // ELIMINADO: paramColorFilamento ya no se usa, el color se ve en cmbFilamento.
    @FXML private TextField paramDensidad;
    @FXML private TextField paramDiametro; // NUEVO CAMPO

    // CAMPOS GENERALES (Tiempo/Altura de Capa)
    @FXML private TextField paramTiempo;
    @FXML private TextField paramAlturaCapa;

    @FXML private TextField paramPrecioPorGramo;
    @FXML private TextField paramCosto;

    private static final File REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos").toFile();

    @FXML
    public void initialize() {
        System.out.println("Controlador de Catálogo (v2.2 con Multi-Color) inicializado.");

        // Configuración CellFactory para TreeView (mostrar nombres amigables)
        folderTreeView.setCellFactory(param -> new TreeCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item.equals(REPOSITORIO_BASE)) {
                    setText("Repositorio Master"); // Etiqueta raíz
                } else {
                    setText(item.getName()); // Nombre de carpeta/archivo
                }
            }
        });

        // Listener para cambios de selección en TreeView
        folderTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            File selectedFile = (newValue != null) ? newValue.getValue() : null;

            if (selectedFile != null && selectedFile.isDirectory() && !selectedFile.equals(REPOSITORIO_BASE)) {
                currentSelectedDirectory = selectedFile; // Directorio de producto seleccionado
                cargarDetallesCarpeta(selectedFile);
            } else {
                currentSelectedDirectory = null; // Limpiar si es la raíz o no es directorio válido
                if (selectedFile != null && selectedFile.isDirectory()) {
                    cargarDetallesCarpeta(selectedFile); // Cargar contenido de la raíz si se selecciona
                } else {
                    fileTableView.getItems().clear(); // Limpiar tabla si no hay selección válida
                }
            }
        });

        // Listener para deseleccionar al hacer clic en área vacía del TreeView
        folderTreeView.setOnMouseClicked(event -> {
            Node node = event.getPickResult().getIntersectedNode();
            while (node != null && !(node instanceof TreeCell)) {
                node = node.getParent();
            }
            if (node == null || ((TreeCell<?>) node).isEmpty()) {
                if (folderTreeView.getSelectionModel().getSelectedItem() != null) {
                    Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(1), e ->
                            folderTreeView.getSelectionModel().clearSelection()));
                    timeline.play();
                }
            }
        });

        // Configuración de las columnas de la TableView (sin cambios)
        colNombre.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            String fileName = archivo.getName();
            String code = "";
            if (fileName.contains(".")) {
                code = fileName.substring(0, fileName.lastIndexOf('.'));
            } else {
                return new SimpleStringProperty(fileName);
            }

            Product product = catalogoService.getProductDetails(code);
            String nameToShow = (product != null && product.getName() != null) ? product.getName() : fileName;
            return new SimpleStringProperty(nameToShow);
        });

        colTamaño.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            String fileName = archivo.getName();
            String code = "";
            if (fileName.contains(".")) {
                code = fileName.substring(0, fileName.lastIndexOf('.'));
            } else {
                code = fileName;
            }
            return new ReadOnlyObjectWrapper<>(code);
        });

        colFechaMod.setCellValueFactory(cellData -> {
            long timestamp = cellData.getValue().lastModified();
            Date fecha = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            return new ReadOnlyObjectWrapper<>(sdf.format(fecha));
        });

        // === Configuración Inicial y Listeners ===

        // 1. Establecer valor por defecto en el campo de precio
        paramPrecioPorGramo.setText(String.format(Locale.US, "%.2f", DEFAULT_COSTO_POR_GRAMO));

        // 2. Agregar Listener para recalcular el costo cuando el precio cambie
        paramPrecioPorGramo.textProperty().addListener((obs, oldValue, newValue) -> {
            recalculateCost();
        });

        // 3. Listener para la selección de archivos en la Tabla (Extracción Automática)
        fileTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                handleArchivoSeleccionado(newSelection);
            } else {
                clearParameters();
            }
        });

        // 4. NUEVO Listener para el ComboBox de Filamento (T0, T1, etc.)
        cmbFilamento.valueProperty().addListener((obs, oldProfileEntry, newProfileEntry) -> {
            if (newProfileEntry != null) {
                loadFilamentProfile(newProfileEntry.getValue());
                recalculateCost(); // Recalcular costo basado en el peso del perfil seleccionado
            } else {
                clearFilamentParameters();
            }
        });


        // Carga inicial del TreeView y TableView
        refrescarVistaCatalogo();
    }

    // method auxiliar para cargar archivos de un directorio en la TableView
    private void cargarDetallesCarpeta(File directorio) {
        fileTableView.getItems().clear();
        File[] archivos = directorio.listFiles();
        if (archivos != null) {
            for (File f : archivos) {
                String filename = f.getName().toLowerCase(Locale.ROOT);
                // Filtrar solo archivos con extensiones permitidas y que no sean ocultos
                if (f.isFile() && !f.getName().startsWith(".") &&
                        (filename.endsWith(".stl") || filename.endsWith(".3mf") || filename.endsWith(".gcode"))) {
                    fileTableView.getItems().add(f);
                }
            }
        }
    }

    /**
     * Maneja la selección de un archivo en la tabla.
     * Busca el archivo compañero (.gcode/.3mf) en el mismo directorio y extrae parámetros.
     */
    private void handleArchivoSeleccionado(File selectedFile) {
        // Mostrar el nombre del archivo seleccionado
        lblNombreArchivo.setText(selectedFile.getName());

        // 1. Determinar el nombre base y la extensión del archivo seleccionado
        String fileName = selectedFile.getName();
        String baseName = "";
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            clearParameters();
            return;
        }

        // 2. Determinar la extensión del archivo compañero y buscar
        String companionExtension = "";
        if (extension.toLowerCase(Locale.ROOT).endsWith(".gcode")) {
            companionExtension = ".3mf";
        } else if (extension.toLowerCase(Locale.ROOT).endsWith(".3mf")) {
            companionExtension = ".gcode";
        } else {
            // Si es .stl, se asume que no hay companion de datos relevantes o se busca el .gcode
            companionExtension = ".gcode";
        }

        // 3. Buscar archivos en el mismo directorio
        File parentDir = selectedFile.getParentFile();
        List<File> filesToProcess = new ArrayList<>();
        filesToProcess.add(selectedFile);

        if (!companionExtension.isEmpty()) {
            File companionFile = new File(parentDir, baseName + companionExtension);
            if (companionFile.exists()) {
                filesToProcess.add(companionFile);
            }
        }


        // 4. Extraer y cargar parámetros
        FileParameterExtractor.PrintInfo info = extractor.extract(filesToProcess);
        loadParameters(info);
    }

    /**
     * Carga los parámetros extraídos en los campos de la interfaz.
     * (MODIFICADO para manejar múltiples perfiles en el ComboBox)
     */
    private void loadParameters(FileParameterExtractor.PrintInfo info) {

        // 1. Cargar parámetros generales (no cambian por filamento)
        paramTiempo.setText(info.timeHuman != null ? info.timeHuman : "N/D");
        paramAlturaCapa.setText(info.layerHeight != null ? info.layerHeight : "N/D");

        // 2. Limpiar y llenar el ComboBox de Filamentos
        cmbFilamento.getItems().clear();

        // Convertir el mapa de perfiles a una lista de entradas para el ComboBox
        List<Map.Entry<Integer, FileParameterExtractor.FilamentProfile>> profiles = new ArrayList<>(info.filamentProfiles.entrySet());
        profiles.sort(Map.Entry.comparingByKey()); // Ordenar por Tool ID (T0, T1, T2...)

        cmbFilamento.setItems(FXCollections.observableArrayList(profiles));

        // 3. Seleccionar el primer perfil (o el T0) y cargar sus detalles en los campos específicos
        if (!profiles.isEmpty()) {
            // Intentar seleccionar T0. Si no existe, seleccionar el primero.
            Map.Entry<Integer, FileParameterExtractor.FilamentProfile> initialSelection =
                    profiles.stream().filter(e -> e.getKey() == 0).findFirst().orElse(profiles.get(0));

            cmbFilamento.getSelectionModel().select(initialSelection);

            // La carga de campos específicos y el recálculo de costo se dispara por el listener del cmbFilamento.
        } else {
            clearFilamentParameters();
            recalculateCost();
        }
    }

    /**
     * Carga los parámetros de un perfil de filamento específico en los campos de la interfaz.
     */
    private void loadFilamentProfile(FileParameterExtractor.FilamentProfile profile) {
        if (profile == null) {
            clearFilamentParameters();
            return;
        }

        // Asignación de parámetros específicos del filamento
        paramPeso.setText(profile.filamentAmountG != null ? profile.filamentAmountG.replace(" g", "") : "N/D");
        paramLargo.setText(profile.filamentAmountM != null ? profile.filamentAmountM.replace(" m", "") : "N/D");
        paramTipoFilamento.setText(profile.filamentType != null ? profile.filamentType : "N/D");
        // Nota: paramColorFilamento ya no se usa, la info de color está en el ComboBox.
        paramDensidad.setText(profile.filamentDensity != null ? profile.filamentDensity.replace(" g/cm³", "") : "N/D");
        paramDiametro.setText(profile.filamentDiameter != null ? profile.filamentDiameter.replace(" mm", "") : "N/D");

        // Disparar recálculo basado en el nuevo peso
        recalculateCost();
    }


    /**
     * Calcula y actualiza el costo total basado en el PESO (paramPeso)
     * y el PRECIO POR GRAMO (paramPrecioPorGramo).
     */
    private void recalculateCost() {
        try {
            // 1. Obtener el peso del filamento (en gramos)
            // Usamos 0 si es "N/D" para evitar fallos.
            String pesoStr = paramPeso.getText().replace("N/D", "0").trim();
            double pesoGramos = Double.parseDouble(pesoStr);

            // 2. Obtener el precio por gramo (del campo editable)
            String precioStr = paramPrecioPorGramo.getText().trim();

            // Revertir a default si la entrada está vacía o es inválida
            double precioPorGramo;
            try {
                // Se eliminan caracteres no numéricos excepto el punto, luego se intenta parsear.
                String cleanedPrecioStr = precioStr.replaceAll("[^0-9.]", "");
                if (cleanedPrecioStr.isEmpty() || cleanedPrecioStr.equals(".")) {
                    precioPorGramo = DEFAULT_COSTO_POR_GRAMO;
                } else {
                    precioPorGramo = Double.parseDouble(cleanedPrecioStr);
                }
            } catch (NumberFormatException e) {
                // Si el parsing falla después de la limpieza (ej., doble punto), usar default.
                precioPorGramo = DEFAULT_COSTO_POR_GRAMO;
            }

            // 3. Calcular el costo total
            double costoTotal = pesoGramos * precioPorGramo;

            // 4. Actualizar el campo de costo total
            paramCosto.setText(String.format(Locale.US, "%.2f", costoTotal));

        } catch (NumberFormatException e) {
            // Este catch maneja si paramPeso no es un número (lo cual no debería ocurrir si loadParameters funciona)
            paramCosto.setText("N/D");
        }
    }

    /**
     * Limpia solo los campos específicos del perfil de filamento.
     */
    private void clearFilamentParameters() {
        paramPeso.setText("");
        paramLargo.setText("");
        paramTipoFilamento.setText("");
        // paramColorFilamento.setText(""); // Ya no se usa
        paramDensidad.setText("");
        paramDiametro.setText(""); // Limpiar nuevo campo
    }


    /**
     * Limpia todos los campos de parámetros.
     */
    private void clearParameters() {
        clearFilamentParameters();

        // Limpiar campos generales
        paramTiempo.setText("");
        paramAlturaCapa.setText("");

        // Limpiar ComboBox
        cmbFilamento.getItems().clear();

        // Mantiene el valor en paramPrecioPorGramo
        paramCosto.setText("");
        lblNombreArchivo.setText("(Seleccione un archivo)");
    }

    // method recursivo para construir el TreeView
    private TreeItem<File> createNode(final File f) {
        if (!f.isDirectory() && !f.equals(REPOSITORIO_BASE)) {
            return null;
        }

        TreeItem<File> item = new TreeItem<>(f);
        item.setExpanded(f.equals(REPOSITORIO_BASE));

        File[] children = f.listFiles();
        if (children != null) {
            for (File childFile : children) {
                if (childFile.isDirectory() && !childFile.getName().startsWith(".")) {
                    item.getChildren().add(createNode(childFile));
                }
            }
        }
        return item;
    }

    // method auxiliar recursivo para encontrar un TreeItem por su valor File
    private TreeItem<File> findTreeItem(TreeItem<File> root, File valueToFind) {
        if (root == null || valueToFind == null) {
            return null;
        }
        if (root.getValue() != null && root.getValue().equals(valueToFind)) {
            return root;
        }
        for (TreeItem<File> child : root.getChildren()) {
            TreeItem<File> found = findTreeItem(child, valueToFind);
            if (found != null) {
                return found;
            }
        }
        return null;
    }


    // Manejador del botón AGREGAR
    @FXML
    public void handleAgregarClick() {
        try {
            mostrarModalTipoCarga(currentSelectedDirectory);
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Error", "No se pudo abrir la ventana para agregar archivo: " + e.getMessage());
            System.err.println("Error al cargar la ventana de tipo de carga: " + e.getMessage());
        }
    }

    // Muestra la ventana modal para elegir tipo de carga (Producto o Pieza)
    private void mostrarModalTipoCarga(File directorioProductoSeleccionado) throws IOException {
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/TipoCargaModal.fxml"));
        javafx.scene.Parent root = loader.load();
        TipoCargaController controller = loader.getController();

        controller.setDirectorioProducto(directorioProductoSeleccionado);

        Stage modalStage = new Stage();
        modalStage.setTitle("AGREGAR ARCHIVO AL CATÁLOGO");
        modalStage.initModality(Modality.APPLICATION_MODAL);
        modalStage.setScene(new javafx.scene.Scene(root));
        modalStage.showAndWait();

        if (controller.isCargaExitosa()) {
            refrescarVistaCatalogo();
        }
    }

    // Refresca el TreeView y TableView leyendo del disco
    @FXML
    public void refrescarVistaCatalogo() {
        System.out.println("Refrescando la vista del catálogo...");

        File previouslySelectedDirectory = null;
        TreeItem<File> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            previouslySelectedDirectory = selectedItem.getValue();
        }

        if (REPOSITORIO_BASE.exists() && REPOSITORIO_BASE.isDirectory()) {
            TreeItem<File> rootItem = createNode(REPOSITORIO_BASE);
            folderTreeView.setRoot(rootItem);
            folderTreeView.setShowRoot(false);

            // Intentar re-seleccionar y expandir
            TreeItem<File> itemToSelect = findTreeItem(rootItem, previouslySelectedDirectory);
            if (itemToSelect != null) {
                folderTreeView.getSelectionModel().select(itemToSelect);
                TreeItem<File> parent = itemToSelect.getParent();
                while(parent != null && !parent.equals(rootItem)){
                    parent.setExpanded(true);
                    parent = parent.getParent();
                }
                itemToSelect.setExpanded(true);
                if (!itemToSelect.getValue().equals(REPOSITORIO_BASE)) {
                    cargarDetallesCarpeta(itemToSelect.getValue());
                } else {
                    cargarDetallesCarpeta(REPOSITORIO_BASE);
                }

            } else {
                folderTreeView.getSelectionModel().select(rootItem);
                rootItem.setExpanded(true);
                cargarDetallesCarpeta(REPOSITORIO_BASE);
            }

            System.out.println("✅ Vista de Repositorio refrescada.");
        } else {
            showAlert(AlertType.ERROR, "Error de Repositorio", "El directorio base del repositorio no existe o no es accesible en: " + REPOSITORIO_BASE.getAbsolutePath());
            folderTreeView.setRoot(null);
            fileTableView.getItems().clear();
        }
    }

    // Manejador del botón ELIMINAR
    @FXML
    public void handleEliminarClick() {
        // Prioridad 1: Selección en la Tabla (eliminar pieza)
        File selectedPieceFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedPieceFile != null) {
            showDeletePieceConfirmation(selectedPieceFile);
            return;
        }

        // Prioridad 2: Selección en el Árbol (eliminar producto/carpeta)
        TreeItem<File> selectedTreeItem = folderTreeView.getSelectionModel().getSelectedItem();
        File selectedDirectory = (selectedTreeItem != null) ? selectedTreeItem.getValue() : null;
        if (selectedDirectory != null && selectedDirectory.isDirectory() && !selectedDirectory.equals(REPOSITORIO_BASE)) {
            showDeleteProductConfirmation(selectedDirectory);
        } else {
            showAlert(AlertType.WARNING, "Selección Requerida", "Seleccione una pieza en la tabla o una carpeta de producto en el árbol para eliminar.");
        }
    }

    // Muestra confirmación para eliminar Pieza
    private void showDeletePieceConfirmation(File pieceFile) {
        String code = pieceFile.getName();
        if (code.contains(".")) {
            code = code.substring(0, code.lastIndexOf('.'));
        }

        Optional<ButtonType> result = showConfirmationAlert("Confirmar Eliminación de Pieza",
                "¿Está seguro de eliminar la pieza con código '" + code + "'?\nSe eliminará el archivo y su registro en la base de datos.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                File parentDir = pieceFile.getParentFile();
                catalogoService.deletePiece(pieceFile);
                refrescarVistaCatalogo();
                TreeItem<File> parentItem = findTreeItem(folderTreeView.getRoot(), parentDir);
                if (parentItem != null) {
                    folderTreeView.getSelectionModel().select(parentItem);
                    cargarDetallesCarpeta(parentDir);
                } else {
                    fileTableView.getItems().clear();
                    folderTreeView.getSelectionModel().clearSelection();
                }

            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error al Eliminar", "No se pudo eliminar la pieza: " + e.getMessage());
            }
        }
    }

    // Muestra confirmación para eliminar Producto (carpeta)
    private void showDeleteProductConfirmation(File productDirectory) {
        Optional<ButtonType> result = showConfirmationAlert("Confirmar Eliminación de Producto",
                "¡ATENCIÓN!\n¿Está seguro de eliminar el producto '" + productDirectory.getName() + "'?\nSe eliminará la carpeta completa, todos sus archivos y todos los registros asociados en la base de datos.");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                catalogoService.deleteProduct(productDirectory);
                refrescarVistaCatalogo();
            } catch (IOException e) {
                showAlert(AlertType.ERROR, "Error al Eliminar", "No se pudo eliminar el producto: " + e.getMessage());
            }
        }
    }

    // method auxiliar genérico para mostrar alertas de confirmación
    private Optional<ButtonType> showConfirmationAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("hubi-dialog");
        return alert.showAndWait();
    }

    // method auxiliar genérico para mostrar alertas informativas/error
    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("hubi-dialog");
        alert.showAndWait();
    }
}
package com.calmasalud.hubi.ui.controller;


import com.calmasalud.hubi.core.service.CatalogService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CatalogManagerController {

    // --- Inyección de Servicios ---
    private final CatalogService catalogoService = new CatalogService();

    // --- Panel Izquierdo (Explorador) ---
    @FXML
    private TextField txtBusqueda;
    @FXML
    private Button btnActualizar;

    // --- Componentes del explorador actualizados ---
    @FXML
    private TreeView<File> folderTreeView;
    @FXML
    private TableView<File> fileTableView;
    @FXML
    private TableColumn<File, String> colNombre;
    @FXML
    private TableColumn<File, String> colTamaño;
    @FXML
    private TableColumn<File, String> colFechaMod;
    // --- Fin de componentes actualizados ---

    @FXML
    private Button btnAgregar;
    @FXML
    private Button btnEliminar;
    @FXML
    private Button btnExtraer;

    // --- Panel Derecho (Parámetros) ---
    @FXML
    private Label lblNombreArchivo;
    @FXML
    private StackPane visor3DPlaceholder;
    @FXML
    private TextField paramPeso;
    @FXML
    private TextField paramLargo;
    @FXML
    private TextField paramTipoFilamento;
    @FXML
    private TextField paramTemperatura;
    @FXML
    private TextField paramVelocidad;
    @FXML
    private TextField paramTiempo;
    @FXML
    private TextField paramDensidad;
    @FXML
    private TextField paramAlturaCapa;
    @FXML
    private TextField paramCosto;
    private static final File REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos").toFile();

    @FXML
    public void initialize() {
        System.out.println("Controlador de Catálogo (v2.1 con Tree/Table) inicializado.");

        // (Lógica futura):
        // 1. Cargar el árbol de carpetas en 'folderTreeView'
        // 2. Añadir un listener a 'folderTreeView.getSelectionModel()'
        // 3. Cuando se seleccione una carpeta, poblar 'fileTableView'
        //    con los archivos de esa carpeta.
        // Se ejecuta al cargar la vista
        folderTreeView.setCellFactory(new Callback<TreeView<File>, TreeCell<File>>() {
            @Override
            public TreeCell<File> call(TreeView<File> param) {
                return new TreeCell<File>() {
                    @Override
                    protected void updateItem(File item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null) {
                            setText(null);
                        } else if (item.equals(REPOSITORIO_BASE)) {
                            // Etiqueta para el nodo raíz
                            setText("Repositorio Master");
                        } else {
                            // CLAVE: Usa item.getName() para mostrar solo el nombre (el archivo o carpeta)
                            setText(item.getName());
                        }
                    }
                };
            }
        });

        // 2. LISTENER DEL TREEVIEW
        folderTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                File selectedFile = newValue.getValue();

                if (selectedFile != null && selectedFile.isDirectory()) {
                    cargarDetallesCarpeta(selectedFile);
                } else {
                    fileTableView.getItems().clear();
                }
            }
        });

        // 3. INICIALIZACIÓN DE COLUMNAS DE LA TABLEVIEW
        colNombre.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colTamaño.setCellValueFactory(cellData -> {
            long bytes = cellData.getValue().length();
            String tamanoFormateado = formatSize(bytes);
            return new ReadOnlyObjectWrapper<>(tamanoFormateado);
        });
        colFechaMod.setCellValueFactory(cellData -> {
            long timestamp = cellData.getValue().lastModified();
            Date fecha = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            return new ReadOnlyObjectWrapper<>(sdf.format(fecha));
        });

        // 4. CARGA INICIAL DE DATOS
        refrescarVistaCatalogo();
    }
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    private void cargarDetallesCarpeta(File directorio) {
        fileTableView.getItems().clear(); // Limpia la tabla antes de cargar

        File[] archivos = directorio.listFiles();

        if (archivos != null) {

            for (File f : archivos) {
                // Puedes filtrar por extensión aquí si solo quieres archivos .stl/.3mf en la tabla
                if (f.isFile() && (f.getName().endsWith(".stl") || f.getName().endsWith(".3mf"))) {
                    fileTableView.getItems().add(f);
                }
                // Si el archivo es una subcarpeta, la ignoramos para esta tabla
            }
        }
        // Si la tabla queda vacía, el mensaje "Tabla sin contenido" se mostrará automáticamente
    }
    /**
     * Implementa HU1 y RF1: Carga de archivos .stl y .3mf
     *
     */
    @FXML
    // Este es el método vinculado al botón "AGREGAR"
    public void handleAgregarClick(){

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Archivos de Diseño/Impresión (.stl, .3mf)");

        // Agregar los filtros de extensión (RF1) - Tomado del código anterior
        fileChooser.getExtensionFilters().addAll(
                // Filtro general (para comodidad del usuario)
                new FileChooser.ExtensionFilter("Modelos 3D (.stl, .3mf)", "*.stl", "*.3mf"),

                // Filtros específicos (opcionales, pero útiles para la interfaz del SO)
                new FileChooser.ExtensionFilter("Archivos STL", "*.stl"),
                new FileChooser.ExtensionFilter("Archivos 3MF", "*.3mf")
        );

        File archivoSeleccionado = fileChooser.showOpenDialog(null);

        if (archivoSeleccionado != null) {
            try {
                // Lanza la nueva ventana, pasando el archivo seleccionado.
                mostrarModalTipoCarga(archivoSeleccionado);

            } catch (IOException e) {
                System.err.println("Error al cargar la ventana de tipo de carga: " + e.getMessage());
            }
        }
    }
    // Método para lanzar la ventana modal
    private void mostrarModalTipoCarga(File archivo) throws IOException {
        // Carga el FXML de la ventana modal
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/TipoCargaModal.fxml"));
        Parent root = loader.load();

        // Obtiene el controlador de la ventana modal
        TipoCargaController controller = loader.getController();

        // Pasa el archivo seleccionado al controlador de la modal
        controller.setArchivoSeleccionado(archivo);

        // Configura y muestra la ventana modal
        Stage modalStage = new Stage();
        modalStage.setTitle("AGREGAR ARCHIVO");
        modalStage.initModality(Modality.APPLICATION_MODAL); // Bloquea la ventana principal
        modalStage.setScene(new Scene(root));
        modalStage.showAndWait(); // Espera a que el usuario cierre la modal
        if (controller.isCargaExitosa()) {
            refrescarVistaCatalogo(); // Llama al método de actualización si hubo éxito
        }
    }
    public void refrescarVistaCatalogo() {
        System.out.println("✅ El Repositorio Master ha cambiado. Refrescando la vista del catálogo...");
        if (REPOSITORIO_BASE.exists()) {
            // Crea el elemento raíz del TreeView
            TreeItem<File> rootItem = createNode(REPOSITORIO_BASE);

            // La raíz del TreeView no es visible para una mejor apariencia
            folderTreeView.setShowRoot(false);
            folderTreeView.setRoot(rootItem);
            System.out.println("✅ Vista de Repositorio refrescada.");
        } else {
            // Manejar el caso donde el repositorio aún no se ha creado
            System.out.println("El Repositorio Master aún no existe. Se creará en la primera carga.");
        }

    }
    private TreeItem<File> createNode(final File f) {
        if (!f.isDirectory() && !f.equals(REPOSITORIO_BASE)) {
            return null; // No crea un nodo para archivos
        }
        // El TreeItem contiene el objeto File ORIGINAL (f), la CellFactory se encarga de la visualización.
        TreeItem<File> root = new TreeItem<>(f);

        if (f.isDirectory()) {
            File[] children = f.listFiles();

            if (children != null && children.length > 0) {
                // Revisa si hay subdirectorios o archivos válidos dentro.
                boolean hasVisibleChildren = false;
                for (File childFile : children) {
                    // Si hay al menos un directorio hijo, o un archivo que queremos ver en la tabla

                    if (!childFile.getName().startsWith(".")) {
                        hasVisibleChildren = true;
                        break;
                    }
                }

                if (hasVisibleChildren) {
                    // Nodo 'dummy' para indicar que se puede expandir
                    root.getChildren().add(new TreeItem<>(null));
                }
            }

            root.setExpanded(false);
            root.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                if (isExpanded && root.getChildren().get(0) != null && root.getChildren().get(0).getValue() == null) {

                    root.getChildren().clear(); // Quitar el nodo dummy

                    // Iterar sobre los archivos reales (usando la ruta completa 'f')
                    for (File childFile : f.listFiles()) {
                        if (!childFile.getName().startsWith(".")) {

                            // Si el createNode() devuelve null, no se agrega.
                            TreeItem<File> childNode = createNode(childFile);
                            if (childNode != null) {
                                root.getChildren().add(childNode);
                            }
                        }
                    }
                }
            });
        }
        return root;
    }
    // (Aquí irán los handlers para btnEliminar y btnExtraer)
}
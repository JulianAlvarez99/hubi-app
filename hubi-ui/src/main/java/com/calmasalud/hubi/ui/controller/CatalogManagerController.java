package com.calmasalud.hubi.ui.controller;



import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
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
import javafx.application.Platform;


public class CatalogManagerController {

    // --- Inyección de Servicios ---
    //private final CatalogService catalogoService = new CatalogService();
    // 1. Instanciar la implementación CONCRETA (desde hubi-persistance)
    private final IProductRepository productSqliteRepository = new ProductRepositorySQLite();

    // 2. Crear el servicio de catálogo, inyectando la implementación
    private final CatalogService catalogoService = new CatalogService(productSqliteRepository);
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



        // --- COLUMNA NOMBRE: Ahora muestra el Name de la entidad Product ---
        colNombre.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            // 1. Obtener el código único del nombre del archivo (sin extensión)
            String fileName = archivo.getName();
            String code = fileName.substring(0, fileName.lastIndexOf('.'));

            // 2. Buscar el producto en la BD por el código único
            Product product = catalogoService.getProductDetails(code);

            // 3. Devolver el Nombre de la entidad o el nombre del archivo como fallback
            String nameToShow = (product != null && product.getName() != null) ? product.getName() : fileName;

            return new SimpleStringProperty(nameToShow);
        });


        // --- COLUMNA TAMAÑO: Ahora muestra el CÓDIGO ÚNICO (RF8) ---
        colTamaño.setCellValueFactory(cellData -> {
            File archivo = cellData.getValue();
            // El código único es el nombre del archivo menos la extensión
            String code = archivo.getName().substring(0, archivo.getName().lastIndexOf('.'));
            return new ReadOnlyObjectWrapper<>(code);
        });

        // --- COLUMNA FECHA MODIFICACIÓN (Sin cambios) ---
        colFechaMod.setCellValueFactory(cellData -> {
            long timestamp = cellData.getValue().lastModified();
            Date fecha = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            return new ReadOnlyObjectWrapper<>(sdf.format(fecha));
        });

        // 4. CARGA INICIAL DE DATOS
        refrescarVistaCatalogo();
    }

    // El metodo formatSize ya no se usa para colTamaño, pero se mantiene por si se usa en otro lugar.
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = ("KMGTPE").charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void cargarDetallesCarpeta(File directorio) {
        fileTableView.getItems().clear();
        File[] archivos = directorio.listFiles();

        if (archivos != null) {
            for (File f : archivos) {

                String filename = f.getName().toLowerCase();
                if (f.isFile() && (filename.endsWith(".stl") || filename.endsWith(".3mf") || filename.endsWith(".gcode"))) {
                    fileTableView.getItems().add(f);
                }
            }
        }
    }

    /**
     * Implementa HU1 y RF1: Carga de archivos .stl y .3mf
     *
     */
    @FXML
    // Este es el metodo está vinculado al botón AGREGAR
    public void handleAgregarClick(){

        try {
            //Abre el modal inmediatamente
            mostrarModalTipoCarga(null);

        } catch (IOException e) {
            System.err.println("Error al cargar la ventana de tipo de carga: " + e.getMessage());
        }
    }

    // Metodo para lanzar la ventana modal
    private void mostrarModalTipoCarga(File archivo) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/TipoCargaModal.fxml"));
        Parent root = loader.load();

        TipoCargaController controller = loader.getController();
        controller.setCatalogoService(this.catalogoService);
        Stage modalStage = new Stage();
        modalStage.setTitle("AGREGAR ARCHIVO");
        modalStage.initModality(Modality.APPLICATION_MODAL);
        modalStage.setScene(new Scene(root));
        modalStage.showAndWait();

        if (controller.isCargaExitosa()) {
            refrescarVistaCatalogo(); // Llama al método de actualización inicial (lectura de disco)

        }
    }

    public void refrescarVistaCatalogo() {
        System.out.println("✅ El Repositorio Master ha cambiado. Refrescando la vista del catálogo...");

        //Guardar la carpeta previamente seleccionada.
        File previouslySelectedDirectory = null;
        TreeItem<File> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            previouslySelectedDirectory = selectedItem.getValue();
        }

        if (REPOSITORIO_BASE.exists()) {
            // 2. Reconstruir todo el TreeView.
            TreeItem<File> rootItem = createNode(REPOSITORIO_BASE);
            folderTreeView.setShowRoot(false);
            folderTreeView.setRoot(rootItem);

            // 3. CLAVE: Intentar restablecer la selección.
            if (previouslySelectedDirectory != null && previouslySelectedDirectory.exists()) {

                if (previouslySelectedDirectory.equals(REPOSITORIO_BASE)) {
                    folderTreeView.getSelectionModel().select(rootItem);
                    rootItem.setExpanded(true);
                }
                // Si el directorio previo era un subdirectorio (Producto):
                else {
                    // Seleccionamos la raíz y forzamos la recarga de la carpeta.
                    // Esto es un compromiso: puede que no seleccione el nodo exacto,
                    // pero dispara la actualización de la tabla.
                    folderTreeView.getSelectionModel().select(rootItem);
                    rootItem.setExpanded(true);

                    // Forzar la carga de la tabla con la ruta de la carpeta del producto (la más reciente).
                    cargarDetallesCarpeta(previouslySelectedDirectory);
                }
            }

            // 4. Si no se pudo restablecer la selección o no había nada seleccionado,
            // simplemente seleccionamos la raíz y recargamos la tabla.
            if (folderTreeView.getSelectionModel().getSelectedItem() == null) {
                folderTreeView.getSelectionModel().select(rootItem);
                rootItem.setExpanded(true);
                // Si no hay selección, aseguramos que la tabla cargue la raíz
                cargarDetallesCarpeta(REPOSITORIO_BASE);
            }

            System.out.println("✅ Vista de Repositorio refrescada.");
        } else {
            // ... (Lógica de error) ...
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
    public void handleEliminarClick() {
        File objetoAEliminar = null;

        //Intentar obtener el archivo seleccionado de la TABLA (Pieza)
        objetoAEliminar = fileTableView.getSelectionModel().getSelectedItem();

        if (objetoAEliminar == null) {
            //Si no hay Pieza seleccionada, intentar obtener la carpeta del ÁRBOL (Producto)
            TreeItem<File> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                objetoAEliminar = selectedItem.getValue();
            }
        }

        if (objetoAEliminar != null && objetoAEliminar.exists()) {
            try {
                // No se permite eliminar el Repositorio Master
                if (objetoAEliminar.equals(REPOSITORIO_BASE)) {
                    mostrarAlerta(Alert.AlertType.WARNING, "Error de Eliminación", "No se puede eliminar la carpeta raíz del repositorio.");
                    return;
                }

                // Si es un directorio (Producto), preguntar si está seguro
                String tipoObjeto = objetoAEliminar.isDirectory() ? "Producto" : "Archivo";

                // Muestra la ventana modal de confirmación
                boolean confirmacion = mostrarModalConfirmacion(objetoAEliminar);

                if (confirmacion) {
                    // Ejecutar la eliminación a través del servicio
                    catalogoService.eliminarObjeto(objetoAEliminar);

                    // Mostrar un mensaje de éxito
                    mostrarAlerta(Alert.AlertType.INFORMATION, "Eliminación Exitosa",
                            tipoObjeto + " '" + objetoAEliminar.getName() + "' eliminado con éxito.");


                    folderTreeView.getSelectionModel().clearSelection();
                    fileTableView.getItems().clear();

                    refrescarVistaCatalogo();
                } else {
                    mostrarAlerta(Alert.AlertType.INFORMATION, "Operación Cancelada", "La eliminación ha sido cancelada.");
                }
            } catch (IOException e) {
                System.err.println("Error al procesar la eliminación: " + e.getMessage());
                mostrarAlerta(Alert.AlertType.ERROR, "Error de Sistema", "No se pudo eliminar el objeto: " + e.getMessage());
            }
        } else {
            mostrarAlerta(Alert.AlertType.WARNING, "Objeto No Seleccionado", "Por favor, seleccione un Producto (en el árbol) o un Archivo (en la tabla) para eliminar.");
        }
    }


    private void mostrarAlerta(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    private boolean mostrarModalConfirmacion(File objetoAEliminar) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/ConfirmacionModal.fxml"));
        Parent root = loader.load();

        ConfirmacionController controller = loader.getController();

        // Configura el mensaje
        String mensaje = "¿Desea eliminar " + objetoAEliminar.getName() + " de manera permanente? Esta acción no se puede deshacer.";
        controller.setMensaje(mensaje);

        Stage modalStage = new Stage();
        modalStage.setTitle("CONFIRMACIÓN DE ELIMINACIÓN");
        modalStage.initModality(Modality.APPLICATION_MODAL);
        modalStage.setScene(new Scene(root));
        modalStage.showAndWait();

        return controller.isConfirmado();
    }
}
package com.calmasalud.hubi.ui.controller;


import com.calmasalud.hubi.core.service.CatalogService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert; // Importar Alert
import javafx.scene.control.Alert.AlertType; // Importar AlertType
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.scene.control.Label;
import java.io.File;
import java.io.IOException;

public class TipoCargaController {
    private File archivoSeleccionado;

    // CAMPOS FXML
    @FXML private RadioButton radioProducto;
    @FXML private RadioButton radioPieza;
    @FXML private TextField txtNombre;
    @FXML private Button btnBuscarDirectorio;

    // NUEVO FXML: Botón para iniciar el diálogo de selección de archivo
    @FXML private Button btnSeleccionarArchivo;
    // NUEVO FXML: Etiqueta para mostrar el nombre del archivo seleccionado
    @FXML private Label lblArchivoSeleccionado;

    private File directorioProducto;
    private final CatalogService catalogoService = new CatalogService();


    // NUEVO: Atributo para recibir el directorio seleccionado del Gestor de Catálogo

    private static final String REPOSITORIO_BASE_PATH =
            System.getProperty("user.home") + File.separator + "SistemaHUBI" + File.separator + "RepositorioArchivos";
    private boolean cargaExitosa = false;

    public void setArchivoSeleccionado(File archivo) {
        this.archivoSeleccionado = archivo;

    }

    // Setter para recibir la selección del TreeView (puede ser null)
    public void setDirectorioProducto(File directorioProducto) {
        this.directorioProducto = directorioProducto;
    }

    public boolean isCargaExitosa() {
        return cargaExitosa;
    }

    @FXML
    public void initialize() {
        // Se establece el estado inicial: Producto (nuevo)
        radioProducto.setSelected(true);
        // Delay para asegurar que el setter directorioProducto haya sido llamado antes de la lógica
        javafx.application.Platform.runLater(() -> actualizarEstadoCampos(radioProducto.isSelected()));

        // Se añade un  Listener para cambiar el estado al hacer clic en los RadioButtons
        radioProducto.selectedProperty().addListener((obs, oldValue, isProducto) -> {
            actualizarEstadoCampos(isProducto);
        });
    }
    @FXML
    private void handleSeleccionarArchivo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Archivo de Diseño/Impresión (.stl, .3mf)");

        // Agregar los filtros de extensión (RF1)
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Modelos 3D y GCode", "*.stl", "*.3mf", "*.gcode"),
                new FileChooser.ExtensionFilter("Archivos STL", "*.stl"),
                new FileChooser.ExtensionFilter("Archivos 3MF", "*.3mf"),
                new FileChooser.ExtensionFilter("Archivos GCode", "*.gcode")
        );

        File selected = fileChooser.showOpenDialog(null);

        if (selected != null) {
            this.archivoSeleccionado = selected;
            lblArchivoSeleccionado.setText("Archivo: " + selected.getName()); // Mostrar al usuario
        }
    }

    /**
     * Función auxiliar para mostrar un cuadro de alerta.
     */
    private void mostrarAlerta(AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    private void actualizarEstadoCampos(boolean esProducto) {
        // 1. Reseteo de campos comunes
        txtNombre.clear();
        btnBuscarDirectorio.setVisible(false); // Siempre oculto en este flujo
        txtNombre.setDisable(false);
        txtNombre.setEditable(true);

        if (esProducto) {
            // MODO PRODUCTO (Nuevo Nombre): SOLO SE PUEDE ESCRIBIR EL NOMBRE
            txtNombre.setPromptText("Ingrese el nombre del nuevo producto (ej: SOPORTE)");

        } else {
            // MODO PIEZA (Asociado a Producto Existente)
            txtNombre.setEditable(false);
            txtNombre.setDisable(true);

            if (directorioProducto != null && directorioProducto.isDirectory()) {
                // Caso A: Producto SELECCIONADO -> pre-llenar
                txtNombre.setText(directorioProducto.getAbsolutePath());
                txtNombre.setPromptText("PIEZA: Asociada a " + directorioProducto.getName());

            } else {
                // Caso B: Producto NO SELECCIONADO -> Bloquear y mostrar requerimiento
                txtNombre.setText("");
                txtNombre.setPromptText("PIEZA: SELECCIONE un Producto en el Catálogo.");
            }
        }
    }

    // Se mantiene onBuscarProductoClicked para la compatibilidad del FXML si el botón se reusa.
    @FXML
    public void onBuscarProductoClicked(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Seleccionar Directorio de Producto Existente");

        // Se configura la carpeta inicial del diálogo al Repositorio Master
        File initialDirectory = new File(REPOSITORIO_BASE_PATH);
        if (initialDirectory.exists()) {
            dirChooser.setInitialDirectory(initialDirectory);
        }

        File directorioSeleccionado = dirChooser.showDialog(null);

        if (directorioSeleccionado != null) {
            // Verificación de seguridad: solo puede seleccionar directorios dentro del Repositorio Master de HUBI
            if (directorioSeleccionado.getAbsolutePath().startsWith(REPOSITORIO_BASE_PATH)) {
                // Coloca la RUTA COMPLETA en el campo de texto.
                txtNombre.setText(directorioSeleccionado.getAbsolutePath());
            } else {
                txtNombre.clear();
            }
        }
    }

    @FXML
    public void onCargarClicked(ActionEvent event) {

        String valorIngresado = txtNombre.getText();
        boolean esPieza = radioPieza.isSelected();

        // MODIFICADO: 1. VALIDACIÓN GENERAL: Asegurar que se haya seleccionado un archivo AHORA
        if (this.archivoSeleccionado == null) {
            mostrarAlerta(AlertType.ERROR, "Error de Validación", "Debe seleccionar un archivo de diseño antes de continuar.");
            return;
        }

        // 2. VALIDACIÓN ESPECÍFICA POR MODO
        if (esPieza) {
            // REGLA DE NEGOCIO: Piezas deben tener Producto seleccionado
            if (directorioProducto == null || !directorioProducto.isDirectory()) {
                mostrarAlerta(AlertType.ERROR, "Error de Asociación (RF8)",
                        "Para cargar una PIEZA, debe seleccionar previamente la carpeta del PRODUCTO al que pertenece en el 'Repositorio Master'.");
                return; // Detiene el proceso y muestra la alerta.
            }
            // Para PIEZA, valorIngresado es la ruta absoluta del directorioProducto,
            // no necesitamos validar el texto ya que fue rellenado automáticamente.

        } else {
            // MODO PRODUCTO: Solo se necesita el nombre ingresado por el usuario.
            if (valorIngresado == null || valorIngresado.trim().isEmpty()) {
                mostrarAlerta(AlertType.ERROR, "Error de Validación",
                        "Debe ingresar un NOMBRE para el nuevo Producto.");
                return;
            }
        }

        // Si la validación pasa, ejecutamos la lógica de negocio
        try {
            if (esPieza) {
                // Caso 1: Carga como PIEZA
                catalogoService.procesarCargaPieza(this.archivoSeleccionado, valorIngresado);
            } else {
                // Caso 2: Carga como PRODUCTO
                catalogoService.procesarCargaProducto(this.archivoSeleccionado, valorIngresado);
            }

            this.cargaExitosa = true;
            // Cerrar la ventana modal al finalizar el proceso
            ((Node)(event.getSource())).getScene().getWindow().hide();

        } catch (IllegalArgumentException e) {
            mostrarAlerta(AlertType.ERROR, "Error de Lógica (RF8)", e.getMessage());
            this.cargaExitosa = false;
        } catch (IOException e) {
            mostrarAlerta(AlertType.ERROR, "Error de Archivo/Persistencia", e.getMessage());
            this.cargaExitosa = false;
        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
            mostrarAlerta(AlertType.ERROR, "Error Inesperado", "Ocurrió un error: " + e.getMessage());
            this.cargaExitosa = false;
        }
    }

    // Método enlazado al botón "CANCELAR"
    @FXML
    public void onCancelarClicked(ActionEvent event) {
        this.cargaExitosa = false;
        // Cierra la ventana modal sin procesar la carga
        ((Node)(event.getSource())).getScene().getWindow().hide();
    }
}
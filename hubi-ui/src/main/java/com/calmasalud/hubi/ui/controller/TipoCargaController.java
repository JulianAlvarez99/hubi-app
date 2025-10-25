package com.calmasalud.hubi.ui.controller;


import com.calmasalud.hubi.core.service.CatalogService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;

public class TipoCargaController {
    private File archivoSeleccionado;
    // Campos FXML que deben coincidir con  TipoCargaModal.fxml
    @FXML
    private RadioButton radioProducto;
    @FXML
    private RadioButton radioPieza;
    @FXML
    private TextField txtNombre;
    @FXML
    private Button btnBuscarDirectorio;
    @FXML
    private Label lblNombreArchivo;
    @FXML
    private Button btnBuscarArchivo;
    @FXML
    private VBox fileSearchContainer; // Contenedor que agrupa lblNombreArchivo y btnBuscarArchivo
    //private CatalogService catalogoService = new CatalogService();
    private CatalogService catalogoService;
    private static String REPOSITORIO_BASE_PATH =
            System.getProperty("user.home") + File.separator + "SistemaHUBI" + File.separator + "RepositorioArchivos";
    private boolean cargaExitosa = false;

    public void setArchivoSeleccionado(File archivo) {
        this.archivoSeleccionado = archivo;

    }
    public void setCatalogoService(CatalogService service) {
        this.catalogoService = service;
    }
    public boolean isCargaExitosa() {
        return cargaExitosa;
    }

    public void initialize() {
        // Agregar Listener al radioProducto: Se activa cuando pasa de FALSE a TRUE.
        radioProducto.selectedProperty().addListener((obs, oldValue, isProducto) -> {
            // Esta lógica se dispara cuando PRODUCTO es seleccionado (isProducto=true) O deseleccionado (isProducto=false)
            if (isProducto) {
                actualizarEstadoCampos(true); // Activar modo PRODUCTO
                resetearSeleccionArchivo();
            }
        });

        // Agregar Listener al radioPieza: Esto asegura que el cambio de estado se detecte.
        radioPieza.selectedProperty().addListener((obs, oldValue, isPieza) -> {
            // Esta lógica se dispara cuando PIEZA es seleccionado.
            if (isPieza) {
                actualizarEstadoCampos(false); // Activar modo PIEZA
                resetearSeleccionArchivo();
            }
        });

        // Aplicar el estado inicial
        // Al usar setSelected(true), se dispara el listener de radioProducto
        radioProducto.setSelected(true);

    }

    private void resetearSeleccionArchivo() {
        this.archivoSeleccionado = null;
        lblNombreArchivo.setText("Ningún archivo seleccionado.");
    }

    private void actualizarEstadoCampos(boolean esProducto) {
        // Se limpia el campo de texto al cambiar de opción.
        txtNombre.clear();

        if (esProducto) {
            // MODO PRODUCTO (Nuevo Nombre)
            txtNombre.setPromptText("Ingrese el nombre del nuevo producto (ej: SOPORTE)");
            txtNombre.setEditable(true);
            btnBuscarDirectorio.setVisible(false);
        } else {
            // MODO PIEZA (Producto Existente)
            txtNombre.setPromptText("Seleccione el directorio del producto existente con el botón '...'");
            txtNombre.setEditable(false);
            btnBuscarDirectorio.setVisible(true);
        }
    }

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

    public void handleBuscarArchivoClicked(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Archivo de Diseño/Impresión (.stl, .3mf)");

        // Configura los filtros (los que tenías en handleAgregarClick)
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Modelos 3D (.stl, .3mf)", "*.stl", "*.3mf"),
                new FileChooser.ExtensionFilter("Archivos STL", "*.stl"),
                new FileChooser.ExtensionFilter("Archivos 3MF", "*.3mf")
        );

        File selectedFile = fileChooser.showOpenDialog(btnBuscarArchivo.getScene().getWindow());

        if (selectedFile != null) {
            this.archivoSeleccionado = selectedFile;
            lblNombreArchivo.setText(selectedFile.getName());

            if (radioProducto.isSelected()) {
                txtNombre.setText(selectedFile.getName().split("\\.")[0]);
            }
        }
        // Adicionalmente, después de seleccionar el archivo:
        if (this.archivoSeleccionado != null) {
            // Bloquea los Radio Buttons para asegurar que no cambie de modo a mitad de carga
            radioProducto.setDisable(true);
            radioPieza.setDisable(true);
        }
    }

    @FXML
    public void onCargarClicked(ActionEvent event) {

        String valorIngresado = txtNombre.getText();
        boolean esPieza = radioPieza.isSelected();

        //Validar que el archivo HAYA SIDO SELECCIONADO en el modal.
        if (archivoSeleccionado == null) {

            return;
        }

        try {
            if (esPieza) {
                // **CARGA COMO PIEZA:** valorIngresado es la RUTA COMPLETA del directorio existente.
                catalogoService.procesarCargaPieza(archivoSeleccionado, valorIngresado);


            } else {
                // **CARGA COMO PRODUCTO:** valorIngresado es el NOMBRE del nuevo producto.
                catalogoService.procesarCargaProducto(archivoSeleccionado, valorIngresado);

            }
            this.cargaExitosa = true;
            // Cerrar la ventana modal al finalizar el proceso
            ((Node) (event.getSource())).getScene().getWindow().hide();

        } catch (IOException e) {
            System.err.println("Error de Carga: " + e.getMessage());
            this.cargaExitosa = false;
        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
            this.cargaExitosa = false;
        }
    }

    // Metodo vinculado al botón "CANCELAR"
    @FXML
    public void onCancelarClicked(ActionEvent event) {
        this.cargaExitosa = false;
        // Cierra la ventana modal sin procesar la carga
        ((Node) (event.getSource())).getScene().getWindow().hide();
    }
}
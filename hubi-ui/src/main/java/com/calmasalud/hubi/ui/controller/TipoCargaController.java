package com.calmasalud.hubi.ui.controller;


import com.calmasalud.hubi.core.service.CatalogService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

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
    private final CatalogService catalogoService = new CatalogService();
    private static final String REPOSITORIO_BASE_PATH =
            System.getProperty("user.home") + File.separator + "SistemaHUBI" + File.separator + "RepositorioArchivos";
    private boolean cargaExitosa = false;
    public void setArchivoSeleccionado(File archivo) {
        this.archivoSeleccionado = archivo;

    }
    public boolean isCargaExitosa() {
        return cargaExitosa;
    }
    public void initialize() {
        // Se establece el estado inicial: Producto (nuevo)
        radioProducto.setSelected(true); // Fuerza el estado a Producto
        actualizarEstadoCampos(radioProducto.isSelected());

        // Se añade un  Listener para cambiar el estado al hacer clic en los RadioButtons
        radioProducto.selectedProperty().addListener((obs, oldValue, isProducto) -> {
            actualizarEstadoCampos(isProducto);
        });
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
    @FXML
    public void onCargarClicked(ActionEvent event) {

        String valorIngresado = txtNombre.getText();
        boolean esPieza = radioPieza.isSelected();

        // Validación de campos obligatorios
        if (archivoSeleccionado == null || valorIngresado == null || valorIngresado.trim().isEmpty()) {

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
            ((Node)(event.getSource())).getScene().getWindow().hide();

        } catch (IOException e) {
            System.err.println("Error de Carga: " + e.getMessage());
            this.cargaExitosa = false;
        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
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
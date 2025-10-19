package com.calmasalud.hubi.ui.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    // 1. Inyecta el StackPane del FXML
    @FXML
    private StackPane mainContentArea;

    // (Aquí puedes inyectar los botones si necesitas deshabilitarlos, etc.)
    // @FXML private Button btnCatalogo;

    /**
     * Este método se llama automáticamente DESPUÉS de que se carga el FXML.
     * Es perfecto para cargar la vista por defecto (el catálogo).
     */
    @FXML
    public void initialize() {
        // Carga la vista de catálogo por defecto al iniciar
        handleMenuCatalogo(null);
    }

    // 2. Métodos 'onAction' de los botones

    @FXML
    void handleMenuCatalogo(ActionEvent event) {
        System.out.println("Cargando vista Catálogo...");
        loadView("/com/calmasalud/hubi/ui/view/GestorCatalogoView.fxml");
    }

    @FXML
    void handleMenuInventario(ActionEvent event) {
        System.out.println("Cargando vista Inventario...");
        // loadView("/com/calmasalud/hubi/ui/view/InventarioView.fxml");
    }

    @FXML
    void handleMenuReportes(ActionEvent event) {
        System.out.println("Cargando vista Reportes...");
        // loadView("/com/calmasalud/hubi/ui/view/ReportesView.fxml");
    }

    /**
     * Método ayudante para cargar un FXML en el área de contenido principal.
     */
    private void loadView(String fxmlPath) {
        try {
            // Carga el FXML hijo
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load(); // El 'Node' es el AnchorPane (o lo que sea) del FXML hijo

            // Limpia el contenido anterior y añade la nueva vista
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(view);

        } catch (IOException e) {
            e.printStackTrace();
            // (En un producto real, mostrarías un diálogo de error aquí)
        }
    }
}
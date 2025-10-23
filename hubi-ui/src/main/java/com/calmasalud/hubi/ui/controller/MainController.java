package com.calmasalud.hubi.ui.controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML
    private StackPane mainContentArea;
    @FXML
    private Label lblDateTime; // Label para la fecha/hora
    @FXML
    private ToggleButton btnCatalogo; // Botón de catálogo

    // Formateador para la fecha y hora
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        // 1. Iniciar el reloj dinámico
        initClock();

        // 2. Cargar la vista de catálogo por defecto al iniciar
        // (Asegurarnos que el botón esté seleccionado y cargar la vista)
        if (btnCatalogo.isSelected()) {
            loadView("/com/calmasalud/hubi/ui/view/GestorCatalogoView.fxml");
        }
    }

    /**
     * Inicializa un Timeline para actualizar el Label de fecha/hora
     * cada segundo.
     */
    private void initClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.ZERO, e -> {
            lblDateTime.setText(LocalDateTime.now().format(dtf));
        }), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    // Métodos 'onAction' de los botones
    @FXML
    void handleMenuCatalogo(ActionEvent event) {
        System.out.println("Cargando vista Catálogo...");
        loadView("/com/calmasalud/hubi/ui/view/GestorCatalogoView.fxml");
    }

    @FXML
    void handleMenuInventario(ActionEvent event) {
        System.out.println("Cargando vista Inventario...");
        // loadView("/com/calmasalud/hubi/ui/view/InventarioView.fxml");
        // (temporalmente limpiamos la vista)
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(new Label("Módulo de Inventario (En construcción)"));
    }

    @FXML
    void handleMenuReportes(ActionEvent event) {
        System.out.println("Cargando vista Reportes...");
        // loadView("/com/calmasalud/hubi/ui/view/ReportesView.fxml");
        mainContentArea.getChildren().clear();
        mainContentArea.getChildren().add(new Label("Módulo de Reportes (En construcción)"));
    }

    /**
     * Funcion ayudante para cargar un FXML en el área de contenido principal.
     */
    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();

            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(view);

        } catch (IOException e) {
            e.printStackTrace();
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error al cargar la vista: " + fxmlPath));
        }
    }
}
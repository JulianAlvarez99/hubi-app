package com.calmasalud.hubi.ui.controller;
import com.calmasalud.hubi.ui.controller.ConfiguracionController;
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
import javafx.stage.Stage;
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
            loadView("/com/calmasalud/hubi/ui/view/GestorCatalogoView.fxml", null);
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
        loadView("/com/calmasalud/hubi/ui/view/GestorCatalogoView.fxml", null);
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
     * AÑADIDO: Manejador para el botón de Configuración.
     * Este method inyectará el Stage principal en el controlador de configuración.
     */
    @FXML
    void handleMenuConfiguracion(ActionEvent event) {
        System.out.println("Cargando vista Configuración...");
        // Pasamos una función lambda que se ejecutará después de cargar el FXML
        // para inyectar el Stage.
        loadView("/com/calmasalud/hubi/ui/view/ConfiguracionView.fxml", (loader) -> {
            try {
                // Obtener el Stage principal (la ventana)
                Stage mainStage = (Stage) mainContentArea.getScene().getWindow();

                // Obtener el controlador de la vista que acabamos de cargar
                ConfiguracionController controller = loader.getController();

                // Inyectar el Stage en el controlador
                controller.setMainStage(mainStage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Funcion ayudante para cargar un FXML en el área de contenido principal.
     */
    /**
     * MODIFICADO: Funcion ayudante para cargar FXML.
     * Añadido un "callback" para poder ejecutar código (como inyectar el Stage)
     * después de que el loader haya cargado el controlador.
     */
    private void loadView(String fxmlPath, java.util.function.Consumer<FXMLLoader> postLoadCallback) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();

            // Ejecutar el callback (si existe) DESPUÉS de .load()
            if (postLoadCallback != null) {
                postLoadCallback.accept(loader);
            }

            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(view);

        } catch (IOException e) {
            e.printStackTrace();
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error al cargar la vista: " + fxmlPath));
        }
    }
}
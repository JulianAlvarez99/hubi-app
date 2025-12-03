package com.calmasalud.hubi.ui.controller;
import com.calmasalud.hubi.ui.controller.ConfiguracionController;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.stage.Stage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.calmasalud.hubi.core.service.CatalogService;
import com.calmasalud.hubi.core.service.CatalogService;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.ISupplyRepository; // Importar interfaz
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.MasterProductRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.ProductCompositionRepositorySQLite;
import com.calmasalud.hubi.persistence.repository.SupplyRepositorySQLite; // Importar implementación

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
public class MainController {

    public ToggleButton btnConfiguracion;
    @FXML
    private StackPane mainContentArea;
    @FXML
    private Label lblDateTime; // Label para la fecha/hora
    @FXML
    private ToggleButton btnCatalogo; // Botón de catálogo
    @FXML private ToggleButton btnInventario;
    @FXML private VBox subInventarioMenu;
    @FXML private ToggleButton btnSubProductos;
    @FXML private ToggleButton btnSubInsumos;
    // Formateador para la fecha y hora
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final IProductRepository productRepository = new ProductRepositorySQLite();
    private final IMasterProductRepository masterProductRepository = new MasterProductRepositorySQLite();
    private final IProductCompositionRepository productCompositionRepository = new ProductCompositionRepositorySQLite();
    private final ISupplyRepository supplyRepository = new SupplyRepositorySQLite();

    // 2. Inicializar CatalogService con TODOS los argumentos (FIX del error)
    private final CatalogService catalogService = new CatalogService(
            productRepository,
            masterProductRepository,
            productCompositionRepository,
            supplyRepository // 🚨 CUARTO ARGUMENTO AGREGADO
    );
    @FXML
    public void initialize() {
        // 1. Iniciar el reloj dinámico
        initClock();

        btnCatalogo.setSelected(true);
        handleMenuCatalogo(null);
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

    // Methods 'onAction' de los botones
    @FXML
    void handleMenuCatalogo(ActionEvent event) {
        System.out.println("Cargando vista Catálogo...");
        loadView("/com/calmasalud/hubi/ui/view/GestorCatalogoView.fxml", null);
    }

    @FXML
    private void handleMenuInventario(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/InventarioView.fxml"));
            Parent view = loader.load();

            InventarioController controller = loader.getController();

            // 🚨 CRÍTICO: Llamar al nuevo setter después de cargar el FXML
            controller.setCatalogService(this.catalogService);

            // Cargar la vista en el área principal
            mainContentArea.getChildren().setAll(view);
        } catch (IOException e) {
            // Manejar error de carga FXML
            e.printStackTrace();
        }
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
    @FXML


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
    @FXML
    private void handleToggleInventarioSubMenu(ActionEvent event) {
        boolean isSelected = btnInventario.isSelected();
        btnSubProductos.setSelected(true);
        subInventarioMenu.setVisible(isSelected);
        subInventarioMenu.setManaged(isSelected);

        if (isSelected) {
            // Si el menú se abre, carga la vista de Inventario y selecciona por defecto Productos
            // Usamos Platform.runLater para evitar errores de timing en la UI
            javafx.application.Platform.runLater(() -> {
                handleMenuInventarioProductos(null);
            });
        } else {
            // Opcional: limpiar la vista si el toggle se desactiva
            mainContentArea.getChildren().clear();
        }
    }
    @FXML
    private void handleMenuInventarioProductos(ActionEvent event) {
        // Llama al cargador y le indica que active la vista de Productos (true)
        loadInventarioViewContent(true);
    }

    @FXML
    private void handleMenuInventarioInsumos(ActionEvent event) {
        // Llama al cargador y le indica que active la vista de Insumos (false)
        loadInventarioViewContent(false);
    }
    private void loadInventarioViewContent(boolean showProducts) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/calmasalud/hubi/ui/view/InventarioView.fxml"));
            Parent view = loader.load();

            InventarioController controller = loader.getController();
            controller.setCatalogService(this.catalogService); // Inyección de dependencia

            // 🚨 CRÍTICO: Indica al controlador interno qué vista mostrar
            controller.setActiveView(showProducts);

            mainContentArea.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
            // Fallback de error
            mainContentArea.getChildren().clear();
            mainContentArea.getChildren().add(new Label("Error al cargar Inventario: " + e.getMessage()));
        }
    }
}
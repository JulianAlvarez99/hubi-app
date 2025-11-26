package com.calmasalud.hubi.ui;

import com.calmasalud.hubi.core.service.RecycleBinManager;
import com.calmasalud.hubi.persistence.db.SQLiteManager;
import com.calmasalud.hubi.ui.util.UISettings;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font; // Importar Font
import javafx.stage.Stage;
import javafx.application.Platform;
import java.io.IOException;
import java.util.Objects; // Importar Objects

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 1. Cargar fuentes personalizadas (si existen)
        try {
            // Cargar una fuente específica como ejemplo, ajusta según necesites
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-Regular.ttf")).toExternalForm(),
                    14 // Tamaño base, la UI lo ajustará
            );
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-Medium.ttf")).toExternalForm(), 14
            );
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-SemiBold.ttf")).toExternalForm(), 14
            );
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-Bold.ttf")).toExternalForm(), 14
            );
            // Añade más cargas si usas otros pesos de Maven Pro
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo cargar una o más fuentes Maven Pro. Usando fuente por defecto.");
            // e.printStackTrace(); // Opcional: mostrar detalles del error
        }

        // Inicializar la base de datos (crear tablas si no existen)
        SQLiteManager.initializeDatabase();

        // Inicializar la papelera de reciclaje (crear carpeta si no existe)
        RecycleBinManager.ensureRecycleBinExists();

        // 2. Cargar el archivo FXML de la vista principal
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/view/MainView.fxml")));
        Parent root = loader.load();

        // 4. Cargar configuración de tamaño
        UISettings settings = new UISettings();
        double[] size = settings.loadWindowSize(); // [width, height] - ahora con validación automática
        double baseFontSize = settings.loadBaseFontSize();

        System.out.println("Iniciando aplicación con resolución: " + size[0] + "x" + size[1]);

        // 5. Configurar la escena con el tamaño CARGADO Y VALIDADO
        Scene scene = new Scene(root, size[0], size[1]);

        // 6. Aplicar la hoja de estilos (código existente)
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/css/styles.css")).toExternalForm()
            );
        } catch (Exception e) {
            System.err.println("Error al cargar la hoja de estilos styles.css: " + e.getMessage());
        }

        // --- APLICAR TAMAÑO FUENTE BASE AL NODO RAÍZ ANTES DE MOSTRAR ---
        if (scene.getRoot() != null) {
            String style = String.format(".root { -fx-font-size: %.2fpx; }", baseFontSize);
            scene.getRoot().setStyle(style);
        }
        // ----------------------------------------------------------------

        // 7. Configurar el escenario
        primaryStage.setTitle("Sistema HUBI v1.0 - Calma Salud");
        primaryStage.setScene(scene);

        // Establecer tamaño mínimo y máximo razonables
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // Establecer el tamaño inicial explícitamente para evitar cambios aleatorios
        primaryStage.setWidth(size[0]);
        primaryStage.setHeight(size[1]);

        // 8. Guardar el tamaño cuando el usuario lo cambia manualmente o cierra la ventana
        primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (primaryStage.isShowing() && !primaryStage.isIconified() && !primaryStage.isMaximized()) {
                settings.saveWindowSize(newVal.doubleValue(), primaryStage.getHeight());
            }
        });

        primaryStage.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (primaryStage.isShowing() && !primaryStage.isIconified() && !primaryStage.isMaximized()) {
                settings.saveWindowSize(primaryStage.getWidth(), newVal.doubleValue());
            }
        });

        primaryStage.setOnCloseRequest(event -> {
            // Guardar tamaño final al cerrar
            if (!primaryStage.isMaximized()) {
                settings.saveWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            }
            Platform.exit();
            System.exit(0);
        });

        // --- FIN DE MODIFICACIÓN ---

        primaryStage.show();

        // Centrar después de mostrar para asegurar posición correcta
        primaryStage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
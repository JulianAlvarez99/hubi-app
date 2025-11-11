package com.calmasalud.hubi.ui;

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

        // 2. Cargar el archivo FXML de la vista principal
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/view/MainView.fxml")));
        Parent root = loader.load();

        // 4. Cargar configuración de tamaño
        UISettings settings = new UISettings();
        double[] size = settings.loadWindowSize(); // [width, height]
        double baseFontSize = settings.loadBaseFontSize();

        // 5. Configurar la escena con el tamaño CARGADO (en lugar del fijo 1024x768)
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

        // 7. Configurar el escenario (código existente)
        primaryStage.setTitle("Sistema HUBI v1.0 - Calma Salud");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // 8. (Opcional pero recomendado) Guardar el tamaño si el usuario lo cambia manualmente
        // Esto guarda el tamaño cuando el usuario cierra la ventana.
        primaryStage.setOnCloseRequest(event -> {
            settings.saveWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            // (Opcional: guardar también posición)
            // settings.saveWindowPosition(primaryStage.getX(), primaryStage.getY());
            // Si quieres guardar el tamaño basado en el tamaño final al cerrar:
            // double finalBaseSize = calculateBaseFontSize(primaryStage.getWidth()); // Necesitarías mover calculateBaseFontSize a UISettings o aquí
            // settings.saveBaseFontSize(finalBaseSize);

            Platform.exit();
            System.exit(0);
        });

        // --- FIN DE MODIFICACIÓN ---

        primaryStage.show();
        primaryStage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
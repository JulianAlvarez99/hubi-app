package com.calmasalud.hubi.ui;

import com.calmasalud.hubi.persistence.db.SQLiteManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font; // Importar Font
import javafx.stage.Stage;

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
                    10 // Tamaño base, la UI lo ajustará
            );
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-Medium.ttf")).toExternalForm(), 10
            );
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-SemiBold.ttf")).toExternalForm(), 10
            );
            Font.loadFont(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/fonts/MavenPro-Bold.ttf")).toExternalForm(), 10
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

        // 3. Configurar la escena con un tamaño inicial razonable
        Scene scene = new Scene(root, 1024, 768); // Tamaño inicial sugerido

        // 4. Aplicar la hoja de estilos CSS
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/com/calmasalud/hubi/ui/css/styles.css")).toExternalForm()
            );
        } catch (NullPointerException e) {
            System.err.println("Error Crítico: No se pudo encontrar la hoja de estilos styles.css.");
            // Considerar salir o mostrar un error grave
        } catch (Exception e) {
            System.err.println("Error al cargar la hoja de estilos styles.css: " + e.getMessage());
            e.printStackTrace();
        }


        // 5. Configurar el escenario (la ventana)
        primaryStage.setTitle("Sistema HUBI v1.0 - Calma Salud"); // Título más descriptivo
        primaryStage.setScene(scene);

        // --- AJUSTES DE RESPONSIVIDAD ---
        primaryStage.setMinWidth(800); // Ancho mínimo para mantener usabilidad
        primaryStage.setMinHeight(600); // Alto mínimo
        // ---------------------------------

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
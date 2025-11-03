package com.calmasalud.hubi.persistence.db;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteManager {

    private static final String DB_FILE = "jdbc:sqlite:hubi_catalog.db";

    public static Connection getConnection() throws SQLException {
        // Establece la conexión. El archivo .db se crea si no existe.
        return DriverManager.getConnection(DB_FILE);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Tabla para guardar los productos/piezas (entidad principal)
            String sqlProducts = "CREATE TABLE IF NOT EXISTS products ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "code TEXT NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "file_extension TEXT NOT NULL"
                    + ");";

            // 2. Tabla para el control de correlativos (RF8)
            String sqlCorrelatives = "CREATE TABLE IF NOT EXISTS product_correlatives ("
                    + "prefix TEXT PRIMARY KEY," // Ej: SOPROJ
                    + "last_number INTEGER NOT NULL DEFAULT 0"
                    + ");";
            // 3. Tabla para la Gestión de Correlativos del Producto MAESTRO
            String sqlMasterCorrelatives = "CREATE TABLE IF NOT EXISTS master_correlatives ("
                    + "master_prefix TEXT PRIMARY KEY," // Ej: SOP (las 3 primeras letras del producto)
                    + "last_number INTEGER NOT NULL DEFAULT 0"
                    + ");";
            // 4.Tabla para definir el Producto Maestro (RF4 - Parte Lógica)
            // La clave es el prefijo + correlativo (Ej: SOP01)
            String sqlMasterProducts = "CREATE TABLE IF NOT EXISTS master_products ("
                    + "master_code TEXT PRIMARY KEY,"
                    + "product_prefix TEXT NOT NULL," // El prefijo de 3 letras (Ej: SOP)
                    + "product_name TEXT NOT NULL UNIQUE," // Nombre completo para mostrar
                    + "description TEXT"
                    + ");";
            // 5.Tabla para el Stock de Productos Finalizados (RF4 - Parte Cuantitativa)
            String sqlFinishedStock = "CREATE TABLE IF NOT EXISTS finished_products_stock ("
                    + "master_code TEXT PRIMARY KEY,"
                    + "quantity_available INTEGER NOT NULL DEFAULT 0,"
                    + "price REAL NOT NULL DEFAULT 0.0,"
                    + "FOREIGN KEY (master_code) REFERENCES master_products(master_code)"
                    + ");";
            stmt.execute(sqlProducts);
            stmt.execute(sqlCorrelatives);
            stmt.execute(sqlMasterCorrelatives);
            stmt.execute(sqlMasterProducts);
            stmt.execute(sqlFinishedStock);
            System.out.println("✅ Base de datos y tablas inicializadas correctamente.");

        } catch (SQLException e) {
            System.err.println("❌ Error al inicializar la base de datos: " + e.getMessage());
        }
    }
}
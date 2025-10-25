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
                    + "code TEXT UNIQUE NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "file_extension TEXT NOT NULL"
                    + ");";

            // 2. Tabla para el control de correlativos (RF8)
            String sqlCorrelatives = "CREATE TABLE IF NOT EXISTS product_correlatives ("
                    + "prefix TEXT PRIMARY KEY," // Ej: SOPROJ
                    + "last_number INTEGER NOT NULL DEFAULT 0"
                    + ");";

            stmt.execute(sqlProducts);
            stmt.execute(sqlCorrelatives);

            System.out.println("✅ Base de datos y tablas inicializadas correctamente.");

        } catch (SQLException e) {
            System.err.println("❌ Error al inicializar la base de datos: " + e.getMessage());
        }
    }
}
package com.calmasalud.hubi.persistence.db;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteManager {

    //private static final String DB_FILE = "jdbc:sqlite:hubi_catalog.db";
    private final String dbUrl;
    public SQLiteManager(String dbFileName) {
        // Formato JDBC SQLite: jdbc:sqlite:nombre_archivo.db
        this.dbUrl = "jdbc:sqlite:" + dbFileName;
    }
    public Connection getConnection() throws SQLException {
        // Establece la conexión. El archivo .db se crea si no existe.
        return DriverManager.getConnection(this.dbUrl);
    }


    public static void initializeDatabase() {
        // Se utiliza una URL local estática para la inicialización inicial
        String staticDbUrl = "jdbc:sqlite:hubi_catalog.db";

        try (Connection conn = DriverManager.getConnection(staticDbUrl);
             Statement stmt = conn.createStatement()) {

            // ... (Creación de tablas existentes: products, correlatives, master_correlatives, master_products, finished_products_stock, composition, piece_stock)

            // 1. Tabla para guardar los productos/piezas (entidad principal)
            String sqlProducts = "CREATE TABLE IF NOT EXISTS products ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "code TEXT NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "file_extension TEXT NOT NULL,"
                    + "peso_filamento_gramos REAL NOT NULL DEFAULT 0.0,"
                    + "usage_detail TEXT DEFAULT ''"
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
                    + "product_prefix TEXT NOT NULL,"
                    + "product_name TEXT NOT NULL,"
                    + "description TEXT"
                    + ");";
            // 5.Tabla para el Stock de Productos Finalizados (RF4 - Parte Cuantitativa)
            String sqlFinishedStock = "CREATE TABLE IF NOT EXISTS finished_products_stock ("
                    + "master_code TEXT PRIMARY KEY,"
                    + "quantity_available INTEGER NOT NULL DEFAULT 0,"
                    + "price REAL NOT NULL DEFAULT 0.0,"
                    + "FOREIGN KEY (master_code) REFERENCES master_products(master_code)"
                    + ");";
            // 6. Tabla para la Composición del Producto (BOM)
            String sqlComposition = "CREATE TABLE IF NOT EXISTS product_composition ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "master_code TEXT NOT NULL," // FK a master_products (Ej: SOP01)
                    + "piece_name_base TEXT NOT NULL," // Nombre base de la pieza (Ej: Llave_base)
                    + "required_quantity INTEGER NOT NULL DEFAULT 1," // Cantidad de esta pieza necesaria para 1 Producto Final
                    + "UNIQUE (master_code, piece_name_base),"
                    + "FOREIGN KEY (master_code) REFERENCES master_products(master_code) ON DELETE CASCADE"
                    + ");";

            // 7: Tabla para el Stock Real de Piezas Producidas (Stock para Ensamble)
            String sqlPieceStock = "CREATE TABLE IF NOT EXISTS piece_stock ("
                    + "piece_name_base TEXT NOT NULL,"
                    + "color_name TEXT NOT NULL," // NUEVO: Color usado (Ej: ROJO PLA)
                    + "available_quantity INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (piece_name_base, color_name)" // CLAVE COMPUESTA
                    + ");";

            // 🚨 8. Tabla para el Stock de Insumos (Supply) (HU1)
            String sqlSupply = "CREATE TABLE IF NOT EXISTS supply (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "code TEXT NOT NULL UNIQUE," +
                    "name TEXT," +
                    "tipoFilamento TEXT NOT NULL," +
                    "colorFilamento TEXT NOT NULL," +
                    "cantidadDisponible REAL NOT NULL," +
                    "umbralAlerta REAL NOT NULL" +
                    ");";
            String sqlSupplyCorrelatives = "CREATE TABLE IF NOT EXISTS supply_correlatives ("
                    + "prefix TEXT PRIMARY KEY," // Ej: ROJ-PLA
                    + "last_number INTEGER NOT NULL DEFAULT 0"
                    + ");";

            stmt.execute(sqlProducts);
            stmt.execute(sqlCorrelatives);
            stmt.execute(sqlMasterCorrelatives);
            stmt.execute(sqlMasterProducts);
            stmt.execute(sqlFinishedStock);
            stmt.execute(sqlComposition);
            stmt.execute(sqlPieceStock);
            stmt.execute(sqlSupply);
            stmt.execute(sqlSupplyCorrelatives);

            System.out.println("✅ Base de datos y tablas inicializadas correctamente.");

        } catch (SQLException e) {
            System.err.println("❌ Error al inicializar la base de datos: " + e.getMessage());
        }
    }
}
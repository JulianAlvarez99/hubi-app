package com.calmasalud.hubi.persistence.repository;

import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.persistence.db.SQLiteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProductCompositionRepositorySQLite implements IProductCompositionRepository {

    private final SQLiteManager sqLiteManager;
    private static final String DB_NAME = "hubi_catalog.db";

    // Constructor para inyección de dependencia (usado en Main.java)
    public ProductCompositionRepositorySQLite(SQLiteManager sqLiteManager) {
        this.sqLiteManager = sqLiteManager;
        initializeTable();
    }

    // Constructor sin argumentos (usado en TipoCargaController.java y otros)
    public ProductCompositionRepositorySQLite() {
        // Llama al constructor que creamos en SQLiteManager, el cual ahora acepta el String
        this.sqLiteManager = new SQLiteManager(DB_NAME);
        initializeTable();
    }

    // 🚨 CORRECCIÓN: Method privado sin @Override
    private void initializeTable() {
        // Definición de la tabla composition de SQLiteManager
        String sql = "CREATE TABLE IF NOT EXISTS product_composition ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "master_code TEXT NOT NULL,"
                + "piece_name_base TEXT NOT NULL,"
                + "required_quantity INTEGER NOT NULL DEFAULT 1,"
                + "UNIQUE (master_code, piece_name_base),"
                + "FOREIGN KEY (master_code) REFERENCES master_products(master_code) ON DELETE CASCADE"
                + ");";

        try (Connection conn = sqLiteManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error al inicializar la tabla 'product_composition': " + e.getMessage());
        }
    }

    // Este Method no implementa una interfaz, por lo que no lleva @Override
    public void saveComposition(String masterCode, List<ProductComposition> composition) {
        // master_code, piece_name_base, required_quantity
        String sql = "INSERT INTO product_composition (master_code, piece_name_base, required_quantity) VALUES (?, ?, ?)";

        Connection conn = null; // 🚨 Declarada fuera para manejo de transacción
        try {
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false); // Iniciar transacción (RF6)

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (ProductComposition item : composition) {
                    pstmt.setString(1, item.getMasterCode());
                    pstmt.setString(2, item.getPieceNameBase());
                    pstmt.setInt(3, item.getRequiredQuantity());
                    pstmt.addBatch(); // Agrega la sentencia al lote
                }
                pstmt.executeBatch(); // Ejecuta todas las inserciones
            }

            conn.commit(); // Confirmar transacción
        } catch (SQLException e) {
            System.err.println("❌ Error al guardar la composición del producto: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            // Lanzo una excepción Runtime para que la capa de servicio pueda manejarla
            throw new RuntimeException("Fallo al guardar la composición del producto.", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    @Override
    public List<ProductComposition> getComposition(String masterCode) {
        List<ProductComposition> composition = new ArrayList<>();
        String sql = "SELECT master_code, piece_name_base, required_quantity FROM product_composition WHERE master_code = ?"; // Agregué master_code al select

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, masterCode);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                composition.add(new ProductComposition(
                        rs.getString("master_code"), // Usamos la columna real si es necesario, o el masterCode pasado
                        rs.getString("piece_name_base"),
                        rs.getInt("required_quantity")
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener la composición: " + e.getMessage());
        }
        return composition;
    }

    @Override
    public boolean compositionExists(String masterCode) {
        String sql = "SELECT COUNT(*) FROM product_composition WHERE master_code = ?";
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, masterCode);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al verificar la existencia de la composición: " + e.getMessage());
        }
        return false;
    }

}
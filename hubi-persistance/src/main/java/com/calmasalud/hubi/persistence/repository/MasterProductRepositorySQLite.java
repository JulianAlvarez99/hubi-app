package com.calmasalud.hubi.persistence.repository;

import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.MasterProductView;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.persistence.db.SQLiteManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MasterProductRepositorySQLite implements IMasterProductRepository {

    // CAMPOS DE INSTANCIA
    private final SQLiteManager sqLiteManager;
    private static final String DB_NAME = "hubi_catalog.db";

    // Constructor 1: Con inyección de dependencia (para Main.java)
    public MasterProductRepositorySQLite(SQLiteManager sqLiteManager) {
        this.sqLiteManager = sqLiteManager;
    }

    // 🚨 FIX: Constructor 2: Sin argumentos (Para uso en controladores)
    public MasterProductRepositorySQLite() {
        // Inicializa el SQLiteManager internamente.
        this.sqLiteManager = new SQLiteManager(DB_NAME);
    }

    // --- Lógica de Correlativo Maestro (RF8) ---
    @Override
    public String getPrefixFromName(String productName) {
        if (productName == null || productName.trim().length() < 3) {
            return null;
        }
        return productName.trim().substring(0, 3).toUpperCase(Locale.ROOT);
    }

    @Override
    public String getNextMasterCode(String masterPrefix) {
        Connection conn = null;
        int nextId = 0;
        String prefix = masterPrefix.toUpperCase(Locale.ROOT);

        try {
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false);

            String selectSql = "SELECT last_number FROM master_correlatives WHERE master_prefix = ?";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, prefix);
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    int lastId = rs.getInt("last_number");
                    nextId = lastId + 1;
                    String updateSql = "UPDATE master_correlatives SET last_number = ? WHERE master_prefix = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, nextId);
                        updateStmt.setString(2, prefix);
                        updateStmt.executeUpdate();
                    }
                } else {
                    nextId = 1;
                    String insertSql = "INSERT INTO master_correlatives (master_prefix, last_number) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, prefix);
                        insertStmt.setInt(2, nextId);
                        insertStmt.executeUpdate();
                    }
                }
            }
            conn.commit();
            return String.format("%s%02d", prefix, nextId); // Formato SOP01

        } catch (SQLException e) {
            System.err.println("❌ Error de BD en correlativo maestro: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RuntimeException("Error en persistencia de correlativo maestro.", e);
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
    public long saveNewProduct(MasterProduct product, double initialPrice) {
        Connection conn = null;

        String sqlInsertMaster = "INSERT INTO master_products (master_code, product_prefix, product_name, description) VALUES (?, ?, ?, ?)";
        String sqlInsertStock = "INSERT INTO finished_products_stock (master_code, quantity_available, price) VALUES (?, 0, ?)";

        try {
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false); // Iniciar transacción (RF6)

            // 1. Insertar el Producto Maestro
            try (PreparedStatement pstmtMaster = conn.prepareStatement(sqlInsertMaster)) {
                pstmtMaster.setString(1, product.getMasterCode());
                pstmtMaster.setString(2, product.getProductPrefix());
                pstmtMaster.setString(3, product.getProductName());
                pstmtMaster.setString(4, product.getDescription());
                pstmtMaster.executeUpdate();
            }

            // 2. Inicializar el Stock (RF4)
            try (PreparedStatement pstmtStock = conn.prepareStatement(sqlInsertStock)) {
                pstmtStock.setString(1, product.getMasterCode());
                pstmtStock.setDouble(2, initialPrice);
                pstmtStock.executeUpdate();
            }

            conn.commit();
            return 1L; // Éxito

        } catch (SQLException e) {
            System.err.println(" Error al guardar Producto Maestro/Stock: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RuntimeException("Fallo al crear el Producto Maestro.", e);
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
    public long save(MasterProduct product) {
        return saveNewProduct(product, 0.0);
    }

    // --- Métodos de Stock y Lectura ---

    @Override
    public void increaseStock(String masterCode, int quantity) {
        String sql = "UPDATE finished_products_stock SET quantity_available = quantity_available + ? WHERE master_code = ?";

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, quantity);
            pstmt.setString(2, masterCode);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println(" Error al modificar stock final: " + e.getMessage());
            throw new RuntimeException("Fallo en la persistencia del stock.", e);
        }
    }

    @Override
    public MasterProduct findByMasterCode(String masterCode) {
        // Implementación de búsqueda
        return null;
    }

    @Override
    public List<MasterProduct> findAll() {
        List<MasterProduct> products = new ArrayList<>();

        String sql = "SELECT m.master_code, m.product_prefix, m.product_name, m.description, "
                + "s.quantity_available, s.price "
                + "FROM master_products m "
                + "JOIN finished_products_stock s ON m.master_code = s.master_code";

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                MasterProductView product = new MasterProductView(
                        rs.getString("master_code"),
                        rs.getString("product_prefix"),
                        rs.getString("product_name"),
                        rs.getString("description"),
                        rs.getInt("quantity_available"),
                        rs.getDouble("price")
                );
                products.add(product);
            }

        } catch (SQLException e) {
            System.err.println("❌ Error al listar productos maestros con stock: " + e.getMessage());
        }
        return products;
    }
    @Override
    public void deleteProduct(String masterCode) {
        Connection conn = null;
        String sqlDeleteStock = "DELETE FROM finished_products_stock WHERE master_code = ?";
        String sqlDeleteMaster = "DELETE FROM master_products WHERE master_code = ?";

        try {
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false); // Iniciar transacción (RF6)

            // 1. Eliminar el registro de Stock
            try (PreparedStatement pstmtStock = conn.prepareStatement(sqlDeleteStock)) {
                pstmtStock.setString(1, masterCode);
                pstmtStock.executeUpdate();
            }

            // 2. Eliminar el registro Maestro
            try (PreparedStatement pstmtMaster = conn.prepareStatement(sqlDeleteMaster)) {
                pstmtMaster.setString(1, masterCode);
                pstmtMaster.executeUpdate();
            }

            conn.commit(); // Confirmar transacción

        } catch (SQLException e) {
            System.err.println("❌ Error al eliminar el Producto Maestro/Stock: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } // Revertir
            }
            throw new RuntimeException("Fallo al eliminar el Producto Maestro.", e);
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
    public MasterProduct findByProductName(String productName) {
        String sql = "SELECT master_code, product_prefix, product_name, description FROM master_products WHERE product_name = ?";
        MasterProduct product = null;

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, productName.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                product = new MasterProduct(
                        rs.getString("master_code"),
                        rs.getString("product_prefix"),
                        rs.getString("product_name"),
                        rs.getString("description")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al buscar producto maestro por nombre: " + e.getMessage());
        }
        return product;
    }
    @Override
    public MasterProduct findByProductPrefix(String prefix) {
        String sql = "SELECT master_code, product_prefix, product_name, description FROM master_products WHERE product_prefix = ?";
        MasterProduct product = null;

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, prefix.toUpperCase(Locale.ROOT));
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                product = new MasterProduct(
                        rs.getString("master_code"),
                        rs.getString("product_prefix"),
                        rs.getString("product_name"),
                        rs.getString("description")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al buscar producto maestro por prefijo: " + e.getMessage());
        }
        return product;
    }
    @Override
    public void decreaseStock(String masterCode, int quantity) throws RuntimeException {
        String sql = "UPDATE finished_products_stock SET quantity_available = quantity_available - ? WHERE master_code = ? AND quantity_available >= ?";

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, quantity);
            pstmt.setString(2, masterCode);
            pstmt.setInt(3, quantity);

            int rows = pstmt.executeUpdate();

            if (rows == 0) {
                throw new RuntimeException("Stock insuficiente o producto no encontrado para la eliminación de unidades.");
            }

        } catch (SQLException e) {
            System.err.println("❌ Error al disminuir stock: " + e.getMessage());
            throw new RuntimeException("Fallo en la persistencia al disminuir stock.", e);
        }
    }
}
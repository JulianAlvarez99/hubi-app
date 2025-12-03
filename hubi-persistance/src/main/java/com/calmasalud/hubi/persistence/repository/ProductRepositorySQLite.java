package com.calmasalud.hubi.persistence.repository;

import com.calmasalud.hubi.core.model.PieceStockColorView;
import com.calmasalud.hubi.core.model.PieceStockDeduction;
import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.db.SQLiteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

//Gestiona las transacciones y guarda el objeto Product.
public class ProductRepositorySQLite implements IProductRepository {

    // 🚨 NUEVOS CAMPOS DE INSTANCIA
    private final SQLiteManager sqLiteManager;
    private static final String DB_NAME = "hubi_catalog.db";

    // Constructor con inyección de dependencia (para Main.java)
    public ProductRepositorySQLite(SQLiteManager sqLiteManager) {
        this.sqLiteManager = sqLiteManager;
        // No hay initializeTable, asumo que las tablas de products/stock ya se crean en SQLiteManager.initializeDatabase()
    }

    // Constructor sin argumentos (para uso directo en controladores)
    public ProductRepositorySQLite() {
        this.sqLiteManager = new SQLiteManager(DB_NAME);
    }

    // [ Implementación de getNextCorrelative(String prefijoSeisLetras) ]
    @Override
    public String getNextCorrelative(String prefijoSeisLetras) {
        Connection conn = null;
        int nextId = 0;

        try {
            // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false);

            // Lógica de BD...
            String selectSql = "SELECT last_number FROM product_correlatives WHERE prefix = ?";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, prefijoSeisLetras);
                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    int lastId = rs.getInt("last_number");
                    nextId = lastId + 1;
                    String updateSql = "UPDATE product_correlatives SET last_number = ? WHERE prefix = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, nextId);
                        updateStmt.setString(2, prefijoSeisLetras);
                        updateStmt.executeUpdate();
                    }
                } else {
                    nextId = 1;
                    String insertSql = "INSERT INTO product_correlatives (prefix, last_number) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, prefijoSeisLetras);
                        insertStmt.setInt(2, nextId);
                        insertStmt.executeUpdate();
                    }
                }
            }
            conn.commit();
            return String.format("%03d", nextId);
        } catch (SQLException e) {
            System.err.println("Error de BD en correlativo: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RuntimeException("Error en persistencia de correlativo.", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    // [ Implementación de save(Product product) ]
    @Override
    public long save(Product product) {
        // 🚨 SQL Actualizado con usage_detail
        String sql = "INSERT INTO products (code, name, file_extension, peso_filamento_gramos, usage_detail, calculated_cost) VALUES (?, ?, ?, ?, ?, ?)";
        long generatedId = -1;

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, product.getCode());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getFileExtension());
            pstmt.setDouble(4, product.getWeightGrams());
            pstmt.setString(5, product.getUsageDetail()); // 🚨 Guardar detalle
            pstmt.setDouble(6, product.getCost());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getLong(1);
                    }
                }
            }
            return generatedId;
        } catch (SQLException e) {
            System.err.println("Error al guardar el producto en BD: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public void updateProductCost(String code, double cost) {
        String sql = "UPDATE products SET calculated_cost = ? WHERE code = ?";
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, cost);
            pstmt.setString(2, code);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error al actualizar costo: " + e.getMessage());
        }
    }

    @Override
    public Product findByCode(String code) {
        // Actualizamos la consulta para traer el costo
        String sql = "SELECT code, name, file_extension, peso_filamento_gramos, usage_detail, calculated_cost FROM products " +
                "WHERE code = ? " +
                "ORDER BY CASE WHEN file_extension LIKE '%.gcode' THEN 1 ELSE 2 END ASC LIMIT 1";

        Product product = null;
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                product = new Product(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("file_extension"),
                        rs.getDouble("peso_filamento_gramos"),
                        rs.getString("usage_detail"),
                        rs.getDouble("calculated_cost") // <--- LEER EL COSTO
                );
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar producto: " + e.getMessage());
        }
        return product;
    }

    @Override
    public void deleteByCode(String code) {
        String sql = "DELETE FROM products WHERE code = ?";
        // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al eliminar producto/pieza por código: " + e.getMessage());
        }
    }

    @Override
    public List<Product> findPiecesByMasterPrefix(String masterPrefix) {
        List<Product> pieces = new ArrayList<>();

        // CORRECCIÓN: Agregar 'calculated_cost' (y otros campos útiles) a la consulta
        String sql = "SELECT code, name, file_extension, peso_filamento_gramos, usage_detail, calculated_cost FROM products WHERE code LIKE ? || '%'";

        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, masterPrefix.toUpperCase());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // CORRECCIÓN: Usar el constructor completo para incluir el costo
                pieces.add(new Product(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("file_extension"),
                        rs.getDouble("peso_filamento_gramos"),
                        rs.getString("usage_detail"),
                        rs.getDouble("calculated_cost") // <--- ¡AQUÍ ESTÁ LA CLAVE!
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al listar piezas por prefijo: " + e.getMessage());
        }
        return pieces;
    }

    /**
     * OBTENER STOCK TOTAL: Suma el stock de todos los colores de esa pieza.
     */
    @Override
    public int getPieceStockQuantity(String pieceNameBase) {
        String sql = "SELECT SUM(available_quantity) FROM piece_stock WHERE piece_name_base = ?";

        // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pieceNameBase.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener stock total de pieza (SUM): " + e.getMessage());
        }
        return 0;
    }

    @Override
    public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {
        String sqlUpsert = "INSERT INTO piece_stock (piece_name_base, color_name, available_quantity) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT(piece_name_base, color_name) DO UPDATE SET available_quantity = available_quantity + excluded.available_quantity";

        // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlUpsert)) {

            pstmt.setString(1, pieceNameBase);
            pstmt.setString(2, colorName);
            pstmt.setInt(3, quantity);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ ERROR SQL al incrementar stock de pieza: " + e.getMessage());
            throw new RuntimeException("Fallo al actualizar el stock de la pieza por color. Fallo: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) {
        List<PieceStockColorView> stockList = new ArrayList<>();
        String sql = "SELECT color_name, available_quantity FROM piece_stock WHERE piece_name_base = ?";

        // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pieceNameBase.trim());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String colorName = rs.getString("color_name");
                int availableQuantity = rs.getInt("available_quantity");

                System.out.println("DEBUG REPO STOCK: Pieza: " + pieceNameBase + ", Clave en DB: " + colorName + ", Cantidad: " + availableQuantity);

                stockList.add(new PieceStockColorView(
                        pieceNameBase,
                        colorName,
                        availableQuantity
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener stock de pieza por color: " + e.getMessage());
        }
        return stockList;
    }

    @Override
    public void deletePieceStockByPieceNameBase(String pieceNameBase) {
        String sql = "DELETE FROM piece_stock WHERE piece_name_base = ?";

        // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pieceNameBase.trim());
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Error al eliminar registro de piece_stock: " + e.getMessage());
        }
    }

    @Override
    public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) throws RuntimeException {
        String sql = "UPDATE piece_stock SET available_quantity = available_quantity - ? " +
                "WHERE piece_name_base = ? AND color_name = ? AND available_quantity >= ?";

        // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, quantity);
            pstmt.setString(2, pieceNameBase);
            pstmt.setString(3, colorName);
            pstmt.setInt(4, quantity);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new RuntimeException(
                        "Stock insuficiente para el color '" + colorName +
                                "' o la pieza no tiene stock suficiente para descontar " + quantity + " unidades."
                );
            }

        } catch (SQLException e) {
            System.err.println("❌ Error SQL al disminuir stock de pieza por color: " + e.getMessage());
            throw new RuntimeException("Fallo en la persistencia al disminuir stock. Fallo: " + e.getMessage(), e);
        }
    }

    @Override
    public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) throws RuntimeException {
        Connection conn = null;
        try {
            // 🚨 CORRECCIÓN: Usar la instancia sqLiteManager
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false);

            for (PieceStockDeduction deduction : deductions) {
                String sql = "UPDATE piece_stock SET available_quantity = available_quantity - ? " +
                        "WHERE piece_name_base = ? AND color_name = ? AND available_quantity >= ?";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, deduction.getQuantity());
                    pstmt.setString(2, deduction.getPieceNameBase());
                    pstmt.setString(3, deduction.getColor());
                    pstmt.setInt(4, deduction.getQuantity());

                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected == 0) {
                        throw new RuntimeException("Stock insuficiente para descontar " + deduction.getQuantity() +
                                " unidades de " + deduction.getPieceNameBase() + " (" + deduction.getColor() + ").");
                    }
                }
            }

            conn.commit();

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Error durante el rollback: " + rollbackEx.getMessage());
                }
            }
            throw new RuntimeException("Fallo en la deducción por lotes. La operación fue revertida. Causa: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("Error al cerrar conexión después de la transacción: " + closeEx.getMessage());
                }
            }
        }
    }
}
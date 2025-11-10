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

    // [ Implementación de getNextCorrelative(String prefijoSeisLetras) ]
    @Override
    public String getNextCorrelative(String prefijoSeisLetras) {
        Connection conn = null;
        int nextId = 0;

        try {
            conn = SQLiteManager.getConnection();
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
        // Inserta el producto o pieza en la tabla 'products'
        String sql = "INSERT INTO products (code, name, file_extension) VALUES (?, ?, ?)";
        long generatedId = -1;

        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, product.getCode());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getFileExtension());
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
    public Product findByCode(String code) {
        String sql = "SELECT code, name, file_extension FROM products WHERE code = ?";
        Product product = null;

        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                product = new Product(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("file_extension")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar producto por código: " + e.getMessage());
        }
        return product;
    }
    @Override
    public void deleteByCode(String code) {
        String sql = "DELETE FROM products WHERE code = ?";
        try (Connection conn = SQLiteManager.getConnection();
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

        // La consulta usa LIKE 'prefijo%', que es la lógica para asociar todas las piezas (archivos)
        // a un producto maestro (Ej: todos los que empiezan con LLA)
        String sql = "SELECT code, name, file_extension FROM products WHERE code LIKE ? || '%'";

        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // El código de pieza es de 9 caracteres (LLAWHI001), por lo que buscar por LLA%
            // encuentra todas las piezas de ese producto.
            pstmt.setString(1, masterPrefix.toUpperCase());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                pieces.add(new Product(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("file_extension")
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
        // Consulta CLAVE: Suma el stock de TODAS las filas que tengan esta piece_name_base.
        String sql = "SELECT SUM(available_quantity) FROM piece_stock WHERE piece_name_base = ?";

        try (java.sql.Connection conn = com.calmasalud.hubi.persistence.db.SQLiteManager.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pieceNameBase.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1); // Devuelve la SUMA total (que es 0 si no hay registros)
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al obtener stock total de pieza (SUM): " + e.getMessage());
        }
        return 0; // Si no hay filas, el stock es cero.
    }
    @Override
    public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {
        // CORRECCIÓN CLAVE: La sintaxis de UPSERT en SQLite.
        // Usamos INSERT INTO ... ON CONFLICT DO UPDATE SET...
        String sqlUpsert = "INSERT INTO piece_stock (piece_name_base, color_name, available_quantity) " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT(piece_name_base, color_name) DO UPDATE SET available_quantity = available_quantity + excluded.available_quantity";

        try (java.sql.Connection conn = com.calmasalud.hubi.persistence.db.SQLiteManager.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sqlUpsert)) {

            // Columnas 1, 2, 3 (INSERT)
            pstmt.setString(1, pieceNameBase);
            pstmt.setString(2, colorName);
            pstmt.setInt(3, quantity); // Este valor se usará en 'excluded.available_quantity'

            pstmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            System.err.println("❌ ERROR SQL al incrementar stock de pieza: " + e.getMessage());
            throw new RuntimeException("Fallo al actualizar el stock de la pieza por color. Fallo: " + e.getMessage(), e);
        }
    }
    @Override
    public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) {
        List<PieceStockColorView> stockList = new ArrayList<>();

        // Consulta para obtener la cantidad y color de la tabla piece_stock
        String sql = "SELECT color_name, available_quantity FROM piece_stock WHERE piece_name_base = ?";

        try (java.sql.Connection conn = com.calmasalud.hubi.persistence.db.SQLiteManager.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pieceNameBase.trim());
            java.sql.ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String colorName = rs.getString("color_name");
                int availableQuantity = rs.getInt("available_quantity");

                // *** PUNTO CRÍTICO DE DEBUGGING ***
                System.out.println("DEBUG REPO STOCK: Pieza: " + pieceNameBase + ", Clave en DB: " + colorName + ", Cantidad: " + availableQuantity);
                // **********************************

                stockList.add(new PieceStockColorView(
                        pieceNameBase,
                        colorName, // Usa la clave tal como viene de la DB
                        availableQuantity
                ));
            }
        } catch (java.sql.SQLException e) {
            System.err.println("❌ Error al obtener stock de pieza por color: " + e.getMessage());
        }
        return stockList;
    }
    @Override
    public void deletePieceStockByPieceNameBase(String pieceNameBase) {
        String sql = "DELETE FROM piece_stock WHERE piece_name_base = ?";

        try (java.sql.Connection conn = com.calmasalud.hubi.persistence.db.SQLiteManager.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pieceNameBase.trim());
            pstmt.executeUpdate();

        } catch (java.sql.SQLException e) {
            System.err.println("❌ Error al eliminar registro de piece_stock: " + e.getMessage());
            // En este punto, no lanzamos una RuntimeException para permitir que el resto del borrado continúe.
        }
    }
    @Override
    public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) throws RuntimeException {
        // 1. Sentencia UPDATE que solo se ejecuta si el stock actual es suficiente para el color y cantidad dados.
        String sql = "UPDATE piece_stock SET available_quantity = available_quantity - ? " +
                "WHERE piece_name_base = ? AND color_name = ? AND available_quantity >= ?";

        try (java.sql.Connection conn = com.calmasalud.hubi.persistence.db.SQLiteManager.getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, quantity);
            pstmt.setString(2, pieceNameBase);
            pstmt.setString(3, colorName);
            pstmt.setInt(4, quantity); // La condición: stock_actual >= quantity

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                // Si no se actualiza ninguna fila, es porque el stock era 0 o insuficiente
                throw new RuntimeException(
                        "Stock insuficiente para el color '" + colorName +
                                "' o la pieza no tiene stock suficiente para descontar " + quantity + " unidades."
                );
            }

        } catch (java.sql.SQLException e) {
            System.err.println("❌ Error SQL al disminuir stock de pieza por color: " + e.getMessage());
            throw new RuntimeException("Fallo en la persistencia al disminuir stock. Fallo: " + e.getMessage(), e);
        }
    }
    @Override
    public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) throws RuntimeException {
        Connection conn = null;
        try {
            conn = SQLiteManager.getConnection(); // Obtener la conexión
            conn.setAutoCommit(false); // <--- INICIO DE LA TRANSACCIÓN

            for (PieceStockDeduction deduction : deductions) {
                // Reutilizamos la lógica de disminución de stock (que ahora debe usar la conexión transaccional)

                // Si decreasePieceStockQuantity abre/cierra su propia conexión,
                // este método fallará. Asumiendo una versión simplificada:

                // Lógica de UPDATE SQL usando la 'conn' transaccional
                String sql = "UPDATE piece_stock SET available_quantity = available_quantity - ? " +
                        "WHERE piece_name_base = ? AND color_name = ? AND available_quantity >= ?";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, deduction.getQuantity());
                    pstmt.setString(2, deduction.getPieceNameBase());
                    pstmt.setString(3, deduction.getColor());
                    pstmt.setInt(4, deduction.getQuantity());

                    int rowsAffected = pstmt.executeUpdate();

                    if (rowsAffected == 0) {
                        // Si el stock es insuficiente, lanzamos una excepción para forzar el rollback
                        throw new RuntimeException("Stock insuficiente para descontar " + deduction.getQuantity() +
                                " unidades de " + deduction.getPieceNameBase() + " (" + deduction.getColor() + ").");
                    }
                }
            }

            conn.commit(); // <--- CONFIRMACIÓN SÓLO SI TODAS LAS DEDUCCIONES FUERON EXITOSAS

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback(); // <--- REVERSIÓN DE TODO EL LOTE
                } catch (SQLException rollbackEx) {
                    System.err.println("Error durante el rollback: " + rollbackEx.getMessage());
                }
            }
            // Propagar la excepción para que el CatalogService la capture y la UI muestre el error.
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
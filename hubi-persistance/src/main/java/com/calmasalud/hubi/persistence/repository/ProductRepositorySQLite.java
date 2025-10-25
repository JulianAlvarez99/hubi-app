package com.calmasalud.hubi.persistence.repository;
import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.db.SQLiteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

}
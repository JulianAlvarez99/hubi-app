package com.calmasalud.hubi.persistence.repository;

import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.persistence.db.SQLiteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductCompositionRepositorySQLite implements IProductCompositionRepository {

    @Override
    public void saveComposition(String masterCode, List<ProductComposition> composition) {
        Connection conn = null;
        // master_code, piece_name_base, required_quantity
        String sql = "INSERT INTO product_composition (master_code, piece_name_base, required_quantity) VALUES (?, ?, ?)";

        try {
            conn = SQLiteManager.getConnection();
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
        String sql = "SELECT piece_name_base, required_quantity FROM product_composition WHERE master_code = ?";

        try (Connection conn = SQLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, masterCode);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                composition.add(new ProductComposition(
                        masterCode,
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
        try (Connection conn = SQLiteManager.getConnection();
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

package com.calmasalud.hubi.persistence.repository;

import com.calmasalud.hubi.core.model.Supply;
import com.calmasalud.hubi.core.repository.ISupplyRepository;
import com.calmasalud.hubi.persistence.db.SQLiteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SupplyRepositorySQLite implements ISupplyRepository {

    private final SQLiteManager sqLiteManager;
    private static final String DB_NAME = "hubi_catalog.db";

    // Constructor con inyección (Main)
    public SupplyRepositorySQLite(SQLiteManager sqLiteManager) {
        this.sqLiteManager = sqLiteManager;
    }

    // Constructor sin argumentos (Controladores)
    public SupplyRepositorySQLite() {
        this.sqLiteManager = new SQLiteManager(DB_NAME);
    }

    // --- Implementación de getNextCorrelativeCode (TU REQUERIMIENTO) ---
    @Override
    public String getNextCorrelativeCode(String colorPrefix, String tipoFilamento) {
        Connection conn = null;
        int nextId = 0;

        // 1. CONSTRUCCIÓN DE LA CLAVE ÚNICA (PREFIJO)
        // Al combinar las 3 letras del color y el tipo, creamos un contador independiente para cada variante.
        // Ejemplo: "ROJ" + "-" + "PLA" = "ROJ-PLA"
        // Ejemplo: "AZU" + "-" + "PLA" = "AZU-PLA"
        String prefixKey = (colorPrefix.toUpperCase() + "-" + tipoFilamento.toUpperCase());

        try {
            conn = sqLiteManager.getConnection();
            conn.setAutoCommit(false); // Inicio de transacción

            // 2. Buscamos si ya existe un contador para ESTA combinación específica
            String selectSql = "SELECT last_number FROM supply_correlatives WHERE prefix = ?";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setString(1, prefixKey);
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    // CASO A: Ya existe (Ej: Ya había ROJ-PLA), incrementamos SU contador
                    int lastId = rs.getInt("last_number");
                    nextId = lastId + 1;

                    String updateSql = "UPDATE supply_correlatives SET last_number = ? WHERE prefix = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, nextId);
                        updateStmt.setString(2, prefixKey);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // CASO B: Es la primera vez para esta combinación (Ej: Primer AZU-PLA)
                    // Iniciamos el contador en 1 para esta nueva clave
                    nextId = 1;

                    String insertSql = "INSERT INTO supply_correlatives (prefix, last_number) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, prefixKey);
                        insertStmt.setInt(2, nextId);
                        insertStmt.executeUpdate();
                    }
                }
            }
            conn.commit(); // Fin de transacción

            // 3. Formateamos el código final: ROJ-PLA-01, ROJ-PLA-02, AZU-PLA-01...
            return String.format("%s-%02d", prefixKey, nextId);

        } catch (SQLException e) {
            System.err.println("Error de BD al generar correlativo de insumo: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RuntimeException("Error en persistencia de correlativo de insumo.", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    // --- Resto de Methods CRUD (Sin cambios) ---

    @Override
    public void add(Supply supply) {
        String sql = "INSERT INTO supply (code, name, tipoFilamento, colorFilamento, cantidadDisponible, umbralAlerta) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, supply.getCode());
            pstmt.setString(2, supply.getName());
            pstmt.setString(3, supply.getTipoFilamento());
            pstmt.setString(4, supply.getColorFilamento());
            pstmt.setDouble(5, supply.getCantidadDisponible());
            pstmt.setDouble(6, supply.getUmbralAlerta());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al agregar un insumo: " + e.getMessage());
        }
    }

    @Override
    public void modify(Supply supply) {
        String sql = "UPDATE supply SET code = ?, name = ?, tipoFilamento = ?, colorFilamento = ?, cantidadDisponible = ?, umbralAlerta = ? WHERE id = ?";
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, supply.getCode());
            pstmt.setString(2, supply.getName());
            pstmt.setString(3, supply.getTipoFilamento());
            pstmt.setString(4, supply.getColorFilamento());
            pstmt.setDouble(5, supply.getCantidadDisponible());
            pstmt.setDouble(6, supply.getUmbralAlerta());
            pstmt.setLong(7, supply.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al modificar un insumo: " + e.getMessage());
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM supply WHERE id = ?";
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al eliminar un insumo: " + e.getMessage());
        }
    }

    @Override
    public Supply findByID(long id) {
        String sql = "SELECT * FROM supply WHERE id = ?";
        try (Connection conn = sqLiteManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToSupply(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar insumo por ID: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Supply> listAll() {
        String sql = "SELECT * FROM supply";
        List<Supply> supplies = new ArrayList<>();
        try (Connection conn = sqLiteManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                supplies.add(mapResultSetToSupply(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error al listar todos los insumos: " + e.getMessage());
        }
        return supplies;
    }

    private Supply mapResultSetToSupply(ResultSet rs) throws SQLException {
        Supply supply = new Supply();
        supply.setId(rs.getLong("id"));
        supply.setCode(rs.getString("code"));
        supply.setName(rs.getString("name"));
        supply.setTipoFilamento(rs.getString("tipoFilamento"));
        supply.setColorFilamento(rs.getString("colorFilamento"));
        supply.setCantidadDisponible(rs.getDouble("cantidadDisponible"));
        supply.setUmbralAlerta(rs.getDouble("umbralAlerta"));
        return supply;
    }
}
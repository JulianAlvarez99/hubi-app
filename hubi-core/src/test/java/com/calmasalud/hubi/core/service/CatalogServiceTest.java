package com.calmasalud.hubi.core.service;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.model.PieceStockColorView;
import com.calmasalud.hubi.core.model.PieceStockDeduction;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.repository.ISupplyRepository; // <-- NUEVO IMPORT
import com.calmasalud.hubi.core.model.Supply; // <-- NUEVO IMPORT
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CatalogServiceTest {

    // --- 0. MOCK COMÚN PARA ISupplyRepository ---
    private final ISupplyRepository mockSupplyRepository = new ISupplyRepository() {
        @Override public void add(Supply supply) {}
        @Override public void modify(Supply supply) {}
        @Override public void delete(long id) {}
        @Override public Supply findByID(long id) { return null; }
        @Override public List<Supply> listAll() { return List.of(); }
        @Override public String getNextCorrelativeCode(String colorName, String tipoFilamento) { return null; }
    };

    @Test
    void testGenerarCodigoProducto() {

        // 1. Mock 1: Repositorio de PRODUCTOS (Pieza) - MOCK ANÓNIMO
        IProductRepository mockProductRepository = new IProductRepository() {
            @Override
            public String getNextCorrelative(String prefijoSeisLetras) {
                if ("SOPROJ".equals(prefijoSeisLetras)) {
                    return "001";
                }
                return "999";
            }
            @Override public long save(Product product) { return 1L; }
            @Override public Product findByCode(String code) { return null; }
            @Override public void deleteByCode(String code) {}

            // Stubs de la interfaz IProductRepository
            @Override public List<Product> findPiecesByMasterPrefix(String masterPrefix) { return new ArrayList<>(); }
            @Override public int getPieceStockQuantity(String pieceNameBase) { return 0; }
            @Override public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {}
            @Override public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) { return new ArrayList<>(); }
            @Override public void deletePieceStockByPieceNameBase(String pieceNameBase) {}
            @Override public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity){}
            @Override public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {}
        };

        // 2. Mock 2: Repositorio MAESTRO - MOCK ANÓNIMO (CORREGIDO)
        IMasterProductRepository mockMasterProductRepository = new IMasterProductRepository() {
            @Override public String getPrefixFromName(String productName) { return null; }
            @Override public String getNextMasterCode(String masterPrefix) { return null; }
            @Override public long saveNewProduct(MasterProduct product, double initialPrice) { return 0; }
            @Override public void increaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByMasterCode(String masterCode) { return null; }
            @Override public List<MasterProduct> findAll() { return List.of(); }
            @Override public long save(MasterProduct product) { return 0; }
            @Override public void deleteProduct(String masterCode) {}
            @Override public void decreaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByProductName(String productName) { return null; }
            @Override public MasterProduct findByProductPrefix(String prefix) { return null; }
        };

        // 3. Mock 3: Repositorio de COMPOSICIÓN (BOM) (CORREGIDO)
        IProductCompositionRepository mockCompositionRepository = new IProductCompositionRepository() {
            @Override public void saveComposition(String masterCode, List<ProductComposition> composition) {}
            @Override public List<ProductComposition> getComposition(String masterCode) { return List.of(); }
            @Override public boolean compositionExists(String masterCode) { return false; }
        };


        // 4. Crear el servicio, inyectando los CUATRO Mocks.
        CatalogService service = new CatalogService(mockProductRepository, mockMasterProductRepository, mockCompositionRepository, mockSupplyRepository); // <-- ¡AQUÍ ESTÁ EL FIX!

        // 5. Ejecutar y validar la prueba original...
        String codigo = service.generateProductCode("Soporte");
        assertEquals("SOPROJ001", codigo, "El código generado debe coincidir con el formato esperado.");

        // Pruebas de excepciones
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateProductCode("So");
        }, "Debería lanzar excepción si el nombre es menor a 3 caracteres.");
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateProductCode(null);
        }, "Debería lanzar excepción si el nombre es nulo.");
    }

    // --------------------------------------------------------------------------
    // TEST PARA LA NUEVA FUNCIONALIDAD DE ELIMINACIÓN DE STOCK POR COMPOSICIÓN
    // --------------------------------------------------------------------------

    @Test
    void testDeleteProductStockByComposition_Success() throws IOException {
        String masterCode = "LAV001";
        List<PieceStockDeduction> deductions = Arrays.asList(
                new PieceStockDeduction("LavadoraBase", "ROJO", 2),
                new PieceStockDeduction("LavadoraBase", "AZUL", 2)
        );

        // 1. Mock de Repositorio de Productos para simular éxito
        IProductRepository mockProductRepositorySuccess = new IProductRepository() {
            @Override public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {} // Simula éxito
            // Stubs para el resto de los Methods
            @Override public String getNextCorrelative(String prefijoSeisLetras) { return null; }
            @Override public long save(Product product) { return 0; }
            @Override public Product findByCode(String code) { return null; }
            @Override public void deleteByCode(String code) {}
            @Override public List<Product> findPiecesByMasterPrefix(String masterPrefix) { return List.of(); }
            @Override public int getPieceStockQuantity(String pieceNameBase) { return 0; }
            @Override public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {}
            @Override public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) { return List.of(); }
            @Override public void deletePieceStockByPieceNameBase(String pieceNameBase) {}
            @Override public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity){}
        };

        // 2. Mock de Repositorios adicionales (CORREGIDO)
        IMasterProductRepository mockMasterProductRepository = new IMasterProductRepository() {
            @Override public String getPrefixFromName(String productName) { return null; }
            @Override public String getNextMasterCode(String masterPrefix) { return null; }
            @Override public long saveNewProduct(MasterProduct product, double initialPrice) { return 0; }
            @Override public void increaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByMasterCode(String masterCode) { return null; }
            @Override public List<MasterProduct> findAll() { return List.of(); }
            @Override public long save(MasterProduct product) { return 0; }
            @Override public void deleteProduct(String masterCode) {}
            @Override public void decreaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByProductName(String productName) { return null; }
            @Override public MasterProduct findByProductPrefix(String prefix) { return null; }
        };
        IProductCompositionRepository mockCompositionRepository = new IProductCompositionRepository() {
            @Override public void saveComposition(String masterCode, List<ProductComposition> composition) {}
            @Override public List<ProductComposition> getComposition(String masterCode) { return List.of(); }
            @Override public boolean compositionExists(String masterCode) { return false; }
        };

        CatalogService service = new CatalogService(mockProductRepositorySuccess, mockMasterProductRepository, mockCompositionRepository, mockSupplyRepository); // <-- ¡AQUÍ ESTÁ EL FIX!


        // 3. Ejecutar y validar que NO lanza excepción
        assertDoesNotThrow(() -> service.deleteProductStockByComposition(masterCode, deductions));
    }

    @Test
    void testDeleteProductStockByComposition_InsufficientStock_ThrowsExceptionAndRollbackIsImplied() throws Exception {
        String masterCode = "LAV001";
        List<PieceStockDeduction> deductions = Arrays.asList(
                new PieceStockDeduction("LavadoraBase", "ROJO", 2),
                new PieceStockDeduction("LavadoraBase", "AZUL", 2)
        );

        // 1. Mock de Repositorio de Productos para simular FALLO (decreasePieceStockBatch)
        IProductRepository mockProductRepositoryFail = new IProductRepository() {
            // Implementación del Method transaccional para el test: FALLO
            @Override
            public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {
                // Simulación de fallo: lanza RuntimeException
                throw new RuntimeException("Stock insuficiente forzado para test.");
            }
            // Stubs para el resto de los Methods
            @Override public String getNextCorrelative(String prefijoSeisLetras) { return null; }
            @Override public long save(Product product) { return 0; }
            @Override public Product findByCode(String code) { return null; }
            @Override public void deleteByCode(String code) {}
            @Override public List<Product> findPiecesByMasterPrefix(String masterPrefix) { return List.of(); }
            @Override public int getPieceStockQuantity(String pieceNameBase) { return 0; }
            @Override public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {}
            @Override public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) { return List.of(); }
            @Override public void deletePieceStockByPieceNameBase(String pieceNameBase) {}
            @Override public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity){}
        };

        // 2. Crear el servicio, inyectando los Mocks.
        IMasterProductRepository mockMasterProductRepository = new IMasterProductRepository() {
            @Override public String getPrefixFromName(String productName) { return null; }
            @Override public String getNextMasterCode(String masterPrefix) { return null; }
            @Override public long saveNewProduct(MasterProduct product, double initialPrice) { return 0; }
            @Override public void increaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByMasterCode(String masterCode) { return null; }
            @Override public List<MasterProduct> findAll() { return List.of(); }
            @Override public long save(MasterProduct product) { return 0; }
            @Override public void deleteProduct(String masterCode) {}
            @Override public void decreaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByProductName(String productName) { return null; }
            @Override public MasterProduct findByProductPrefix(String prefix) { return null; }
        };
        IProductCompositionRepository mockCompositionRepository = new IProductCompositionRepository() {
            @Override public void saveComposition(String masterCode, List<ProductComposition> composition) {}
            @Override public List<ProductComposition> getComposition(String masterCode) { return List.of(); }
            @Override public boolean compositionExists(String masterCode) { return false; }
        };

        // 3. Crear el servicio con el mock de fallo Y el mock de Supply.
        CatalogService service = new CatalogService(mockProductRepositoryFail, mockMasterProductRepository, mockCompositionRepository, mockSupplyRepository); // <-- ¡AQUÍ ESTÁ EL FIX!

        // 4. Ejecutar y Verificar Excepción
        assertThrows(IOException.class, () -> {
            service.deleteProductStockByComposition(masterCode, deductions);
        }, "Debería lanzar IOException al fallar la deducción transaccional.");
    }
}
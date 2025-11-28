package com.calmasalud.hubi.core.service;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.model.PieceStockColorView;
import com.calmasalud.hubi.core.model.PieceStockDeduction;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.repository.ISupplyRepository;
import com.calmasalud.hubi.core.model.Supply;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CatalogServiceTest {

    // --- 0. MOCK COMÚN PARA ISupplyRepository (ADAPTADO para registrar llamadas) ---
    private final AtomicInteger deleteCallCount = new AtomicInteger(0);
    private final AtomicInteger modifyCallCount = new AtomicInteger(0);
    private final AtomicReference<Supply> modifiedSupplyRef = new AtomicReference<>();

    // Mocks de datos específicos para los nuevos tests (para findByID)
    private final Supply supplyForTest1 = new Supply(1L, "AZUPLA001", "Filamento Azul PLA", "PLA", "Azul", 0.0, 50.0); // Stock 0
    private final Supply supplyForTest2 = new Supply(2L, "VERABS001", "Filamento Verde ABS", "ABS", "Verde", 100.5, 50.0); // Stock > 0
    private final Supply supplyForTest3 = new Supply(3L, "ROJPLA001", "Filamento Rojo PLA", "PLA", "Rojo", 10.0, 50.0); // Stock 10.0
    private final Supply supplyForTest4 = new Supply(4L, "GRIPLA001", "Filamento Gris PLA", "PLA", "Gris", 500.0, 50.0); // Nuevo para getSupplyById

    private final ISupplyRepository mockSupplyRepository = new ISupplyRepository() {
        @Override public void add(Supply supply) {}

        @Override public void modify(Supply supply) {
            modifyCallCount.incrementAndGet();
            modifiedSupplyRef.set(supply); // Capturar el objeto modificado
        }

        @Override public void delete(long id) {
            deleteCallCount.incrementAndGet();
        }

        @Override public Supply findByID(long id) {
            if (id == 1L) return supplyForTest1;
            if (id == 2L) return supplyForTest2;
            if (id == 3L) return supplyForTest3;
            if (id == 4L) return supplyForTest4; // Devuelve el nuevo Supply
            return null;
        }

        @Override public List<Supply> listAll() { return List.of(); }
        @Override public String getNextCorrelativeCode(String colorName, String tipoFilamento) { return null; }
    };

    // Método auxiliar para restablecer los contadores y datos antes de cada prueba de insumos
    private void resetSupplyMockState() {
        deleteCallCount.set(0);
        modifyCallCount.set(0);
        modifiedSupplyRef.set(null);
        // Es crucial resetear la cantidad disponible si la modificamos en los tests
        supplyForTest1.setCantidadDisponible(0.0);
        supplyForTest2.setCantidadDisponible(100.5);
        supplyForTest3.setCantidadDisponible(10.0);
        supplyForTest4.setCantidadDisponible(500.0);
    }

    // Mock genérico para dependencias que no se usan en los tests de insumo
    private IProductRepository createProductRepoStub() {
        return new IProductRepository() {
            @Override public String getNextCorrelative(String prefijoSeisLetras) { return null; }
            @Override public long save(Product product) { return 0; }
            @Override public Product findByCode(String code) { return null; }
            @Override public void deleteByCode(String code) {}
            @Override public List<Product> findPiecesByMasterPrefix(String masterPrefix) { return new ArrayList<>(); }
            @Override public int getPieceStockQuantity(String pieceNameBase) { return 0; }
            @Override public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {}
            @Override public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) { return new ArrayList<>(); }
            @Override public void deletePieceStockByPieceNameBase(String pieceNameBase) {}
            @Override public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity){}
            @Override public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {}
        };
    }

    private IMasterProductRepository createMasterProductRepoStub() {
        return new IMasterProductRepository() {
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
    }

    private IProductCompositionRepository createCompositionRepoStub() {
        return new IProductCompositionRepository() {
            @Override public void saveComposition(String masterCode, List<ProductComposition> composition) {}
            @Override public List<ProductComposition> getComposition(String masterCode) { return List.of(); }
            @Override public boolean compositionExists(String masterCode) { return false; }
        };
    }


    @Test
    void testGenerarCodigoProducto() {

        IProductRepository mockProductRepository = createProductRepoStub();

        IProductRepository productRepositoryWithCorrelative = new IProductRepository() {
            @Override public String getNextCorrelative(String prefijoSeisLetras) {
                if ("SOPROJ".equals(prefijoSeisLetras)) {
                    return "001";
                }
                return "999";
            }
            @Override public long save(Product product) { return 1L; }
            @Override public Product findByCode(String code) { return null; }
            @Override public void deleteByCode(String code) {}
            @Override public List<Product> findPiecesByMasterPrefix(String masterPrefix) { return new ArrayList<>(); }
            @Override public int getPieceStockQuantity(String pieceNameBase) { return 0; }
            @Override public void increasePieceStockQuantity(String pieceNameBase, String colorName, int quantity) {}
            @Override public List<PieceStockColorView> getStockByPieceNameBase(String pieceNameBase) { return new ArrayList<>(); }
            @Override public void deletePieceStockByPieceNameBase(String pieceNameBase) {}
            @Override public void decreasePieceStockQuantity(String pieceNameBase, String colorName, int quantity){}
            @Override public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {}
        };

        IMasterProductRepository mockMasterProductRepository = createMasterProductRepoStub();
        IProductCompositionRepository mockCompositionRepository = createCompositionRepoStub();

        CatalogService service = new CatalogService(productRepositoryWithCorrelative, mockMasterProductRepository, mockCompositionRepository, mockSupplyRepository);

        String codigo = service.generateProductCode("Soporte");
        assertEquals("SOPROJ001", codigo, "El código generado debe coincidir con el formato esperado.");

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

        IProductRepository mockProductRepositorySuccess = new IProductRepository() {
            @Override public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {}
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

        IMasterProductRepository mockMasterProductRepository = createMasterProductRepoStub();
        IProductCompositionRepository mockCompositionRepository = createCompositionRepoStub();

        CatalogService service = new CatalogService(mockProductRepositorySuccess, mockMasterProductRepository, mockCompositionRepository, mockSupplyRepository);


        assertDoesNotThrow(() -> service.deleteProductStockByComposition(masterCode, deductions));
    }

    @Test
    void testDeleteProductStockByComposition_InsufficientStock_ThrowsExceptionAndRollbackIsImplied() throws Exception {
        String masterCode = "LAV001";
        List<PieceStockDeduction> deductions = Arrays.asList(
                new PieceStockDeduction("LavadoraBase", "ROJO", 2),
                new PieceStockDeduction("LavadoraBase", "AZUL", 2)
        );

        IProductRepository mockProductRepositoryFail = new IProductRepository() {
            @Override
            public void decreasePieceStockBatch(List<PieceStockDeduction> deductions) {
                throw new RuntimeException("Stock insuficiente forzado para test.");
            }
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

        IMasterProductRepository mockMasterProductRepository = createMasterProductRepoStub();
        IProductCompositionRepository mockCompositionRepository = createCompositionRepoStub();

        CatalogService service = new CatalogService(mockProductRepositoryFail, mockMasterProductRepository, mockCompositionRepository, mockSupplyRepository);

        assertThrows(IOException.class, () -> {
            service.deleteProductStockByComposition(masterCode, deductions);
        }, "Debería lanzar IOException al fallar la deducción transaccional.");
    }

    // --------------------------------------------------------------------------
    // TEST PARA LA NUEVA FUNCIONALIDAD DE INSUMOS
    // --------------------------------------------------------------------------

    @Test
    void testGetSupplyById_Success() {
        resetSupplyMockState();
        long supplyId = 4L;

        CatalogService service = new CatalogService(createProductRepoStub(), createMasterProductRepoStub(), createCompositionRepoStub(), mockSupplyRepository);

        Supply result = service.getSupplyById(supplyId);

        assertEquals(supplyId, result.getId());
        assertEquals("Gris", result.getColorFilamento());
        assertEquals(500.0, result.getCantidadDisponible());
    }

    // --- TEST MODIFICADO: Ahora pasa false (safe) con stock 0 ---
    @Test
    void deleteSupplyPermanently_StockZero_Success() {
        resetSupplyMockState();
        long supplyId = 1L; // Stock 0

        CatalogService service = new CatalogService(createProductRepoStub(), createMasterProductRepoStub(), createCompositionRepoStub(), mockSupplyRepository);

        // ACT: Borrado seguro (false)
        assertDoesNotThrow(() -> service.deleteSupplyPermanently(supplyId, false));

        // ASSERT
        assertEquals(1, deleteCallCount.get(), "La eliminación debe ser llamada 1 vez.");
    }

    // --- TEST MODIFICADO: Ahora pasa false (safe) y lanza excepción si hay stock ---
    @Test
    void deleteSupplyPermanently_StockGreaterThanZero_ThrowsException() {
        resetSupplyMockState();
        long supplyId = 2L; // Stock > 0

        CatalogService service = new CatalogService(createProductRepoStub(), createMasterProductRepoStub(), createCompositionRepoStub(), mockSupplyRepository);

        // ACT & ASSERT: Borrado seguro (false)
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.deleteSupplyPermanently(supplyId, false);
        });

        assertTrue(exception.getMessage().contains("No se puede eliminar un insumo que tiene stock disponible"));
        assertEquals(0, deleteCallCount.get(), "La eliminación no debe ser llamada.");
    }

    // --- NUEVO TEST: Elimina con stock usando el forzado (true) ---
    @Test
    void deleteSupplyPermanently_ForceDeletion_StockGreaterThanZero_Success() {
        resetSupplyMockState();
        long supplyId = 2L; // Stock > 0

        CatalogService service = new CatalogService(createProductRepoStub(), createMasterProductRepoStub(), createCompositionRepoStub(), mockSupplyRepository);

        // ACT: Borrado forzado (true)
        assertDoesNotThrow(() -> service.deleteSupplyPermanently(supplyId, true));

        // ASSERT
        assertEquals(1, deleteCallCount.get(), "La eliminación debe ser llamada 1 vez cuando se fuerza.");
    }

    @Test
    void deleteSupplyPermanently_SupplyNotFound_ThrowsException() {
        resetSupplyMockState();
        long nonExistentId = 99L;

        CatalogService service = new CatalogService(createProductRepoStub(), createMasterProductRepoStub(), createCompositionRepoStub(), mockSupplyRepository);

        // ACT & ASSERT
        assertThrows(IllegalArgumentException.class, () -> {
            service.deleteSupplyPermanently(nonExistentId, false); // Se prueba con false, el resultado es el mismo
        });
        assertEquals(0, deleteCallCount.get(), "La eliminación no debe ser llamada.");
    }

    @Test
    void removeSupplyStock_ReachesZero_SupplyIsNotDeleted() {
        resetSupplyMockState();
        long supplyId = 3L; // Stock inicial: 10.0g

        CatalogService service = new CatalogService(createProductRepoStub(), createMasterProductRepoStub(), createCompositionRepoStub(), mockSupplyRepository);

        // ACT
        assertDoesNotThrow(() -> service.removeSupplyStock(supplyId, 10.0)); // Descontar todo

        // ASSERT
        assertEquals(0, deleteCallCount.get(), "La eliminación no debe ser llamada.");

        assertEquals(1, modifyCallCount.get(), "La modificación debe ser llamada 1 vez.");

        Supply modifiedSupply = modifiedSupplyRef.get();
        assertTrue(modifiedSupply.getCantidadDisponible() < 0.01, "El stock modificado debe ser 0.0.");
    }
}
package com.calmasalud.hubi.core.service;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogServiceTest {

    @Test
    void testGenerarCodigoProducto() {

        // 1. Mock 1: Repositorio de PRODUCTOS (Pieza)
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
            @Override
            public List<Product> findPiecesByMasterPrefix(String masterPrefix) {
                return new ArrayList<>();
            }
            @Override
            public int getPieceStockQuantity(String pieceNameBase) {
                return 0;
            }
            @Override
            public void increasePieceStockQuantity(String pieceNameBase, int quantity) {
                // Stub para satisfacer el contrato
            }
        };

        // 2. Mock 2: Repositorio MAESTRO
        IMasterProductRepository mockMasterProductRepository = new IMasterProductRepository() {
            @Override public String getPrefixFromName(String productName) { return null; }
            @Override public String getNextMasterCode(String masterPrefix) { return null; }
            @Override public long saveNewProduct(MasterProduct product, double initialPrice) { return 0; }
            @Override public void increaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByMasterCode(String masterCode) { return null; }
            @Override public List<MasterProduct> findAll() { return List.of(); }
            @Override public long save(MasterProduct product) { return 0; }
            @Override public void deleteProduct(String masterCode) {}
            @Override public MasterProduct findByProductName(String productName) { return null; }

            // <--- STUBS REQUERIDOS PARA EL CONTRATO --->

            @Override public void decreaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByProductPrefix(String prefix) { return null; }
            // <---------------------------------------->
        };

        // 3. Mock 3: Repositorio de COMPOSICIÓN (BOM)
        IProductCompositionRepository mockCompositionRepository = new IProductCompositionRepository() {
            @Override public void saveComposition(String masterCode, List<ProductComposition> composition) {}
            @Override public List<ProductComposition> getComposition(String masterCode) { return List.of(); }
            @Override public boolean compositionExists(String masterCode) { return false; }
        };


        // 4. Crear el servicio, inyectando los TRES Mocks.
        CatalogService service = new CatalogService(mockProductRepository, mockMasterProductRepository, mockCompositionRepository);

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
}
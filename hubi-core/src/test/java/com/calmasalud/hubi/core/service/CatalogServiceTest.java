package com.calmasalud.hubi.core.service;

import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows; // Importar si se añaden pruebas de excepciones

class CatalogServiceTest {

    @Test
    void testGenerarCodigoProducto() {

        // 1. Crear una implementación simulada (Mock) de la interfaz de repositorio de PRODUCTOS.
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

            /**
             * IMPLEMENTACIÓN AÑADIDA: Resuelve el error de compilación.
             * Devuelve una lista vacía, ya que esta prueba no necesita piezas reales.
             */
            @Override
            public List<Product> findPiecesByMasterPrefix(String masterPrefix) {
                return new ArrayList<>();
            }
        };

        // 2. NUEVO: Crear un STUB/MOCK para la nueva interfaz IMasterProductRepository.
        IMasterProductRepository mockMasterProductRepository = new IMasterProductRepository() {
            // Stubs para evitar errores de compilación
            @Override public String getPrefixFromName(String productName) { return null; }
            @Override public String getNextMasterCode(String masterPrefix) { return null; }
            @Override public long saveNewProduct(MasterProduct product, double initialPrice) { return 0; }
            @Override public void increaseStock(String masterCode, int quantity) {}
            @Override public MasterProduct findByMasterCode(String masterCode) { return null; }
            @Override public List<MasterProduct> findAll() { return List.of(); }
            @Override public long save(MasterProduct product) { return 0; } // Añadido para el contrato completo
        };


        // 3. Crear el servicio, inyectando AMBOS Mocks.
        CatalogService service = new CatalogService(mockProductRepository, mockMasterProductRepository);

        // 4. Ejecutar el método a probar y validar...
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

package com.calmasalud.hubi.core.service;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*class CatalogoServiceTest {

    @Test
    void testGenerarCodigoProducto() {
        CatalogService service = new CatalogService();
        String codigo = service.generateProductCode("Soporte", "Rojo", true);

        // Valida contra el ejemplo de tu propia especificación [cite: 773]
        assertEquals("SOPROJ-0-001", codigo);
    }
}*/
class CatalogoServiceTest {

    @Test
    void testGenerarCodigoProducto() {

        // 1. Crear una implementación simulada (Mock) de la interfaz de repositorio.
        //    Esto es necesario para satisfacer el constructor con DI.
        IProductRepository mockRepository = new IProductRepository() {

            // ----------------------------------------------------
            // IMPLEMENTACIÓN 1: getNextCorrelative
            // La lógica de generateProductCode necesita llamar a este método.
            // ----------------------------------------------------
            @Override
            public String getNextCorrelative(String prefijoSeisLetras) {
                // Simulamos que el siguiente correlativo es '001' (la primera vez que se llama).
                return "001";
            }

            // ----------------------------------------------------
            // IMPLEMENTACIÓN 2: save(Product product)
            // Requerida por la interfaz, pero vacía para la prueba unitaria.
            // ----------------------------------------------------
            @Override
            public long save(Product product) {
                // Devolvemos un ID simulado para evitar errores de compilación.
                return 1L;
            }

            // ----------------------------------------------------
            // IMPLEMENTACIÓN 3: findByCode(String code)
            // Requerida por la interfaz, pero vacía para la prueba unitaria.
            // ----------------------------------------------------
            @Override
            public Product findByCode(String code) {
                return null;
            }
        };

        // 2. Crear el servicio, inyectando el Mock
        //    (Satisfaciendo el constructor con DI: new CatalogService(IProductRepository))
        CatalogService service = new CatalogService(mockRepository);

        // 3. Ejecutar el método a probar
        String codigo = service.generateProductCode("Soporte", "Rojo", true);

        // 4. Valida contra el ejemplo de tu propia especificación
        //    SOP (Soporte) + ROJ (Rojo) + 0 (Pieza) + 001 (Correlativo)
        assertEquals("SOPROJ-0-001", codigo, "El código generado debe coincidir con el formato esperado.");
    }
}
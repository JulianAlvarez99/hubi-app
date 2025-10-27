package com.calmasalud.hubi.core.service;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows; // Importar si se añaden pruebas de excepciones

class CatalogServiceTest {

    @Test
    void testGenerarCodigoProducto() {

        // 1. Crear una implementación simulada (Mock) de la interfaz de repositorio.
        IProductRepository mockRepository = new IProductRepository() {

            // Implementación simulada de getNextCorrelative
            @Override
            public String getNextCorrelative(String prefijoSeisLetras) {
                // Simulamos que el siguiente correlativo es '001'.
                if ("SOPROJ".equals(prefijoSeisLetras)) { // Ser más específico si es necesario
                    return "001";
                }
                return "999"; // Valor por defecto para otros prefijos si aparecen
            }

            // Implementación simulada de save (vacía para esta prueba)
            @Override
            public long save(Product product) {
                return 1L; // Devolver un ID simulado
            }

            // Implementación simulada de findByCode (vacía para esta prueba)
            @Override
            public Product findByCode(String code) {
                return null; // No necesitamos encontrar productos en esta prueba
            }

            // --- IMPLEMENTACIÓN FALTANTE AÑADIDA ---
            @Override
            public void deleteByCode(String code) {
                // Vacío para este mock, ya que la prueba generateProductCode no lo usa.
                // Simplemente cumple con el contrato de la interfaz.
                System.out.println("Mock deleteByCode llamado con código: " + code); // Opcional: para debugging
            }
            // -----------------------------------------
        };

        // 2. Crear el servicio, inyectando el Mock
        CatalogService service = new CatalogService(mockRepository);

        // 3. Ejecutar el method a probar
        //    (Ajustado para reflejar la simplificación del method generateProductCode)
        String codigo = service.generateProductCode("Soporte"); // Ya no necesita color ni boolean

        // 4. Validar contra el formato esperado (sin el -0-)
        //    SOP (Soporte) + ROJ (Color por defecto) + 001 (Correlativo)
        assertEquals("SOPROJ001", codigo, "El código generado debe coincidir con el formato esperado.");

        // Prueba adicional: Nombre corto debería lanzar excepción
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateProductCode("So");
        }, "Debería lanzar excepción si el nombre es menor a 3 caracteres.");
        // Prueba adicional: Nombre nulo debería lanzar excepción
        assertThrows(IllegalArgumentException.class, () -> {
            service.generateProductCode(null);
        }, "Debería lanzar excepción si el nombre es nulo.");

    }

    // Podrías añadir más tests aquí para probar otros métodos de CatalogService
    // como procesarCargaProducto, procesarCargaPieza, deletePiece, deleteProduct, etc.
    // Cada uno requeriría mocks apropiados para IProductRepository y quizás mocks
    // para operaciones de sistema de archivos si quieres aislarlas.
}
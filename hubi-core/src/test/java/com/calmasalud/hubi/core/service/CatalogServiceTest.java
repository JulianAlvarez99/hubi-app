package com.calmasalud.hubi.core.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogoServiceTest {

    @Test
    void testGenerarCodigoProducto() {
        CatalogService service = new CatalogService();
        String codigo = service.generateProductCode("Soporte", "Rojo", true);

        // Valida contra el ejemplo de tu propia especificación [cite: 773]
        assertEquals("SOPROJ-0-001", codigo);
    }
}
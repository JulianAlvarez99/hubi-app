package com.calmasalud.hubi.core.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CatalogService {

    // (Más adelante inyectarás los repositorios aquí)
    // Se define la ubicación base de forma portable
    private static final Path REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos");
    /**
     * Implementa la lógica de RF8 para generar códigos únicos.
     * [cite: 771-779]
     */
    public String generateProductCode(String productName, String color, boolean isPart) {
        String prefijoProd = productName.substring(0, 3).toUpperCase();
        String prefijoColor = color.substring(0, 3).toUpperCase();
        String tipo = isPart ? "0" : "1";

        // (Aquí faltaría la lógica para buscar el último número correlativo,
        // que vendrá del IProductoRepository)
        String correlativo = "001";

        return String.format("%s%s-%s-%s", prefijoProd, prefijoColor, tipo, correlativo);
    }
    public File copiarArchivoARepositorio(File archivoOrigen) throws IOException {

        // Validación de la Extensión (RF1)
        String nombre = archivoOrigen.getName().toLowerCase();
        if (!nombre.endsWith(".stl") && !nombre.endsWith(".3mf") && !nombre.endsWith(".gcode")) {
            throw new IOException("Error: El archivo debe ser .stl o .3mf.");
        }

        // Se debe asegurar la creación del Directorio Destino
        // Se crea si no existe.
        Files.createDirectories(REPOSITORIO_BASE);

        // Definición de Rutas
        Path rutaOrigen = archivoOrigen.toPath();
        Path rutaDestinoFinal = REPOSITORIO_BASE.resolve(rutaOrigen.getFileName());

        // La operación de copiar el Archivo  será una Operación atómica
        // REPLACE_EXISTING es para evitar que falle si el usuario sube el mismo archivo dos veces.
        Files.copy(rutaOrigen, rutaDestinoFinal, StandardCopyOption.REPLACE_EXISTING);

        return rutaDestinoFinal.toFile();
    }
    /* Procesa la carga de un archivo, copiándolo al repositorio con la estructura de directorios
    y la asignación de códigos única (RF8) solicitada.
            */
    // Método 1: Para Carga como PRODUCTO (Siempre crea un nuevo directorio)
    public void procesarCargaProducto(File archivoOrigen, String nombreProducto) throws IOException {
        // 1. Crear la ruta de destino (Nuevo Directorio)
        Path directorioProducto = REPOSITORIO_BASE.resolve(nombreProducto);

        // 2. Crear el directorio (Falla si ya existe, a menos que uses REPLACE_EXISTING en la copia)
        Files.createDirectories(directorioProducto);

        // 3. Copia el archivo al nuevo directorio
        Files.copy(archivoOrigen.toPath(), directorioProducto.resolve(archivoOrigen.getName()), StandardCopyOption.REPLACE_EXISTING);

        // 4. Asignación de Código Único (RF8) para Producto (Lógica pendiente)
        // ...
    }

    // Método 2: Para Carga como PIEZA (Crea o incorpora)
    public void procesarCargaPieza(File archivoOrigen, String rutaODiretorio) throws IOException {

        Path directorioDestino;
        File posibleDirectorio = new File(rutaODiretorio);

        // A. Si el usuario seleccionó un directorio existente (ruta completa)
        if (posibleDirectorio.isDirectory()) {
            directorioDestino = posibleDirectorio.toPath();
        }
        // B. Si el usuario ingresó un nombre de producto NUEVO (ruta corta)
        else {
            // Lógica de "primera pieza" (crea el directorio)
            directorioDestino = REPOSITORIO_BASE.resolve(rutaODiretorio);
            Files.createDirectories(directorioDestino);
        }

        // 2. Copia el archivo
        Files.copy(archivoOrigen.toPath(), directorioDestino.resolve(archivoOrigen.getName()), StandardCopyOption.REPLACE_EXISTING);

        // 3. Asignación de Subcódigo (RF8) para Pieza (Lógica pendiente)
        // ...
    }
}
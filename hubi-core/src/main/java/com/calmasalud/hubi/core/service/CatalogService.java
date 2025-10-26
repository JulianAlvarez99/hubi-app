package com.calmasalud.hubi.core.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.Supply;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;
import java.util.Comparator;

public class CatalogService {

    private final IProductRepository productRepository = new ProductRepositorySQLite();
    // Se define la ubicación base de forma portable
    private static final Path REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos");

    //Color por defecto
    private static final String COLOR_POR_DEFECTO = "ROJO";

    /**
     * Implementa la lógica de RF8 para generar códigos únicos.
     *  FORMATO: [PROD 3 letras][COLOR 3 letras][CORRELATIVO]
     */
    public String generateProductCode(String productName, String color) {

        // --- RF8: Validación de entradas mínimas ---
        if (productName == null || productName.length() < 3) {
            throw new IllegalArgumentException("El nombre del producto debe tener al menos 3 caracteres.");
        }
        if (color == null || color.length() < 3) {
            throw new IllegalArgumentException("El color debe tener al menos 3 caracteres.");
        }

        String prefijoProd = productName.substring(0, 3).toUpperCase();
        String prefijoColor = color.substring(0, 3).toUpperCase();

        String prefijoSeisLetras = prefijoProd + prefijoColor;

        // RF8: Obtiene el siguiente correlativo único de la base de datos (SQLite)
        String correlativo = productRepository.getNextCorrelative(prefijoSeisLetras);

        // Retorna el código completo
        return String.format("%s%s", prefijoSeisLetras, correlativo);
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

    // Método 1: Para Carga como PRODUCTO (Crea la carpeta y la primera pieza)
    public void procesarCargaProducto(File archivoOrigen, String nombreProducto) throws IOException {

        // Generar el código único (RF8) usando el COLOR POR DEFECTO
        String codigoUnicoProducto = generateProductCode(nombreProducto, COLOR_POR_DEFECTO);
        String nombreArchivoOriginal = archivoOrigen.getName(); // <-- Captura el nombre del archivo
        String extension = getFileExtension(nombreArchivoOriginal);

        // Crear el objeto Product
        Product newProduct = new Product(codigoUnicoProducto, nombreArchivoOriginal, extension);
        // Persistir el objeto en la base de datos
        long id = productRepository.save(newProduct);
        if (id == -1) {
            throw new IOException("Error: No se pudo guardar el producto en la base de datos.");
        }

        // Mover el archivo al repositorio físico (usando el código único como nombre)
        Path directorioProducto = REPOSITORIO_BASE.resolve(nombreProducto);
        // Crear el directorio
        Files.createDirectories(directorioProducto);
        String nombreArchivoFinal = codigoUnicoProducto + extension;

        // Copia el archivo al nuevo directorio con el nombre único
        Files.copy(archivoOrigen.toPath(), directorioProducto.resolve(nombreArchivoFinal), StandardCopyOption.REPLACE_EXISTING);
    }

    // Método 2: Para Carga como PIEZA (Asociación a un Producto existente)
    public void procesarCargaPieza(File archivoOrigen, String rutaODiretorio) throws IOException {

        Path directorioDestino;
        File posibleDirectorio = new File(rutaODiretorio);
        String nombreProducto;
        String nombreArchivoOriginal = archivoOrigen.getName(); // <-- Captura el nombre del archivo

        if (!posibleDirectorio.isDirectory()) {
            throw new IllegalArgumentException("La ruta proporcionada para la pieza no es un directorio válido de Producto.");
        }

        directorioDestino = posibleDirectorio.toPath();
        nombreProducto = posibleDirectorio.getName();

        // Generar el código único (RF8) para la Pieza
        String codigoUnicoPieza = generateProductCode(nombreProducto, COLOR_POR_DEFECTO);
        String extension = getFileExtension(archivoOrigen.getName());

        // Crear el objeto Product
        Product newPiece = new Product(codigoUnicoPieza, nombreArchivoOriginal, extension);
        // Persistir el objeto en la base de datos
        long id = productRepository.save(newPiece);
        if (id == -1) {
            throw new IOException("Error: No se pudo guardar la pieza en la base de datos.");
        }

        // Mover el archivo al repositorio físico (usando el código único como nombre)
        String nombreArchivoFinal = codigoUnicoPieza + extension;

        // Copia el archivo con el nombre único
        Files.copy(archivoOrigen.toPath(), directorioDestino.resolve(nombreArchivoFinal), StandardCopyOption.REPLACE_EXISTING);
    }
     /* Función auxiliar para obtener la extensión de un archivo.
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return filename.substring(dotIndex);
    }

    /**
     * Busca los detalles completos de un producto o pieza por su código único.
     * @param code El código único (Ej: SOPROJ001)
     * @return El objeto Product o null si no se encuentra.
     */
    public Product getProductDetails(String code) {
        return productRepository.findByCode(code);
    }
    public void deletePiece(File pieceFile) throws IOException {
        String fileName = pieceFile.getName();

        // Safety check para asegurar que tiene formato de código único.
        if (fileName.lastIndexOf('.') == -1) {
            throw new IOException("Error: Archivo de pieza con formato de nombre inválido.");
        }

        String code = fileName.substring(0, fileName.lastIndexOf('.'));
        Path parentPath = pieceFile.getParentFile().toPath(); // Obtenemos la carpeta del Producto

        // 1. Eliminar de la BD
        productRepository.deleteByCode(code);

        // 2. Eliminar del Disco
        if (pieceFile.exists()) {
            Files.delete(pieceFile.toPath());
            System.out.println("✅ Pieza eliminada del disco y BD: " + code);
        } else {
            System.out.println("⚠️ Advertencia: Pieza eliminada de BD, pero archivo no encontrado: " + code);
        }

        // 3. NUEVA LÓGICA: ELIMINAR DIRECTORIO PADRE SI QUEDA VACÍO

        if (pieceFile.getParentFile().exists()) {
            // Verificamos si el directorio está vacío
            boolean isEmpty = true;
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(parentPath)) {
                if (dirStream.iterator().hasNext()) {
                    isEmpty = false;
                }
            }

            if (isEmpty) {
                Files.delete(parentPath);
                System.out.println("✅ Carpeta de Producto vacía eliminada: " + parentPath.getFileName());
            }
        }
    }

    /**
     * Elimina un Producto completo (todas sus piezas, la carpeta, y los registros de la BD).
     * El borrado se basa en los archivos encontrados en el directorio físico.
     * @param productDirectory Directorio del producto a eliminar.
     */
    public void deleteProduct(File productDirectory) throws IOException {

        if (!productDirectory.exists() || !productDirectory.isDirectory()) {
            throw new IOException("Error: El directorio del producto no existe o no es una carpeta.");
        }

        // 1. Recorrer el directorio y eliminar cada pieza de la BD
        // Hacemos el borrado de la BD primero, por si falla el borrado físico, no quedan registros huérfanos.
        Files.walk(productDirectory.toPath())
                .filter(Files::isRegularFile) // Solo nos interesan los archivos
                .forEach(path -> {
                    File pieceFile = path.toFile();
                    String fileName = pieceFile.getName();

                    // Intentamos extraer el código único para borrar el registro de la BD
                    try {
                        String code = fileName.substring(0, fileName.lastIndexOf('.'));
                        productRepository.deleteByCode(code);
                    } catch (Exception e) {
                        // Ignorar errores de archivos sin código único (archivos ocultos, etc.)
                        System.out.println("⚠️ Archivo en carpeta ignorado para borrado de BD: " + fileName);
                    }
                });

        // 2. Eliminar el Directorio Físico y todo su contenido (recursivamente)
        Files.walk(productDirectory.toPath())
                // Ordenamos por descendente para eliminar primero los archivos
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);

        System.out.println("✅ Producto (y todas sus piezas) eliminado: " + productDirectory.getName());
    }
}
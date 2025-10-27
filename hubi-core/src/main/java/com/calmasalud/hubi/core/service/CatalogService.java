package com.calmasalud.hubi.core.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.repository.IProductRepository;
import java.util.Comparator;

public class CatalogService {
    private final IProductRepository productRepository;
    // Commented line removed as constructor injection is used now
    //private final IProductRepository productRepository = new ProductRepositorySQLite();

    // Constructor for Dependency Injection (Correct)
    public CatalogService(IProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Se define la ubicación base de forma portable
    private static final Path REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos");

    //Color por defecto
    private static final String COLOR_POR_DEFECTO = "ROJO"; // Example, consider making configurable

//    /**
//     * Implementa la lógica de RF8 para generar códigos únicos.
//     * FORMATO: [PROD 3 letras][COLOR 3 letras][CORRELATIVO 3 dígitos]
//     * Modificado para usar siempre COLOR_POR_DEFECTO internamente por simplicidad
//     * y quitar dependencia de Supply model aquí. El color real podría almacenarse
//     * como un atributo del producto si es necesario más adelante.
//     *
//     * @param productName Nombre del producto/pieza.
//     * @param isPiece     (Parámetro eliminado, ya no necesario para la lógica actual de código)
//     * @return El código de producto generado.
//     **/

    public String generateProductCode(String productName /*, String color, boolean isPiece */) {
        // Simplificamos usando el color por defecto siempre para el código.
        String color = COLOR_POR_DEFECTO;

        // --- RF8: Validación de entradas mínimas ---
        if (productName == null || productName.trim().length() < 3) {
            throw new IllegalArgumentException("El nombre del producto debe tener al menos 3 caracteres.");
        }
        // La validación del color ya no es necesaria aquí si usamos el por defecto.
        // if (color == null || color.trim().length() < 3) {
        //     throw new IllegalArgumentException("El color debe tener al menos 3 caracteres.");
        // }

        String prefijoProd = productName.trim().substring(0, 3).toUpperCase();
        String prefijoColor = color.trim().substring(0, 3).toUpperCase(); // Usando el color por defecto

        String prefijoSeisLetras = prefijoProd + prefijoColor;

        // RF8: Obtiene el siguiente correlativo único de la base de datos (dependencia inyectada)
        String correlativo = productRepository.getNextCorrelative(prefijoSeisLetras); // Formato "001" esperado

        // Retorna el código completo (Sin el indicador -0- / -1-)
        // return String.format("%s-%d-%s", prefijoSeisLetras, isPiece ? 0 : 1, correlativo); // Formato anterior
        return String.format("%s%s", prefijoSeisLetras, correlativo); // Nuevo formato PROCOL001
    }


    /**
     * Copia un archivo origen al directorio base del repositorio.
     * Valida la extensión del archivo.
     * Asegura que el directorio repositorio exista.
     *
     * @param archivoOrigen El archivo a copiar.
     * @return El archivo destino en el repositorio.
     * @throws IOException Si la extensión no es válida, no se puede crear el directorio, o falla la copia.
     */
    public File copiarArchivoARepositorio(File archivoOrigen) throws IOException {
        if (archivoOrigen == null || !archivoOrigen.isFile()) {
            throw new IOException("Error: El archivo origen no es válido.");
        }
        // Validación de la Extensión (RF1)
        String nombre = archivoOrigen.getName().toLowerCase();
        if (!nombre.endsWith(".stl") && !nombre.endsWith(".3mf") && !nombre.endsWith(".gcode")) {
            throw new IOException("Error: El archivo debe ser .stl, .3mf o .gcode.");
        }

        // Asegurar la creación del Directorio Destino
        Files.createDirectories(REPOSITORIO_BASE);

        // Definición de Rutas
        Path rutaOrigen = archivoOrigen.toPath();
        // El nombre en el repositorio base SÍ mantiene el nombre original por ahora
        Path rutaDestinoFinal = REPOSITORIO_BASE.resolve(rutaOrigen.getFileName());

        // Copia el Archivo, reemplazando si existe.
        Files.copy(rutaOrigen, rutaDestinoFinal, StandardCopyOption.REPLACE_EXISTING);

        return rutaDestinoFinal.toFile(); // Devuelve el archivo copiado en el repositorio
    }

    /**
     * Procesa la carga de un archivo como un NUEVO PRODUCTO.
     * Genera código único, crea carpeta de producto, guarda en BD, y mueve el archivo.
     * @param archivoOrigen Archivo .stl, .3mf, .gcode a cargar.
     * @param nombreProducto Nombre deseado para el nuevo producto (usado para la carpeta).
     * @throws IOException Si hay error de validación, creación de directorio, BD o copia/movimiento de archivo.
     */
    public void procesarCargaProducto(File archivoOrigen, String nombreProducto) throws IOException {
        if (archivoOrigen == null || !archivoOrigen.isFile()) {
            throw new IOException("Error: El archivo a cargar no es válido.");
        }
        if (nombreProducto == null || nombreProducto.trim().isEmpty()){
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        nombreProducto = nombreProducto.trim(); // Limpiar espacios

        // Generar el código único (RF8)
        String codigoUnicoProducto = generateProductCode(nombreProducto); // Usa color por defecto
        String nombreArchivoOriginal = archivoOrigen.getName();
        String extension = getFileExtension(nombreArchivoOriginal);
        if (extension.isEmpty()) { // Validar que tenga extensión
            throw new IOException("Error: El archivo original no tiene extensión.");
        }

        // Crear el objeto Product (Modelo del Core)
        Product newProduct = new Product(codigoUnicoProducto, nombreArchivoOriginal, extension);

        // Persistir en la base de datos usando el repositorio inyectado
        long id = productRepository.save(newProduct);
        if (id == -1) {
            throw new IOException("Error: No se pudo guardar el producto en la base de datos.");
        }

        // Crear directorio para el producto (si no existe)
        Path directorioProducto = REPOSITORIO_BASE.resolve(nombreProducto);
        Files.createDirectories(directorioProducto);

        // Definir nombre final del archivo DENTRO de la carpeta del producto
        String nombreArchivoFinal = codigoUnicoProducto + extension;
        Path rutaDestinoFinalEnProducto = directorioProducto.resolve(nombreArchivoFinal);

        // Copia el archivo original a la carpeta del producto con el nombre de código único
        Files.copy(archivoOrigen.toPath(), rutaDestinoFinalEnProducto, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("✅ Producto '" + nombreProducto + "' creado con pieza inicial: " + nombreArchivoFinal);
    }

    /**
     * Procesa la carga de un archivo como una PIEZA asociada a un PRODUCTO existente.
     * Genera código único, guarda en BD, y mueve el archivo a la carpeta del producto existente.
     * @param archivoOrigen Archivo .stl, .3mf, .gcode a cargar como pieza.
     * @param rutaDirectorioProducto Ruta absoluta a la carpeta del producto existente.
     * @throws IOException Si hay error de validación, BD o copia/movimiento de archivo.
     * @throws IllegalArgumentException Si la ruta no es un directorio válido.
     */
    public void procesarCargaPieza(File archivoOrigen, String rutaDirectorioProducto) throws IOException {
        if (archivoOrigen == null || !archivoOrigen.isFile()) {
            throw new IOException("Error: El archivo a cargar no es válido.");
        }
        File directorioProductoFile = new File(rutaDirectorioProducto);
        if (!directorioProductoFile.isDirectory()) {
            throw new IllegalArgumentException("La ruta proporcionada no corresponde a un directorio de producto válido.");
        }

        Path directorioDestino = directorioProductoFile.toPath();
        // El nombre del producto se extrae del nombre de la carpeta seleccionada
        String nombreProducto = directorioProductoFile.getName();
        String nombreArchivoOriginal = archivoOrigen.getName();
        String extension = getFileExtension(nombreArchivoOriginal);
        if (extension.isEmpty()) {
            throw new IOException("Error: El archivo original no tiene extensión.");
        }

        // Generar el código único (RF8) para la nueva Pieza (basado en el nombre del producto padre)
        String codigoUnicoPieza = generateProductCode(nombreProducto); // Usa color por defecto
        String nombreArchivoFinal = codigoUnicoPieza + extension;
        Path rutaDestinoFinalEnProducto = directorioDestino.resolve(nombreArchivoFinal);

        // Crear el objeto Product (representa la pieza en este contexto)
        Product newPiece = new Product(codigoUnicoPieza, nombreArchivoOriginal, extension);

        // Persistir en la base de datos
        long id = productRepository.save(newPiece);
        if (id == -1) {
            throw new IOException("Error: No se pudo guardar la pieza en la base de datos.");
        }

        // Copia el archivo original a la carpeta del producto con el nombre de código único
        Files.copy(archivoOrigen.toPath(), rutaDestinoFinalEnProducto, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("✅ Pieza '" + nombreArchivoFinal + "' agregada al producto '" + nombreProducto + "'.");
    }

    /* Función auxiliar para obtener la extensión de un archivo (incluyendo el punto).
     * Devuelve "" si no hay extensión.
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        // Asegurarse que el punto no sea el primer caracter y que exista
        if (dotIndex == -1 || dotIndex == 0) {
            return ""; // Sin extensión o archivo tipo ".bashrc"
        }
        return filename.substring(dotIndex); // Incluye el punto, ej: ".stl"
    }

    /**
     * Busca los detalles completos de un producto o pieza por su código único.
     * @param code El código único (Ej: SOPROJ001)
     * @return El objeto Product o null si no se encuentra.
     */
    public Product getProductDetails(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return productRepository.findByCode(code.trim());
    }

    /**
     * Elimina una pieza específica.
     * Borra el registro de la BD y el archivo físico.
     * Si la carpeta del producto queda vacía después de eliminar la pieza, también elimina la carpeta.
     * @param pieceFile El archivo de la pieza a eliminar.
     * @throws IOException Si el archivo no es válido, no se puede eliminar, o hay error de BD.
     */
    public void deletePiece(File pieceFile) throws IOException {
        if (pieceFile == null || !pieceFile.isFile()) {
            throw new IOException("Error: El archivo de pieza proporcionado no es válido.");
        }
        String fileName = pieceFile.getName();
        String code = "";
        // Extraer código (nombre sin extensión)
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex != -1 && lastDotIndex > 0) {
            code = fileName.substring(0, lastDotIndex);
        } else {
            // Si no hay extensión o empieza con punto, no podemos obtener código válido para BD
            throw new IOException("Error: Formato de nombre de archivo de pieza inválido para obtener código: " + fileName);
        }


        File parentDir = pieceFile.getParentFile(); // Directorio del producto
        Path parentPath = parentDir.toPath();

        // 1. Eliminar de la BD
        productRepository.deleteByCode(code);

        // 2. Eliminar del Disco
        Files.delete(pieceFile.toPath());
        System.out.println("✅ Pieza eliminada del disco y BD: " + code);


        // 3. Verificar si el directorio padre quedó vacío y eliminarlo si es así
        if (parentDir.exists() && parentDir.isDirectory()) {
            boolean isEmpty = true;
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(parentPath)) {
                // Chequear si hay CUALQUIER archivo o subdirectorio (ignorando ocultos)
                for (Path entry : dirStream) {
                    if (!entry.getFileName().toString().startsWith(".")) {
                        isEmpty = false;
                        break;
                    }
                }
            }

            if (isEmpty) {
                Files.delete(parentPath);
                System.out.println("✅ Carpeta de Producto vacía eliminada: " + parentPath.getFileName());
            }
        }
    }

    /**
     * Elimina un Producto completo (su carpeta y todas las piezas dentro).
     * Recorre los archivos, elimina los registros de la BD y luego borra la carpeta recursivamente.
     * @param productDirectory Directorio del producto a eliminar.
     * @throws IOException Si el directorio no es válido o falla la eliminación.
     */
    public void deleteProduct(File productDirectory) throws IOException {

        if (productDirectory == null || !productDirectory.isDirectory() || productDirectory.equals(REPOSITORIO_BASE)) {
            throw new IOException("Error: El directorio del producto no es válido o es el repositorio base.");
        }

        Path productPath = productDirectory.toPath();

        // 1. Eliminar registros de la BD para cada archivo dentro del directorio
        Files.walk(productPath)
                .filter(Files::isRegularFile) // Solo archivos
                .forEach(path -> {
                    File pieceFile = path.toFile();
                    String fileName = pieceFile.getName();
                    String code = "";
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex != -1 && lastDotIndex > 0) {
                        code = fileName.substring(0, lastDotIndex);
                        try {
                            productRepository.deleteByCode(code);
                        } catch (Exception e) {
                            // Loggear error de BD pero continuar con borrado físico
                            System.err.println("⚠️ Error al eliminar registro BD para pieza '" + code + "': " + e.getMessage());
                        }
                    } else {
                        // Archivo sin extensión o inválido, no intentar borrar de BD
                        System.out.println("ℹ️ Archivo ignorado para borrado de BD (formato inválido): " + fileName);
                    }
                });

        // 2. Eliminar el Directorio Físico y  su contenido (recursivamente)
        // Usar Comparator.reverseOrder() asegura que los archivos se borren antes que los directorios
        Files.walk(productPath)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete); // Intenta borrar cada archivo/directorio

        // Verificar si realmente se borró (puede fallar por permisos, etc.)
        if (Files.exists(productPath)) {
            // Podría lanzar una excepción aquí si el borrado es crítico
            System.err.println("⚠️ Advertencia: No se pudo eliminar completamente la carpeta del producto: " + productDirectory.getName());
        } else {
            System.out.println("✅ Producto (carpeta y piezas) eliminado: " + productDirectory.getName());
        }
    }
}
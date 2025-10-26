package com.calmasalud.hubi.core.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.Supply;
import com.calmasalud.hubi.core.repository.IProductRepository;
//Tuve que comentar esto (CAMI)
//import com.calmasalud.hubi.persistence.repository.ProductRepositorySQLite;

public class CatalogService {
    private final IProductRepository productRepository;
    //Tuve que comentar esto (CAMI)
    //private final IProductRepository productRepository = new ProductRepositorySQLite();
    public CatalogService(IProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    // Se define la ubicación base de forma portable
    private static final Path REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos");

    // El color por defecto se define aquí.
    private static final String COLOR_POR_DEFECTO = "ROJO";
    public Product getProductDetails(String code) {
        return productRepository.findByCode(code);
    }
    /**
     * Implementa la lógica de RF8 para generar códigos únicos.
     */
    public String generateProductCode(String productName, String color, boolean isPart) {

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

        // Tipo: '0' si es una pieza, '1' si es un producto completo (RF8)
        String tipo = isPart ? "0" : "1";

        // RF8: Obtiene el siguiente correlativo único de la base de datos (SQLite)
        String correlativo = productRepository.getNextCorrelative(prefijoSeisLetras);

        // Retorna el código completo
        return String.format("%s-%s-%s", prefijoSeisLetras, tipo, correlativo);
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

    // Método 1: Para Carga como PRODUCTO (Se elimina 'Supply supply' y se usa el valor por defecto)
    public void procesarCargaProducto(File archivoOrigen, String nombreProducto) throws IOException {

        // Generar el código único (RF8) usando el COLOR POR DEFECTO
        String codigoUnicoProducto = generateProductCode(nombreProducto, COLOR_POR_DEFECTO, false);
        String extension = getFileExtension(archivoOrigen.getName());

        // Crear el objeto Product (Ahora con la extensión)
        Product newProduct = new Product(codigoUnicoProducto, nombreProducto, extension); // <--- CONSTRUCTOR MODIFICADO

        // Persistir el objeto en la base de datos (Primero, para asegurar la atomicidad del código)
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

    // Método 2: Para Carga como PIEZA
    public void procesarCargaPieza(File archivoOrigen, String rutaODiretorio) throws IOException {

        Path directorioDestino;
        File posibleDirectorio = new File(rutaODiretorio);
        String nombreProducto;
        String nombreArchivoOriginal = archivoOrigen.getName();

        // A. Si el usuario seleccionó un directorio existente (ruta completa)
        if (posibleDirectorio.isDirectory()) {
            directorioDestino = posibleDirectorio.toPath();
            nombreProducto = posibleDirectorio.getName();
        }
        // B. Si el usuario ingresó un nombre de producto NUEVO (ruta corta)
        else {
            // Lógica de "primera pieza" (crea el directorio)
            directorioDestino = REPOSITORIO_BASE.resolve(rutaODiretorio);
            Files.createDirectories(directorioDestino);
            nombreProducto = rutaODiretorio;
        }

        // Generar el código único (RF8) para la Pieza usando el COLOR POR DEFECTO
        String codigoUnicoPieza = generateProductCode(nombreProducto, COLOR_POR_DEFECTO, true);
        String extension = getFileExtension(archivoOrigen.getName());
        // Crear el objeto Product (la pieza es una entidad con su propio código único)
        Product newPiece = new Product(codigoUnicoPieza, nombreArchivoOriginal + " (Pieza)", extension);
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

    /**
     * Función auxiliar para obtener la extensión de un archivo.
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return filename.substring(dotIndex);
    }
    public void eliminarObjeto(File objeto) throws IOException {
        if (objeto == null || !objeto.exists()) {
            throw new IOException("El objeto no existe o ya ha sido eliminado.");
        }

        // IMPORTANTE: Manejar la eliminación recursiva si es un directorio (Producto)
        if (objeto.isDirectory()) {
            // Lógica para eliminar el contenido del directorio antes de eliminar el directorio mismo.
            eliminarDirectorioRecursivo(objeto);
        } else {
            // Eliminar el archivo (Pieza)
            if (!objeto.delete()) {
                throw new IOException("Fallo al eliminar el archivo: " + objeto.getAbsolutePath());
            }
        }
    }
    private void eliminarDirectorioRecursivo(File directorio) throws IOException {
        if (directorio.isDirectory()) {
            File[] children = directorio.listFiles();
            if (children != null) {
                for (File child : children) {
                    eliminarDirectorioRecursivo(child);
                }
            }
        }
        if (!directorio.delete()) {
            throw new IOException("Fallo al eliminar el directorio: " + directorio.getAbsolutePath());
        }
    }
}
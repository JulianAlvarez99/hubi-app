package com.calmasalud.hubi.core.service;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import com.calmasalud.hubi.core.model.MasterProduct;
import com.calmasalud.hubi.core.model.PieceStockDeduction;
import com.calmasalud.hubi.core.model.Product;
import com.calmasalud.hubi.core.model.ProductComposition;
import com.calmasalud.hubi.core.repository.IMasterProductRepository;
import com.calmasalud.hubi.core.repository.IProductCompositionRepository;
import com.calmasalud.hubi.core.repository.IProductRepository;
import com.calmasalud.hubi.core.model.Supply;
import com.calmasalud.hubi.core.repository.ISupplyRepository;
import com.calmasalud.hubi.core.repository.ISupplyRepository;
public class CatalogService {
    private final IProductRepository productRepository;
    private final IMasterProductRepository masterProductRepository;
    private final IProductCompositionRepository productCompositionRepository;
    private final FileParameterExtractor fileExtractor = new FileParameterExtractor();
    private final ISupplyRepository supplyRepository;
    // Constructor for Dependency Injection (Correct)
    public CatalogService(IProductRepository productRepository, IMasterProductRepository masterProductRepository,IProductCompositionRepository productCompositionRepository, ISupplyRepository supplyRepository) {
        this.productRepository = productRepository;
        this.masterProductRepository = masterProductRepository;
        this.productCompositionRepository = productCompositionRepository;
        this.supplyRepository = supplyRepository;
    }

    // Se define la ubicación base de forma portable
    private static final Path REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos");

    //Color por defecto
    private static final String COLOR_POR_DEFECTO = "ROJO"; // Example, consider making configurable

    /**
     * Implementa la lógica de RF8 para generar códigos únicos. (Method PÚBLICO SIMPLE)
     * FORMATO: [PROD 3 letras][COLOR 3 letras][CORRELATIVO 3 dígitos]
     * @param productName Nombre del producto/pieza.
     * @return El código de producto/pieza generado.
     **/
    public String generateProductCode(String productName) {
        return generateProductCode(productName, null);
    }

    /**
     * Implementa la lógica de RF8 para generar códigos únicos, opcionalmente forzando un correlativo.
     * (REQ 2: Usado para forzar correlativos en piezas complementarias).
     * @param productName Nombre del producto/pieza.
     * @param nextCorrelative Si no es nulo, usa este correlativo (ej: "001"), si es nulo, lo pide a BD.
     * @return El código de producto/pieza generado.
     */
    public String generateProductCode(String productName, String nextCorrelative) {
        String color = COLOR_POR_DEFECTO;

        // --- RF8: Validación de entradas mínimas ---
        if (productName == null || productName.trim().length() < 3) {
            throw new IllegalArgumentException("El nombre del producto debe tener al menos 3 caracteres.");
        }

        String prefijoProd = productName.trim().substring(0, 3).toUpperCase();
        String prefijoColor = color.trim().substring(0, 3).toUpperCase();
        String prefijoSeisLetras = prefijoProd + prefijoColor;

        String correlativo;
        if (nextCorrelative != null && !nextCorrelative.isEmpty()) {
            correlativo = nextCorrelative; // Usa el correlativo forzado (para correlación de piezas)
        } else {
            // Obtiene el siguiente correlativo único de la base de datos (RF8)
            correlativo = productRepository.getNextCorrelative(prefijoSeisLetras); // Formato "001" esperado
        }

        return String.format("%s%s", prefijoSeisLetras, correlativo); // Nuevo formato PROCOL001
    }

    /**
     * Copia un archivo origen al directorio base del repositorio.
     *
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

    // --- MethodS AUXILIARES DE ARCHIVOS (REQ 2 & 3) ---

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
     * Auxiliar: Busca si ya existe un archivo con el mismo nombre base y diferente extensión. (REQ 2)
     * Retorna el CÓDIGO ÚNICO (ej: SOPROJ001) del archivo existente si se encuentra una correlación.
     */
    private String findExistingCorrelative(File directorioProducto, String baseName, String extension) {
        // Obtenemos las extensiones complementarias (con el punto)
        String lowerExt = extension.toLowerCase();
        String complementaryExtension = null;
        if (lowerExt.endsWith(".gcode")) {
            complementaryExtension = ".stl";
        } else if (lowerExt.endsWith(".3mf")) {
            complementaryExtension = ".gcode";
        } else if (lowerExt.endsWith(".stl")) {
            // Buscamos el GCODE complementario si subimos STL
            complementaryExtension = ".gcode";
        }

        if (complementaryExtension == null) return null;

        File[] files = directorioProducto.listFiles();
        if (files == null) return null;

        for (File f : files) {
            // Un archivo existente en disco está nombrado con su CÓDIGO_UNICO_PRODUCTO
            String fileName = f.getName();
            String fileExt = getFileExtension(fileName);

            // 1. Verificar si tiene la extensión complementaria y es un archivo de pieza
            if (f.isFile() && fileExt.equalsIgnoreCase(complementaryExtension)) {
                String existingCode = fileName.substring(0, fileName.lastIndexOf('.'));

                // 2. Usar el repositorio para obtener el nombre original guardado en la BD
                Product existingProduct = productRepository.findByCode(existingCode);

                if (existingProduct != null) {
                    String existingOriginalName = existingProduct.getName(); // Ej: "MyPart.stl"
                    String existingBaseName = existingOriginalName.substring(0, existingOriginalName.lastIndexOf('.')); // Ej: "MyPart"

                    // 3. Comparar el nombre base original (guardado en DB) con el nombre base del nuevo archivo
                    if (existingBaseName.equalsIgnoreCase(baseName)) {
                        // ¡Correlación encontrada! Retornamos el código base existente (Ej: SOPROJ001)
                        return existingCode;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Auxiliar: Comprueba si ya existe un archivo con el mismo nombre y extensión. (REQ 3)
     * La verificación se hace buscando si existe un registro en la BD con el mismo nombre ORIGINAL.
     */
    private boolean isDuplicate(File directorioProducto, String nombreArchivoOriginal) {
        File[] allPieces = directorioProducto.listFiles();
        if (allPieces != null) {
            for (File pieceFile : allPieces) {
                if (!pieceFile.isFile() || pieceFile.getName().startsWith(".")) continue;

                // Extraemos el código del nombre del archivo en disco
                String code = pieceFile.getName().substring(0, pieceFile.getName().lastIndexOf('.'));

                // Buscamos el producto por su código para obtener su nombre original
                Product existingPiece = productRepository.findByCode(code);

                // Comparamos el nombre original guardado en la BD con el nombre original del archivo subido
                if (existingPiece != null && existingPiece.getName().equals(nombreArchivoOriginal)) {
                    return true; // Duplicado encontrado
                }
            }
        }
        return false;
    }


    /**
     * Procesa la carga de uno o más archivos como un NUEVO PRODUCTO. (REQ 1 - Modificado para List)
     * @param archivosOrigen Lista de archivos .stl, .3mf, .gcode a cargar.
     * @param nombreProducto Nombre deseado para el nuevo producto (usado para la carpeta).
     * @throws IOException Si hay error de validación, creación de directorio, BD o copia/movimiento de archivo.
     */
    /**
     * Procesa la carga de uno o más archivos como un NUEVO PRODUCTO. (REQ 1 - Modificado para List)
     * @param archivosOrigen Lista de archivos .stl, .3mf, .gcode a cargar.
     * @param nombreProducto Nombre deseado para el nuevo producto (usado para la carpeta).
     * @throws IOException Si hay error de validación, creación de directorio, BD o copia/movimiento de archivo.
     */
    /**
     * Procesa la carga de uno o más archivos como un NUEVO PRODUCTO. (REQ 1 - Modificado para List)
     * Incluye extracción de peso y detalle de consumo para HU3.
     * @param archivosOrigen Lista de archivos .stl, .3mf, .gcode a cargar.
     * @param nombreProducto Nombre deseado para el nuevo producto (usado para la carpeta).
     * @throws IOException Si hay error de validación, creación de directorio, BD o copia/movimiento de archivo.
     */
    public void procesarCargaProducto(List<File> archivosOrigen, String nombreProducto) throws IOException {
        if (archivosOrigen == null || archivosOrigen.isEmpty()) {
            throw new IOException("Error: No se proporcionaron archivos para cargar.");
        }
        if (nombreProducto == null || nombreProducto.trim().isEmpty()){
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        nombreProducto = nombreProducto.trim();

        // 1. --- REGISTRO DEL PRODUCTO MAESTRO (RF4, RF8) ---

        // 1.1. Obtener Prefijo y Código Maestro Único (Ej: SOP01)
        String productPrefix = masterProductRepository.getPrefixFromName(nombreProducto);
        // Genera el código (Ej: SOP01) y reserva el correlativo en master_correlatives.
        String masterCode = masterProductRepository.getNextMasterCode(productPrefix);

        // 1.2. Guardar el Producto Maestro y el Stock Inicial (RF6)
        MasterProduct newMasterProduct = new MasterProduct(
                masterCode,
                productPrefix,
                nombreProducto,
                "Producto inicial generado al cargar piezas."
        );
        // Guardamos el producto maestro y su stock inicial (Qty: 0)
        masterProductRepository.saveNewProduct(newMasterProduct, 0.0);

        System.out.println("✅ Producto Maestro Registrado: " + masterCode);

        // 2. Preparar Prefijos y Agrupación (Continúa lógica de piezas)
        String prefijoProd = productPrefix; // Usamos el prefijo de 3 letras (SOP)
        String prefijoColor = COLOR_POR_DEFECTO.trim().substring(0, 3).toUpperCase();
        String prefijoSeisLetras = prefijoProd + prefijoColor; // Ej: SOPROJ

        // 3. Obtener el primer correlativo disponible para PIEZAS (La BD lo incrementa/reserva aquí)
        int currentCorrelative = Integer.parseInt(productRepository.getNextCorrelative(prefijoSeisLetras));

        // 4. Crear el directorio (si no existe) con el nombre dado por el usuario
        Path directorioProducto = REPOSITORIO_BASE.resolve(nombreProducto);
        Files.createDirectories(directorioProducto);

        // 5. Lógica para procesar y guardar las piezas individuales
        Map<String, List<File>> archivosPorNombreBase = archivosOrigen.stream()
                .filter(file -> {
                    String name = file.getName();
                    String ext = getFileExtension(name);
                    return !ext.isEmpty() && (ext.endsWith(".stl") || ext.endsWith(".3mf") || ext.endsWith(".gcode"));
                })
                .collect(Collectors.groupingBy(file -> {
                    String name = file.getName();
                    int lastDot = name.lastIndexOf('.');
                    return lastDot != -1 ? name.substring(0, lastDot) : name;
                }));

        if (archivosPorNombreBase.isEmpty()) {
            throw new IOException("No se pudo cargar ningún archivo válido de la lista proporcionada.");
        }
        int loadedCount = 0;

        for (Map.Entry<String, List<File>> entry : archivosPorNombreBase.entrySet()) {
            String correlativeStr = String.format("%03d", currentCorrelative);
            String pieceCode = prefijoSeisLetras + correlativeStr; // Ej: SOPROJ001

            boolean groupProcessed = false;

            for (File archivo : entry.getValue()) {
                String nombreArchivoOriginal = archivo.getName();
                String pieceExtension = getFileExtension(nombreArchivoOriginal);

                try {
                    // --- EXTRAER PESO Y DETALLE (LOGICA HU3) ---
                    double pesoTotalDetectado = 0.0;
                    List<Double> pesosIndividuales = new ArrayList<>();

                    try {
                        List<File> filesToAnalyze = new ArrayList<>();
                        filesToAnalyze.add(archivo);

                        // Usamos el extractor de la clase
                        FileParameterExtractor.PrintInfo info = fileExtractor.extract(filesToAnalyze);

                        for (FileParameterExtractor.FilamentProfile profile : info.filamentProfiles.values()) {
                            if (profile.filamentAmountG != null && !profile.filamentAmountG.equals("N/D")) {
                                try {
                                    String cleanWeight = profile.filamentAmountG.replace(" g", "").trim();
                                    double val = Double.parseDouble(cleanWeight);
                                    if (val > 0) {
                                        pesoTotalDetectado += val;
                                        pesosIndividuales.add(val);
                                    }
                                } catch (NumberFormatException e) { /* Ignorar */ }
                            }
                        }
                        System.out.println("⚖️ Peso extraído para " + archivo.getName() + ": " + pesoTotalDetectado + "g");

                    } catch (Exception e) {
                        System.err.println("⚠️ No se pudo extraer el peso del archivo: " + e.getMessage());
                    }

                    // Generar String de detalle (ej: "80.5;20.2") para soporte multicolor
                    String usageDetailStr = pesosIndividuales.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(";"));
                    // ---------------------------------------------------------

                    // A. Guardar en BD (CON PESO Y DETALLE)
                    // 🚨 Usamos el constructor completo actualizado
                    Product newPiece = new Product(pieceCode, nombreArchivoOriginal, pieceExtension, pesoTotalDetectado, usageDetailStr);

                    long pieceId = productRepository.save(newPiece);
                    if (pieceId == -1) {
                        throw new IOException("No se pudo guardar la pieza en la base de datos. Código: " + pieceCode);
                    }

                    // B. Copiar archivo con el nombre de código único
                    String nombreArchivoFinalPieza = pieceCode + pieceExtension;
                    Path rutaDestinoFinalEnProductoPieza = directorioProducto.resolve(nombreArchivoFinalPieza);
                    Files.copy(archivo.toPath(), rutaDestinoFinalEnProductoPieza, StandardCopyOption.REPLACE_EXISTING);

                    loadedCount++;
                    groupProcessed = true;

                } catch (Exception e) {
                    throw new IOException("Error al procesar el archivo '" + archivo.getName() + "': " + e.getMessage(), e);
                }
            }

            // 6. Incrementar Correlativo SOLO si el grupo fue procesado
            if (groupProcessed) {
                currentCorrelative++;
                productRepository.getNextCorrelative(prefijoSeisLetras);
            }
        }

        if (loadedCount == 0) {
            throw new IOException("No se pudo cargar ningún archivo de la lista proporcionada.");
        }
    }/**
     * Procesa la carga de un archivo como una PIEZA asociada a un PRODUCTO existente. (REQ 2 & 3)
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
        String nombreProducto = directorioProductoFile.getName();
        String nombreArchivoOriginal = archivoOrigen.getName();
        String extension = getFileExtension(nombreArchivoOriginal);

        if (extension.isEmpty()) {
            throw new IOException("Error: El archivo original no tiene extensión.");
        }

        String baseNameWithoutExt = nombreArchivoOriginal.substring(0, nombreArchivoOriginal.lastIndexOf('.'));

        double pesoTotalDetectado = 0.0;
        List<Double> pesosIndividuales = new ArrayList<>();
        try {
            List<File> filesToAnalyze = new ArrayList<>();
            filesToAnalyze.add(archivoOrigen);
            FileParameterExtractor.PrintInfo info = fileExtractor.extract(filesToAnalyze);

            // Iterar sobre los perfiles encontrados (T0, T1...)
            for (FileParameterExtractor.FilamentProfile profile : info.filamentProfiles.values()) {
                if (profile.filamentAmountG != null && !profile.filamentAmountG.equals("N/D")) {
                    try {
                        String cleanWeight = profile.filamentAmountG.replace(" g", "").trim();
                        double val = Double.parseDouble(cleanWeight);
                        if (val > 0) {
                            pesoTotalDetectado += val;
                            pesosIndividuales.add(val); // Guardamos cada peso > 0
                        }
                    } catch (NumberFormatException e) { }
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ No se pudo extraer el peso del archivo: " + e.getMessage());
        }
        // Convertir lista de pesos a String (Ej: "12.5;4.2")
        String usageDetailStr = pesosIndividuales.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(";"));
        // --- VALIDACIÓN 1: DUPLICADO (REQ 3) ---
        if (isDuplicate(directorioProductoFile, nombreArchivoOriginal)) {
            throw new IOException("Duplicado: Ya existe un archivo con el mismo nombre.");
        }

        // --- CORRELACIÓN (REQ 2) ---
        String existingFullCode = findExistingCorrelative(directorioProductoFile, baseNameWithoutExt, extension);
        String finalCode;
        String prefijoSeisLetras;

        // Obtener prefijo base
        String tempCode = generateProductCode(nombreProducto, "001");
        prefijoSeisLetras = tempCode.substring(0, 6);

        if (existingFullCode != null) {
            String correlativeToUse = existingFullCode.substring(existingFullCode.length() - 3);
            finalCode = prefijoSeisLetras + correlativeToUse;
        } else {
            finalCode = generateProductCode(nombreProducto, null);
        }

        // 🚨 FALTABA ESTO: DEFINIR EL NOMBRE Y RUTA FINAL
        String nombreArchivoFinal = finalCode + extension;
        Path rutaDestinoFinalEnProducto = directorioDestino.resolve(nombreArchivoFinal);

        Product newPiece = new Product(finalCode, nombreArchivoOriginal, extension, pesoTotalDetectado, usageDetailStr);

        // Persistir en la base de datos
        long id = productRepository.save(newPiece);
        if (id == -1) {
            throw new IOException("Error: No se pudo guardar la pieza en la base de datos.");
        }

        // Copia el archivo
        Files.copy(archivoOrigen.toPath(), rutaDestinoFinalEnProducto, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("✅ Pieza '" + nombreArchivoFinal + "' agregada al producto '" + nombreProducto + "'.");
    }
    /**
     * Procesa la carga de un archivo como un NUEVO PRODUCTO.
     * (Mantenido para compatibilidad, redirige a la implementación de lista).
     */
    public void procesarCargaProducto(File archivoOrigen, String nombreProducto) throws IOException {
        this.procesarCargaProducto(java.util.List.of(archivoOrigen), nombreProducto);
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
     * @return El código maestro (Ej: LLA01) asociado al nombre de la carpeta de producto.
     * CORREGIDO: Manejo seguro de NullPointerException.
     */
    public String getMasterCodeByProductName(String productName) {
        // NOTA: Asumimos que findByProductName está disponible en IMasterProductRepository.
        MasterProduct masterProduct = masterProductRepository.findByProductName(productName);

        if (masterProduct == null) {
            // Si el repositorio devuelve null (producto no encontrado), retornamos null.
            return null;
        }

        // Si el producto existe, retornamos su código maestro.
        return masterProduct.getMasterCode();
    }

    /**
     * Elimina una pieza específica.
     * Borra el registro de la BD y MUEVE el archivo físico a la papelera de reciclaje.
     * Si la carpeta del producto queda vacía después de eliminar la pieza, también mueve la carpeta a la papelera.
     * @param pieceFile El archivo de la pieza a eliminar.
     * @throws IOException Si el archivo no es válido, no se puede mover a la papelera, o hay error de BD.
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

        // 2. MOVER A LA PAPELERA en lugar de eliminar permanentemente
        boolean movedToRecycleBin = RecycleBinManager.moveToRecycleBin(pieceFile);

        if (!movedToRecycleBin) {
            // Si falla el movimiento a la papelera, lanzar excepción
            throw new IOException("No se pudo mover el archivo a la papelera de reciclaje: " + fileName);
        }

        System.out.println("✅ Pieza eliminada de la BD y movida a papelera: " + code);


        // 3. Verificar si el directorio padre quedó vacío y moverlo a la papelera si es así
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
                // Mover la carpeta vacía a la papelera en lugar de eliminarla
                boolean dirMovedToRecycleBin = RecycleBinManager.moveDirectoryToRecycleBin(parentDir);
                if (dirMovedToRecycleBin) {
                    System.out.println("✅ Carpeta de Producto vacía movida a papelera: " + parentPath.getFileName());
                } else {
                    System.err.println("⚠️ No se pudo mover la carpeta vacía a la papelera: " + parentPath.getFileName());
                }
            }
        }
    }

    /**
     * Elimina un Producto completo (MUEVE su carpeta y todas las piezas dentro a la papelera) Y elimina el registro maestro de la BD.
     */
    public void deleteProduct(File productDirectory) throws IOException {

        if (productDirectory == null || !productDirectory.isDirectory() || productDirectory.equals(REPOSITORIO_BASE.toFile())) {
            throw new IOException("Error: El directorio del producto no es válido o es el repositorio base.");
        }

        String productName = productDirectory.getName();
        String masterCode = getMasterCodeByProductName(productName);
        Path productPath = productDirectory.toPath();

        // Lista para almacenar los nombres base de las piezas para la limpieza de piece_stock
        final List<String> pieceNamesToDeleteStock = new ArrayList<>();

        // 1. Eliminar registros de la BD para cada archivo dentro del directorio (Piezas)
        Files.walk(productPath)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    File pieceFile = path.toFile();
                    String fileName = pieceFile.getName();
                    int lastDotIndex = fileName.lastIndexOf('.');

                    if (lastDotIndex > 0) {
                        String code = fileName.substring(0, lastDotIndex);

                        // LÓGICA CRÍTICA: Obtener el nombre base ANTES de eliminar el registro de 'products'
                        Product piece = productRepository.findByCode(code);
                        if (piece != null) {
                            String pieceNameBase = piece.getName().substring(0, piece.getName().lastIndexOf('.'));
                            pieceNamesToDeleteStock.add(pieceNameBase);
                        }

                        // Eliminar registro de la tabla 'products'
                        try {
                            productRepository.deleteByCode(code);
                        } catch (Exception e) {
                            System.err.println("⚠️ Error al eliminar registro BD para pieza '" + code + "': " + e.getMessage());
                        }
                    } else {
                        System.out.println("ℹ️ Archivo ignorado para borrado de BD (formato inválido): " + fileName);
                    }
                });

        // 2. MOVER el Directorio Completo a la Papelera (en lugar de eliminarlo permanentemente)
        boolean movedToRecycleBin = RecycleBinManager.moveDirectoryToRecycleBin(productDirectory);

        if (!movedToRecycleBin) {
            throw new IOException("No se pudo mover el producto a la papelera de reciclaje: " + productName);
        }

        System.out.println("♻️ Producto completo movido a papelera: " + productName);

        // 3. Eliminar el registro de Producto Maestro y su Stock Final
        if (masterCode != null) {
            masterProductRepository.deleteProduct(masterCode);

            // 4. LIMPIEZA CRÍTICA: Eliminar el Stock de las Piezas Asociadas (Limpia la tabla piece_stock)
            pieceNamesToDeleteStock.stream().distinct().forEach(pieceNameBase -> {
                productRepository.deletePieceStockByPieceNameBase(pieceNameBase);
                System.out.println("✅ Stock de Pieza Base eliminado de piece_stock: " + pieceNameBase);
            });

            System.out.println("✅ Registro Maestro y Stock final eliminados para: " + masterCode);
        }

        System.out.println("✅ Producto '" + productName + "' eliminado del catálogo y movido a papelera.");
    }

    public void verifyPieceAvailability(String masterCode, int quantity) throws IOException {
        // 1. Obtener la composición (BOM) requerida para 1 unidad
        List<ProductComposition> composition = productCompositionRepository.getComposition(masterCode);
        if (composition.isEmpty()) {
            // Si no hay composición, significa que no se definió la BOM.
            throw new IOException("Error de composición: La receta del producto (BOM) no ha sido definida.");
        }

        // 2. Verificar si se cumplen los requisitos para la cantidad total (BOM x Cantidad)
        for (ProductComposition requiredPiece : composition) {
            String pieceNameBase = requiredPiece.getPieceNameBase();
            int totalRequired = requiredPiece.getRequiredQuantity() * quantity;

            // CORRECCIÓN CLAVE: CONSULTAR STOCK TOTAL DISPONIBLE (SUMA DE TODOS LOS COLORES)
            // La implementación del repositorio suma el campo 'available_quantity' de todas
            // las filas con ese 'piece_name_base' en la tabla piece_stock.
            long currentAvailable = productRepository.getPieceStockQuantity(pieceNameBase);

            if (currentAvailable < totalRequired) {
                throw new IOException(
                        String.format(
                                "Faltan piezas para producir %d unidades de %s. Pieza requerida: '%s'. Disponibles: %d. Necesarias: %d.",
                                quantity, masterCode, pieceNameBase, currentAvailable, totalRequired
                        )
                );
            }
        }
    }
    public MasterProduct findByProductPrefix(String prefix) {
        // Asumimos que la implementación del repositorio existe y se llama aquí:
        // return masterProductRepository.findByProductPrefix(prefix);

        // STUB TEMPORAL: Para que el código compile ahora, si el Method no está en la interfaz.
        // Si el Method no existe, esto causará un error. Asumiendo que SÍ existe:
        return masterProductRepository.findByProductPrefix(prefix);
    }

    /**
     * Elimina una pieza específica (archivo y registro BD) dado solo el código.
     * @param code El código único de la pieza (Ej: LLAROJ007).
     * @throws IOException Si el archivo no se puede encontrar o eliminar.
     */
    public void deletePieceByCode(String code) throws IOException {
        Product piece = productRepository.findByCode(code); // Obtener detalles de la pieza (code, name, ext)

        if (piece == null) {
            throw new IOException("Error: La pieza con código " + code + " no fue encontrada en la base de datos.");
        }

        // 1. Obtener los detalles necesarios para la ruta
        String pieceFileName = piece.getCode() + piece.getFileExtension(); // Ej: LLAROJ007.gcode
        String productPrefix = piece.getCode().substring(0, 3); // Ej: LLA

        // 2. Buscar el nombre de la carpeta (Ej: Llavero) usando el MasterProduct
        MasterProduct masterProduct = findByProductPrefix(productPrefix);
        // Usamos el nombre del MasterProduct como el nombre de la carpeta física
        String productName = masterProduct != null ? masterProduct.getProductName() : "UnknownProduct";

        // 3. Reconstruir la ruta física del archivo
        File productDirectory = REPOSITORIO_BASE.resolve(productName).toFile();
        File pieceFile = new File(productDirectory, pieceFileName);

        if (!pieceFile.exists()) {
            // Si el archivo físico no se encuentra (Ej: eliminado manualmente), borramos solo el registro de BD.
            productRepository.deleteByCode(code);
            throw new IOException("Advertencia: El archivo físico de la pieza '" + code + "' no existe. Solo se eliminó el registro de la BD.");
        }

        // 4. Llamar a la lógica de eliminación ya existente (que maneja BD, Archivo, y Carpeta Vacía)
        deletePiece(pieceFile);
    }

    /**
     * Disminuye el stock disponible del Producto Maestro (Finished Stock).
     * @param masterCode El código del producto maestro (Ej: LLA01).
     * @param quantity Cantidad de unidades a descontar.
     * @throws RuntimeException Si hay un error de persistencia o stock insuficiente.
     */
    public void decreaseMasterProductStock(String masterCode, int quantity) throws RuntimeException {
        // Asumimos que masterProductRepository.decreaseStock está disponible
        // y maneja la validación de stock.
        masterProductRepository.decreaseStock(masterCode, quantity);
    }
    public void deleteProductStockByComposition(String masterCode, List<PieceStockDeduction> deductions) throws IOException {
        if (deductions == null || deductions.isEmpty()) {
            throw new IllegalArgumentException("La lista de deducciones de stock no puede estar vacía.");
        }

        try {
            // Llamada al Method transaccional del repositorio
            productRepository.decreasePieceStockBatch(deductions);

        } catch (RuntimeException e) {
            // Capturamos el error de runtime del repositorio y lo lanzamos como IOException para la UI
            throw new IOException("Fallo al descontar el stock de piezas. La operación fue revertida. Razón: " + e.getMessage(), e);
        }
    }
    public void addOrModifySupplyStock(Supply supply) {
        Supply existingSupply = null;

        // 1. Buscamos coincidencias
        for (Supply s : supplyRepository.listAll()) {
            if (s.getColorFilamento().equalsIgnoreCase(supply.getColorFilamento()) &&
                    s.getTipoFilamento().equalsIgnoreCase(supply.getTipoFilamento()) &&
                    s.getName().equalsIgnoreCase(supply.getName())) {

                existingSupply = s;
                break;
            }
        }

        if (existingSupply != null) {
            // --- CASO FUSIÓN (No gastamos código) ---
            double newQuantity = existingSupply.getCantidadDisponible() + supply.getCantidadDisponible();
            existingSupply.setCantidadDisponible(newQuantity);
            existingSupply.setUmbralAlerta(supply.getUmbralAlerta());

            supplyRepository.modify(existingSupply);
            System.out.println("✅ Stock fusionado con éxito.");

        } else {
            // --- CASO NUEVO (Aquí sí generamos el código) ---
            if (supply.getCantidadDisponible() > 0) {

                // 🚨 CAMBIO CLAVE: Generamos el código AQUÍ, solo si realmente vamos a guardar.
                // Si el objeto venía sin código, se lo ponemos ahora.
                if (supply.getCode() == null || supply.getCode().isEmpty() || supply.getCode().equals("TEMP")) {
                    String nuevoCodigo = supplyRepository.getNextCorrelativeCode(
                            supply.getColorFilamento(),
                            supply.getTipoFilamento()
                    );
                    supply.setCode(nuevoCodigo);
                }

                supplyRepository.add(supply);
                System.out.println("✅ Nuevo insumo creado con código: " + supply.getCode());
            }
        }
    }

    /**
     * Descuenta una cantidad de un insumo específico por su ID (ej. por descarte o defecto).
     * RF5: Eliminar insumo (defecto o descarte).
     * @param id El ID del insumo.
     * @param quantity La cantidad a descontar (en gramos).
     * @throws IllegalArgumentException si la cantidad a descontar es mayor a la disponible.
     */
    public void removeSupplyStock(long id, double quantity) throws IllegalArgumentException {
        Supply supply = supplyRepository.findByID(id);
        if (supply == null) {
            throw new IllegalArgumentException("Insumo no encontrado con ID: " + id);
        }

        double currentQuantity = supply.getCantidadDisponible();

        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad a descontar debe ser positiva.");
        }

        // Usamos una pequeña tolerancia (epsilon) para comparaciones de punto flotante
        if (quantity > (currentQuantity + 0.001)) {
            throw new IllegalArgumentException("No hay suficiente stock. Disponible: " + String.format("%.2f", currentQuantity) + " gramos. Intentando descontar: " + String.format("%.2f", quantity) + " gramos.");
        }

        double newQuantity = currentQuantity - quantity;


        if (newQuantity <= 0.01) {
            supply.setCantidadDisponible(0.0); // Poner stock a cero
            supplyRepository.modify(supply); // Mantenemos el registro en la BD
            System.out.println("ℹ️ Stock agotado. Insumo ID " + id + " mantenido en el catálogo con 0g de stock.");
        } else {
            supply.setCantidadDisponible(newQuantity);
            supplyRepository.modify(supply);
        }

    }
    /**
     * Elimina permanentemente un insumo del catálogo por su ID.
     * Solo se permite si el stock es cero (por buena práctica de inventario).
     * @param id El ID del insumo a eliminar.
     * @throws IllegalArgumentException Si el insumo tiene stock disponible.
     */
    public void deleteSupplyPermanently(long id) throws IllegalArgumentException {
        Supply supply = supplyRepository.findByID(id);
        if (supply == null) {
            throw new IllegalArgumentException("Insumo no encontrado con ID: " + id);
        }

        // Validación de stock cero antes de la eliminación permanente
        if (supply.getCantidadDisponible() > 0.01) {
            throw new IllegalArgumentException("No se puede eliminar un insumo que tiene stock disponible (" + String.format("%.2f", supply.getCantidadDisponible()) + "g). Descarte el stock primero.");
        }

        // Eliminación física del registro de la BD
        supplyRepository.delete(id);
        System.out.println("✅ Insumo eliminado permanentemente del catálogo: ID " + id + ", Código " + supply.getCode());
    }
    /**
     * Obtiene el listado completo de insumos.
     * @return Lista de todos los insumos.
     */
    public List<Supply> listAllSupplies() {
        return supplyRepository.listAll();
    }

    public String generateNextSupplyCode(String colorPrefix, String tipoFilamento) {
        // Delega la responsabilidad de la transacción al repositorio
        return supplyRepository.getNextCorrelativeCode(colorPrefix, tipoFilamento);
    }
    public List<String> getAvailableFilamentColors() {
        return supplyRepository.listAll().stream()
                .filter(s -> s.getCantidadDisponible() > 0) // Solo los que tienen stock
                // CAMBIO: Agregamos el nombre y un separador "|"
                .map(s -> s.getName() + " | " + s.getColorFilamento() + " " + s.getTipoFilamento())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    /**
     * HU3: Registra la producción de una pieza, verifica stock, descuenta insumos y devuelve reporte.
     * @param pieceCode Código único de la pieza (Ej: SOPROJ001).
     * @param selectedColors Lista de strings "COLOR TIPO" seleccionados (Ej: ["ROJO PLA", "AZUL ABS"]).
     * @param quantity Cantidad de piezas producidas.
     * @return Lista de mensajes detallados sobre el descuento realizado.
     */
    /**
     * HU3: Registra la producción, verifica stock y descuenta insumos basándose en el consumo real por color.
     * Regla: Los pesos extraídos del archivo se ordenan (Mayor a Menor) y se asignan a los colores seleccionados (Color 1, Color 2...).
     * @return Lista de mensajes detallados sobre el descuento realizado.
     */
    public List<String> registerPieceProduction(String pieceCode, List<String> selectedColors, int quantity) {
        List<String> reportMessages = new ArrayList<>();
        System.out.println("--- DEBUG: Iniciando Registro de Producción (Lógica Ponderada) ---");
        System.out.println("Pieza: " + pieceCode + " | Cantidad: " + quantity);

        // 1. BUSCAR LA PIEZA Y SUS DETALLES DE CONSUMO
        Product piece = productRepository.findByCode(pieceCode);
        if (piece == null) {
            throw new RuntimeException("Pieza no encontrada con código: " + pieceCode);
        }

        // Recuperar y parsear los pesos individuales (ej: "12.5;4.2")
        List<Double> weights = new ArrayList<>();
        String detail = piece.getUsageDetail();

        if (detail != null && !detail.isEmpty()) {
            for (String w : detail.split(";")) {
                try {
                    double val = Double.parseDouble(w);
                    if (val > 0) weights.add(val);
                } catch (NumberFormatException e) { /* Ignorar valores corruptos */ }
            }
        }

        // Fallback: Si no hay detalle (piezas viejas), usamos el peso total como un único bloque
        if (weights.isEmpty() && piece.getWeightGrams() > 0) {
            weights.add(piece.getWeightGrams());
        }

        // 🚨 ORDENAR PESOS DE MAYOR A MENOR
        // Para que el Color 1 (Principal) consuma la mayor cantidad
        weights.sort(Comparator.reverseOrder());

        if (weights.isEmpty()) {
            reportMessages.add("⚠️ Advertencia: La pieza no tiene peso registrado (0g). No se descontará filamento.");
            System.out.println("WARN: Peso 0g.");
        }

        // 2. PRE-VALIDACIÓN DE STOCK
        // Verificamos que TODOS los colores tengan suficiente material antes de tocar la BD.
        if (!selectedColors.isEmpty() && !weights.isEmpty()) {
            List<Supply> allSupplies = supplyRepository.listAll();

            for (int i = 0; i < selectedColors.size(); i++) {
                String colorKey = selectedColors.get(i);

                // Asignar peso: El color i consume el peso i. Si hay más colores que pesos, consumen 0.
                double weightForThisColorUnit = (i < weights.size()) ? weights.get(i) : 0.0;
                double totalRequiredForBatch = weightForThisColorUnit * quantity;

                if (totalRequiredForBatch > 0) {
                    Supply supply = findSupplyByColorKey(allSupplies, colorKey);

                    if (supply == null) {
                        throw new RuntimeException("No existe stock registrado para el insumo: " + colorKey);
                    }

                    // Verificación estricta
                    // Usamos un pequeño margen de error (epsilon) para evitar problemas de punto flotante
                    if (supply.getCantidadDisponible() < (totalRequiredForBatch - 0.001)) {
                        throw new RuntimeException("Stock insuficiente para " + colorKey +
                                ".\nRequerido: " + String.format(Locale.US, "%.2f", totalRequiredForBatch) + "g" +
                                "\nDisponible: " + String.format(Locale.US, "%.2f", supply.getCantidadDisponible()) + "g");
                    }
                }
            }
        }

        // 3. EJECUCIÓN: DESCUENTO DE STOCK
        if (!selectedColors.isEmpty() && !weights.isEmpty()) {
            // Recargamos la lista para encontrar la referencia por colorKey
            List<Supply> allSupplies = supplyRepository.listAll();

            for (int i = 0; i < selectedColors.size(); i++) {
                String colorKey = selectedColors.get(i);
                double weightForThisColorUnit = (i < weights.size()) ? weights.get(i) : 0.0;
                double totalRequiredForBatch = weightForThisColorUnit * quantity;

                if (totalRequiredForBatch > 0) {

                    // Usamos findSupplyByColorKey solo para obtener el ID de referencia
                    Supply supplyRef = findSupplyByColorKey(allSupplies, colorKey);
                    if (supplyRef == null) continue;

                    // OBTENER EL ESTADO FRESCO DEL INSUMO (CRÍTICO: antes de la deducción)
                    Supply freshSupply = getSupplyById(supplyRef.getId()); // Carga el estado actual de la DB
                    if (freshSupply == null) continue;

                    // 1. Usar los valores FRESCOS
                    double previousStock = freshSupply.getCantidadDisponible();
                    double umbral = freshSupply.getUmbralAlerta();
                    double newStock = previousStock - totalRequiredForBatch;
                    if (newStock < 0) newStock = 0;

                    // 2. Descontar (Llama a removeSupplyStock que modifica el DB)
                    removeSupplyStock(freshSupply.getId(), totalRequiredForBatch);

                    // --- INICIO DE LA VERIFICACIÓN DE UMBRAL (RF7) ROBUSTA ---

                    final double EPSILON = 0.0001;

                    // Check 1: El stock ANTERIOR debe haber estado por encima del umbral.
                    // Usamos EPSILON para manejar la robustez de los puntos flotantes.
                    boolean wasAboveUmbral = previousStock > umbral + EPSILON;

                    // Check 2: El stock NUEVO debe estar en el umbral o por debajo.
                    boolean isBelowOrAtUmbral = newStock < umbral + EPSILON;

                    // LÍNEA DE DEBUG CRÍTICA: Muestra los valores y el resultado de la condición
                    System.out.println("--- DEBUG UMBRAL CHECK ---");
                    System.out.println("Insumo: " + colorKey + " | PrevStock: " + previousStock + " | Umbral: " + umbral + " | NewStock: " + newStock);
                    System.out.println("Condición 1 (wasAbove): " + wasAboveUmbral + " | Condición 2 (isBelow): " + isBelowOrAtUmbral + " | ALERTA: " + (wasAboveUmbral && isBelowOrAtUmbral));
                    System.out.println("--------------------------");

                    if (wasAboveUmbral && isBelowOrAtUmbral) {
                        reportMessages.add("🚨 ALERTA UMBRAL: " + colorKey +
                                " ha caído por debajo del umbral de " + String.format(Locale.US, "%.2f", umbral) + "g. Stock actual: " + String.format(Locale.US, "%.2f", newStock) + "g.");
                    }
                    // --- FIN DE LA VERIFICACIÓN ---

                    // Agregar al reporte
                    reportMessages.add(String.format("• %s (Prioridad %d): Descontado %.2fg. (Quedan %.2fg)",
                            colorKey, i + 1, totalRequiredForBatch, newStock));

                    System.out.println(">> Descuento: " + colorKey + " -" + totalRequiredForBatch + "g");
                } else {
                    // Si el usuario seleccionó 3 colores pero el archivo solo usa 2, el 3ro es 0g
                    reportMessages.add(String.format("• %s: No se detectó consumo asociado (0g).", colorKey));
                }
            }
        }

        // 4. AUMENTAR STOCK DE PIEZA
        // Usamos el nombre base (sin extensión) para que coincida con la vista de Inventario
        String pieceName = piece.getName();
        String pieceNameBase = pieceName.contains(".") ? pieceName.substring(0, pieceName.lastIndexOf('.')) : pieceName;
        String combinationKey = String.join("|", selectedColors);

        productRepository.increasePieceStockQuantity(pieceNameBase, combinationKey, quantity);

        System.out.println("✅ Stock aumentado para pieza: " + pieceNameBase + " | Color: " + combinationKey + " | Cant: " + quantity);
        System.out.println("--- DEBUG: Fin Registro Producción ---");

        return reportMessages;
    }
    public void registerProductAssembly(String masterCode, List<PieceStockDeduction> componentsUsed, int quantityProduced) throws IOException {
        if (componentsUsed == null || componentsUsed.isEmpty()) {
            throw new IllegalArgumentException("La lista de componentes no puede estar vacía.");
        }

        // 1. Descontar las piezas del stock (Lógica que antes usabas al eliminar)
        try {
            productRepository.decreasePieceStockBatch(componentsUsed);
        } catch (RuntimeException e) {
            throw new IOException("Error al descontar piezas: " + e.getMessage(), e);
        }

        // 2. Aumentar el stock del producto terminado (Lógica que antes usabas al agregar simple)
        masterProductRepository.increaseStock(masterCode, quantityProduced);

        System.out.println("✅ Ensamblaje registrado: " + quantityProduced + " unidades de " + masterCode);
    }
    // Method auxiliar privado para buscar el insumo (Parseo robusto)
    // Método auxiliar privado para buscar el insumo (Parseo robusto con Nombre)
    private Supply findSupplyByColorKey(List<Supply> allSupplies, String colorKey) {
        String targetName = "";
        String colorAndType = "";

        // 1. Intentamos separar el Nombre del resto usando el separador " | "
        if (colorKey.contains(" | ")) {
            String[] parts = colorKey.split(" \\| ");
            if (parts.length >= 2) {
                targetName = parts[0].trim();
                colorAndType = parts[1].trim();
            }
        } else {
            // Fallback por si acaso llega el formato viejo (solo COLOR TIPO)
            colorAndType = colorKey;
        }

        // 2. Separar Color y Tipo (Lógica existente)
        int lastSpaceIndex = colorAndType.lastIndexOf(' ');
        String color = "";
        String tipo = "";

        if (lastSpaceIndex != -1) {
            color = colorAndType.substring(0, lastSpaceIndex).trim(); // "ROJO"
            tipo = colorAndType.substring(lastSpaceIndex + 1).trim(); // "PLA"
        } else {
            color = colorAndType; // Fallback extremo
        }

        String finalColor = color;
        String finalTipo = tipo;
        String finalName = targetName;

        // 3. Búsqueda en la lista (Ahora incluye el Nombre si lo tenemos)
        return allSupplies.stream()
                .filter(s -> {
                    boolean matchesColorType = s.getColorFilamento().equalsIgnoreCase(finalColor) &&
                            s.getTipoFilamento().equalsIgnoreCase(finalTipo);

                    // Si tenemos nombre, también debe coincidir. Si no (formato viejo), lo ignoramos.
                    boolean matchesName = finalName.isEmpty() || s.getName().equalsIgnoreCase(finalName);

                    return matchesColorType && matchesName;
                })
                .findFirst()
                .orElse(null);
    }
    /**
     * Obtiene un insumo por su ID (delegación al repositorio).
     * @param id El ID del insumo.
     * @return El objeto Supply o null si no se encuentra.
     */
    public Supply getSupplyById(long id) {
        return supplyRepository.findByID(id);
    }
    /**
     * Elimina permanentemente un insumo del catálogo por su ID, con opción a forzar
     * la eliminación incluso si hay stock disponible.
     *
     * @param id El ID del insumo a eliminar.
     * @param forceDeletion Si es true, ignora la verificación de stock > 0.
     */
    public void deleteSupplyPermanently(long id, boolean forceDeletion) {
        Supply supply = supplyRepository.findByID(id);
        if (supply == null) {
            throw new IllegalArgumentException("Insumo no encontrado con ID: " + id);
        }

        if (!forceDeletion && supply.getCantidadDisponible() > 0.01) {
            throw new IllegalArgumentException("No se puede eliminar un insumo que tiene stock disponible. Se requiere forzar la eliminación.");
        }

        // Eliminación física del registro de la BD
        supplyRepository.delete(id);
        System.out.println("✅ Insumo eliminado permanentemente (Forzado: " + forceDeletion + "): ID " + id);
    }
}


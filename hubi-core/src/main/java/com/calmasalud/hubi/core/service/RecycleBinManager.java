package com.calmasalud.hubi.core.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Gestiona la papelera de reciclaje de la aplicación HUBI.
 * Los archivos eliminados del catálogo se mueven a esta papelera en lugar de borrarse permanentemente.
 */
public class RecycleBinManager {

    private static final Path REPOSITORIO_BASE =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "RepositorioArchivos");

    private static final Path RECYCLE_BIN_PATH =
            Paths.get(System.getProperty("user.home"), "SistemaHUBI", "PapeleraReciclaje");

    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    /**
     * Asegura que el directorio de la papelera existe.
     * @return true si existe o se creó exitosamente, false en caso contrario.
     */
    public static boolean ensureRecycleBinExists() {
        try {
            if (!Files.exists(RECYCLE_BIN_PATH)) {
                Files.createDirectories(RECYCLE_BIN_PATH);
                System.out.println("✅ Papelera de reciclaje creada en: " + RECYCLE_BIN_PATH);
                return true;
            }
            return true;
        } catch (IOException e) {
            System.err.println("❌ Error al crear la papelera de reciclaje: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mueve un archivo individual a la papelera de reciclaje.
     * El archivo se renombra con un timestamp para evitar colisiones.
     *
     * @param fileToDelete El archivo a mover a la papelera
     * @return true si se movió exitosamente, false en caso contrario
     */
    public static boolean moveToRecycleBin(File fileToDelete) {
        if (fileToDelete == null || !fileToDelete.exists()) {
            System.err.println("⚠️ Archivo no válido para mover a papelera: " + fileToDelete);
            return false;
        }

        if (!ensureRecycleBinExists()) {
            System.err.println("❌ No se pudo crear la papelera de reciclaje");
            return false;
        }

        try {
            String timestamp = TIMESTAMP_FORMAT.format(new Date());
            String originalName = fileToDelete.getName();
            String newName = timestamp + "_" + originalName;

            Path sourcePath = fileToDelete.toPath();
            Path targetPath = RECYCLE_BIN_PATH.resolve(newName);

            // Mover el archivo a la papelera
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("♻️ Archivo movido a papelera: " + originalName + " → " + newName);
            return true;

        } catch (IOException e) {
            System.err.println("❌ Error al mover archivo a papelera: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Mueve un directorio completo (producto) a la papelera de reciclaje.
     * El directorio se renombra con un timestamp para evitar colisiones.
     *
     * @param directoryToDelete El directorio a mover a la papelera
     * @return true si se movió exitosamente, false en caso contrario
     */
    public static boolean moveDirectoryToRecycleBin(File directoryToDelete) {
        if (directoryToDelete == null || !directoryToDelete.exists() || !directoryToDelete.isDirectory()) {
            System.err.println("⚠️ Directorio no válido para mover a papelera: " + directoryToDelete);
            return false;
        }

        // No permitir mover el repositorio base
        if (directoryToDelete.toPath().equals(REPOSITORIO_BASE)) {
            System.err.println("⚠️ No se puede mover el repositorio base a la papelera");
            return false;
        }

        if (!ensureRecycleBinExists()) {
            System.err.println("❌ No se pudo crear la papelera de reciclaje");
            return false;
        }

        try {
            String timestamp = TIMESTAMP_FORMAT.format(new Date());
            String originalDirName = directoryToDelete.getName();
            String newDirName = timestamp + "_" + originalDirName;

            Path sourcePath = directoryToDelete.toPath();
            Path targetPath = RECYCLE_BIN_PATH.resolve(newDirName);

            // Mover el directorio completo a la papelera
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("♻️ Directorio movido a papelera: " + originalDirName + " → " + newDirName);
            return true;

        } catch (IOException e) {
            System.err.println("❌ Error al mover directorio a papelera: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtiene la ruta de la papelera de reciclaje.
     * @return Path de la papelera
     */
    public static Path getRecycleBinPath() {
        return RECYCLE_BIN_PATH;
    }

    /**
     * Verifica si la papelera existe.
     * @return true si existe, false en caso contrario
     */
    public static boolean recycleBinExists() {
        return Files.exists(RECYCLE_BIN_PATH) && Files.isDirectory(RECYCLE_BIN_PATH);
    }
}


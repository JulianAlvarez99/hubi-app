package com.calmasalud.hubi.core.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Clase para extraer parámetros de archivos G-code y 3MF
public class FileParameterExtractor {

    // 💰 CONSTANTE DE COSTO ESTATICO (Por gramo de filamento)
    private static final double COSTO_POR_GRAMO_ESTATICO = 18.5;

    // --- PATRONES DE EXPRESIONES REGULARES (Reducidos) ---

    // Patrones de tiempo
    private static final Pattern P_TIME_SECONDS = Pattern.compile("^;\\s*TIME\\s*:\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TIME_HMS_TEXT = Pattern.compile("^;\\s*estimated printing time.*?=\\s*([0-9hms :]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TIME_ELAPSED = Pattern.compile("^;\\s*TIME_ELAPSED\\s*:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TIME_IN_NAME = Pattern.compile("([0-9]+)h([0-9]{1,2})m", Pattern.CASE_INSENSITIVE);

    // Patrones de filamento
    private static final Pattern P_FILAM_OS_MM = Pattern.compile("^;\\s*filament used\\s*\\[mm\\]\\s*=\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_OS_G = Pattern.compile("^;\\s*filament used\\s*\\[g\\]\\s*=\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_CURA_M = Pattern.compile("^;\\s*Filament used\\s*:\\s*([0-9.]+)\\s*m\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_EQ_G = Pattern.compile("^;\\s*filament\\s*used\\s*=\\s*([0-9.]+)\\s*g\\b", Pattern.CASE_INSENSITIVE);

    // Patrones de Material
    private static final Pattern P_FILAMENT_TYPE = Pattern.compile("^;\\s*filament_type\\s*=\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_MATERIAL = Pattern.compile("^;\\s*MATERIAL:?\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_COLOR = Pattern.compile("^;\\s*filament_colour\\s*=\\s*(#[0-9a-fA-F]{6}|\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_MATERIAL_COLOR = Pattern.compile("^;\\s*MATERIAL_COLOR:?\\s*(#[0-9a-fA-F]{6}|\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_DENSITY = Pattern.compile("^;\\s*filament_density\\s*:\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_DIAMETER = Pattern.compile("^;\\s*filament_diameter\\s*:\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);

    // PATRONES DE CONFIGURACIÓN RESTANTES
    private static final Pattern P_TOTAL_LAYERS = Pattern.compile("^;\\s*total layer number:?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_LAYER_HEIGHT = Pattern.compile("^;\\s*layer_height\\s*=\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);

    // Patrones generales
    private static final Pattern P_OBJECT = Pattern.compile("^;\\s*(OBJECT|MESH)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_COLOR_CHANGE_GC = Pattern.compile("^(;\\s*COLOR_CHANGE|M600)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TOOL_CHANGE = Pattern.compile("^T(\\d+)\\s*(;.*)?$");

    // --- CLASE DE RESULTADO ---

    public static class PrintInfo {
        public String timeHuman;
        public int pieces = 0;
        public int colorChanges = 0;
        public String filamentType;
        public String filamentDensity;
        public String filamentDiameter;
        public String filamentColor;
        public String filamentColorName;
        public String filamentAmountM;
        public String filamentAmountG;
        public String layerHeight;
        public int totalLayers = 0;
        //public String totalCost; // Costo total (Calculado)
    }

    // --- MÉTODO DE EXTRACCIÓN PRINCIPAL ---

    public PrintInfo extract(List<File> files) {
        PrintInfo info = new PrintInfo();

        // Ordenar: G-code primero, 3MF después (para que 3MF sobrescriba)
        files.sort(Comparator.comparing(f -> f.getName().toLowerCase().endsWith(".3mf")));

        for (File f : files) {
            if (!f.exists()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            try {
                if (name.endsWith(".gcode")) {
                    parseGCode(f.toPath(), info);
                    if (info.timeHuman == null) {
                        String t = timeFromFilename(f.getName());
                        if (t != null) info.timeHuman = t;
                    }
                } else if (name.endsWith(".3mf")) {
                    parse3MF(f.toPath(), info);
                }
            } catch (IOException e) {
                System.err.println("Error al procesar archivo " + f.getName() + ": " + e.getMessage());
            }
        }

        // --- POSTPROCESO Y CÁLCULOS ---
        if (info.filamentColor != null) {
            info.filamentColorName = getColorNameFromHex(info.filamentColor);
        }

        if (info.layerHeight != null && info.layerHeight.endsWith(" mm")) {
            try {
                double mm = Double.parseDouble(info.layerHeight.replace(" mm", ""));
                info.layerHeight = trimNum(mm) + " mm";
            } catch (NumberFormatException e) { /* Ignorar */ }
        } else if (info.layerHeight != null && !info.layerHeight.isEmpty()) {
            try {
                double val = Double.parseDouble(info.layerHeight);
                info.layerHeight = trimNum(val) + " mm";
            } catch (NumberFormatException e) {/* Ignorar */}
        }




        return info;
    }


    // --- LÓGICA DE PARSEO ---

    private void parseGCode(Path gcode, PrintInfo info) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(gcode, StandardCharsets.UTF_8)) {
            String line;
            Set<String> uniqueObjects = new HashSet<>();
            String lastTool = null;

            Double osFilamentValueMM = null;
            Double osFilamentValueG = null;

            double filamentTotalValueM = -1;
            String filamentTotalUnitM = null;
            double filamentTotalValueG = -1;
            String filamentTotalUnitG = null;


            while ((line = br.readLine()) != null) {
                String l = line.trim();

                Matcher m;
                // Búsqueda de tiempo y Capas Totales
                if ((m = P_TIME_SECONDS.matcher(l)).matches()) {
                    info.timeHuman = humanTimeFromSeconds(Long.parseLong(m.group(1)));
                    continue;
                }
                if ((m = P_TIME_HMS_TEXT.matcher(l)).matches()) {
                    info.timeHuman = normalizeHumanTime(m.group(1));
                    continue;
                }
                if ((m = P_TIME_ELAPSED.matcher(l)).matches() && info.timeHuman == null) {
                    double sec = Double.parseDouble(m.group(1));
                    info.timeHuman = humanTimeFromSeconds((long) Math.round(sec));
                    continue;
                }
                // Capas Totales
                if ((m = P_TOTAL_LAYERS.matcher(l)).matches()) {
                    info.totalLayers = Integer.parseInt(m.group(1));
                }

                // BÚSQUEDA DE VALORES DE FILAMENTO ESPECÍFICOS
                if ((m = P_FILAM_OS_MM.matcher(l)).matches()) {
                    osFilamentValueMM = Double.parseDouble(m.group(1));
                }
                if ((m = P_FILAM_OS_G.matcher(l)).matches()) {
                    osFilamentValueG = Double.parseDouble(m.group(1));
                }
                if ((m = P_FILAM_CURA_M.matcher(l)).matches()) {
                    filamentTotalValueM = Double.parseDouble(m.group(1));
                    filamentTotalUnitM = "m";
                }
                if ((m = P_FILAM_EQ_G.matcher(l)).matches()) {
                    filamentTotalValueG = Double.parseDouble(m.group(1));
                    filamentTotalUnitG = "g";
                }

                // Búsqueda de Densidad, Diámetro, Tipo y Color
                if ((m = P_FILAMENT_DENSITY.matcher(l)).matches()) {
                    info.filamentDensity = m.group(1) + " g/cm³";
                }
                if ((m = P_FILAMENT_DIAMETER.matcher(l)).matches()) {
                    info.filamentDiameter = m.group(1) + " mm";
                }
                if ((m = P_FILAMENT_TYPE.matcher(l)).matches()) {
                    info.filamentType = m.group(1);
                } else if ((m = P_FILAMENT_MATERIAL.matcher(l)).matches()) {
                    info.filamentType = m.group(1);
                }
                if ((m = P_FILAMENT_COLOR.matcher(l)).matches()) {
                    info.filamentColor = m.group(1);
                } else if ((m = P_MATERIAL_COLOR.matcher(l)).matches()) {
                    info.filamentColor = m.group(1);
                }

                // PARÁMETROS DE CONFIGURACIÓN RESTANTES
                if ((m = P_LAYER_HEIGHT.matcher(l)).matches()) {
                    info.layerHeight = m.group(1) + " mm";
                }

                // Búsqueda de Piezas / Objetos y Cambios de Color
                if ((m = P_OBJECT.matcher(l)).matches()) {
                    String obj = m.group(2).trim();
                    if (!obj.equalsIgnoreCase("NONMESH")) {
                        uniqueObjects.add(obj);
                        info.pieces = Math.max(info.pieces, uniqueObjects.size());
                    }
                }
                if (P_COLOR_CHANGE_GC.matcher(l).find()) {
                    info.colorChanges++;
                }
                if ((m = P_TOOL_CHANGE.matcher(l)).matches()) {
                    String t = m.group(1);
                    if (!t.equals(lastTool)) {
                        if (lastTool != null) info.colorChanges++;
                        lastTool = t;
                    }
                }
            }

            // POSTPROCESO: PRIORIZAR VALORES DE FILAMENTO
            if (osFilamentValueMM != null) {
                double valM = osFilamentValueMM / 1000.0;
                info.filamentAmountM = trimNum(valM) + " m";
            } else if (filamentTotalUnitM != null) {
                info.filamentAmountM = trimNum(filamentTotalValueM) + " " + filamentTotalUnitM;
            }

            if (osFilamentValueG != null) {
                info.filamentAmountG = trimNum(osFilamentValueG) + " g";
            } else if (filamentTotalUnitG != null) {
                info.filamentAmountG = trimNum(filamentTotalValueG) + " " + filamentTotalUnitG;
            }
        }
    }

    private void parse3MF(Path threeMF, PrintInfo info) {
        try (ZipFile zip = new ZipFile(threeMF.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().toLowerCase(Locale.ROOT);

                if (name.endsWith(".ini") || name.contains("metadata") || name.endsWith(".config")) {
                    try (InputStream is = zip.getInputStream(e);
                         BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String l = line.trim();
                            Matcher m;

                            m = Pattern.compile("^filament_type\\s*=\\s*(\\S+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) info.filamentType = m.group(1);
                            m = Pattern.compile("^filament_colour\\s*=\\s*(#[0-9a-fA-F]{6}|\\S+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) info.filamentColor = m.group(1);
                            m = Pattern.compile("^filament_density\\s*=\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) info.filamentDensity = m.group(1) + " g/cm³";
                            m = Pattern.compile("^filament_diameter\\s*=\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) info.filamentDiameter = m.group(1) + " mm";
                            m = Pattern.compile("^estimated printing time.*=\\s*([0-9hms :]+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) info.timeHuman = normalizeHumanTime(m.group(1));

                            m = P_LAYER_HEIGHT.matcher(l);
                            if (m.find()) info.layerHeight = m.group(1) + " mm";

                            m = Pattern.compile("filament (used|total)\\s*=\\s*([0-9.]+)\\s*m", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) {
                                info.filamentAmountM = trimNum(Double.parseDouble(m.group(2))) + " m";
                            }
                            m = Pattern.compile("filament (used|total)\\s*=\\s*([0-9.]+)\\s*g", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) {
                                info.filamentAmountG = trimNum(Double.parseDouble(m.group(2))) + " g";
                            }
                        }
                    } catch (Exception ignore) {}
                }
            }

            ZipEntry model = zip.getEntry("3D/3dmodel.model");
            if (model != null) {
                try (InputStream is = zip.getInputStream(model)) {
                    String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Matcher mObj = Pattern.compile("<object\\b", Pattern.CASE_INSENSITIVE).matcher(xml);
                    int count = 0;
                    while (mObj.find()) count++;
                    if (count > 0) {
                        info.pieces = Math.max(info.pieces, count);
                    }
                }
            }
        } catch (Exception ignore) {}
    }


    // --- MÉTODOS AUXILIARES (del extractor) ---

    private static String getColorNameFromHex(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            if (hex != null && !hex.isEmpty() && !hex.startsWith("#")) {
                return hex;
            }
            return null;
        }
        // Lógica de traducción HEX -> Nombre (simplificada)
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);

            if (r > 240 && g > 240 && b > 240) return "Blanco";
            if (r < 15 && g < 15 && b < 15) return "Negro";

            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            if (max - min < 40 && max < 200) return "Gris";

            if (r > 200 && g < 100 && b < 100) return "Rojo";
            if (g > 200 && r < 100 && b < 100) return "Verde";
            if (b > 200 && r < 100 && g < 100) return "Azul";

            if (r > 200 && g > 200 && b < 100) return "Amarillo";
            if (r > 200 && b > 200 && g < 100) return "Magenta";
            if (g > 200 && b > 200 && r < 100) return "Cian";

            if (r > 180 && g > 100 && g < 160 && b < 80) return "Naranja";
            if (r > 200 && b > 100 && g < 150) return "Rosa";
            if (max == r && min == b && g < 150) return "Marrón";

            return "Otro";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String humanTimeFromSeconds(long totalSec) {
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%dh %dm", h, m);
        if (m > 0) return String.format("%dm %ds", m, s);
        return String.format("%ds", s);
    }

    private static String normalizeHumanTime(String raw) {
        String t = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        Matcher hm = Pattern.compile("(\\d+)h(\\d{1,2})m").matcher(t);
        if (hm.find()) return hm.group(1) + "h " + hm.group(2) + "m";
        Matcher hOnly = Pattern.compile("(\\d+)h").matcher(t);
        Matcher mOnly = Pattern.compile("(\\d{1,2})m").matcher(t);
        if (hOnly.find() && mOnly.find()) return hOnly.group(1) + "h " + mOnly.group(1) + "m";
        Matcher clock = Pattern.compile("(\\d+):(\\d{2})(?::(\\d{2}))?").matcher(raw);
        if (clock.find()) {
            int h = Integer.parseInt(clock.group(1));
            int m = Integer.parseInt(clock.group(2));
            return h + "h " + m + "m";
        }
        return raw;
    }

    private static String timeFromFilename(String filename) {
        Matcher m = P_TIME_IN_NAME.matcher(filename);
        if (m.find()) return m.group(1) + "h " + m.group(2) + "m";
        return null;
    }

    private static String nz(String s, String d) { return (s == null || s.isEmpty()) ? d : s; }

    private static String trimNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.2f", v);
    }

}
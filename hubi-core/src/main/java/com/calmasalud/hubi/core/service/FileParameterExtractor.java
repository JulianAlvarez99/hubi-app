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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Clase para extraer parámetros de archivos G-code y 3MF
public class FileParameterExtractor {

    // 💰 CONSTANTE DE COSTO ESTATICO (Por gramo de filamento)
    private static final double COSTO_POR_GRAMO_ESTATICO = 18.5;

    // --- PATRONES DE EXPRESIONES REGULARES ---
    private static final Pattern P_TIME_SECONDS = Pattern.compile("^;\\s*TIME\\s*:\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TIME_HMS_TEXT = Pattern.compile("^;\\s*estimated printing time.*?=\\s*([0-9hms :]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TIME_ELAPSED = Pattern.compile("^;\\s*TIME_ELAPSED\\s*:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_TIME_IN_NAME = Pattern.compile("([0-9]+)h([0-9]{1,2})m", Pattern.CASE_INSENSITIVE);

    // Patrones de filamento indexado (Multi-Color)
    private static final Pattern P_FILAM_USED_MM_INDEXED = Pattern.compile("^;\\s*filament_used\\[(\\d+)\\]\\s*=\\s*([0-9.]+)\\s*\\[mm\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_USED_G_INDEXED = Pattern.compile("^;\\s*filament_used\\[(\\d+)\\]\\s*=\\s*([0-9.]+)\\s*\\[g\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_TYPE_INDEXED = Pattern.compile("^;\\s*filament_type\\[(\\d+)\\]\\s*=\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_COLOR_INDEXED = Pattern.compile("^;\\s*filament_colour\\[(\\d+)\\]\\s*=\\s*(#[0-9a-fA-F]{6}|\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_DENSITY_INDEXED = Pattern.compile("^;\\s*filament_density\\[(\\d+)\\]\\s*:\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_DIAMETER_INDEXED = Pattern.compile("^;\\s*filament_diameter\\[(\\d+)\\]\\s*:\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_USED_MULTI =
            Pattern.compile("^;\\s*filament used by extruder (\\d+)\\s*:\\s*([0-9.]+) m\\s*([0-9.]+) g",
                    Pattern.CASE_INSENSITIVE);
    // Patrones de encabezado (Multi-Color, valores separados por coma, ej: OrcaSlicer)
// ... (Tus patrones existentes)

    private static final Pattern P_FILAM_USED_MM_COMMA = Pattern.compile("^;\\s*filament used\\s*\\[mm\\]\\s*=\\s*([0-9.,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_USED_G_COMMA = Pattern.compile("^;\\s*filament used\\s*\\[g\\]\\s*=\\s*([0-9.,\\s]+)", Pattern.CASE_INSENSITIVE);

    // Patrones de encabezado (Multi-Color, valores separados por coma, ej: OrcaSlicer)
    private static final Pattern P_FILAMENT_DENSITY_COMMA = Pattern.compile("^;\\s*filament_density:\\s*([0-9.,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_DIAMETER_COMMA = Pattern.compile("^;\\s*filament_diameter:\\s*([0-9.,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_TYPE_COMMA = Pattern.compile("^;\\s*filament_type:\\s*([A-Za-z0-9,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_COLOR_COMMA = Pattern.compile("^;\\s*filament_colour:\\s*([#\\sA-Za-z0-9,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_EXTRUDER_COLOUR =
            Pattern.compile("^;\\s*extruder_colour\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAMENT_TYPE_SEMICOLON = Pattern.compile("^;\\s*filament_type\\s*=\\s*([A-Za-z0-9;\\s]+)", Pattern.CASE_INSENSITIVE);

    // Patrones de filamento (Legacy/General - Solo T0)
    private static final Pattern P_FILAM_OS_MM = Pattern.compile("^;\\s*filament used\\s*\\[mm\\]\\s*=\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_OS_G = Pattern.compile("^;\\s*filament used\\s*\\[g\\]\\s*=\\s*([0-9.]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_CURA_M = Pattern.compile("^;\\s*Filament used\\s*:\\s*([0-9.]+)\\s*m\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_FILAM_EQ_G = Pattern.compile("^;\\s*filament\\s*used\\s*=\\s*([0-9.]+)\\s*g\\b", Pattern.CASE_INSENSITIVE);

    // Patrones de Material (Legacy)
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

    // --- CLASE DE PERFIL DE FILAMENTO ---

    public static class FilamentProfile {
        private final int toolIndex;

        public String filamentType = "N/D";
        public String filamentDensity = "N/D";
        public String filamentDiameter = "N/D";
        public String filamentColor;
        public String filamentColorName;
        public String filamentAmountM;
        public String filamentAmountG;

        // Constructor que recibe el índice de herramienta
        public FilamentProfile(int toolIndex) {
            this.toolIndex = toolIndex;
        }

        public int getToolIndex() {
            return toolIndex;
        }

        @Override
        public String toString() {
            // Si el nombre de color es N/D, muestra el color HEX si está disponible, sino "Filamento"
            String color = (filamentColorName != null && !filamentColorName.startsWith("N/D")) ? filamentColorName :
                    (filamentColor != null ? filamentColor : "Filamento");

            String type = filamentType.equals("N/D") ? "" : " | " + filamentType;
            String amount = (filamentAmountG != null && !filamentAmountG.startsWith("N/D")) ? " | " + filamentAmountG : "";

            // USAR toolIndex almacenado para la etiqueta
            return  color + type + amount;
        }
    }


    // --- CLASE DE RESULTADO PRINCIPAL ---

    public static class PrintInfo {
        public String timeHuman;
        public int pieces = 0;
        public int colorChanges = 0;
        public String layerHeight;
        public int totalLayers = 0;

        // Mapa para almacenar los perfiles de filamento, key = Tool Index (0, 1, 2...)
        public final java.util.Map<Integer, FilamentProfile> filamentProfiles = new java.util.HashMap<>();
    }

    // --- Method DE EXTRACCIÓN PRINCIPAL ---

    public PrintInfo extract(List<File> files) {
        PrintInfo info = new PrintInfo();
        // Inicializar con el perfil T0/Default
        info.filamentProfiles.put(0, new FilamentProfile(0));

        // Ordenar: G-code primero
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

        // 1. Limpieza de perfiles (SOLO si no tienen datos, manteniendo T0 si hay otros)
        info.filamentProfiles.entrySet().removeIf(entry -> {
            FilamentProfile p = entry.getValue();

            // Si es T0, NUNCA lo eliminamos si se detectaron otros tools
            if (entry.getKey() == 0 && info.filamentProfiles.size() > 1) {
                return false;
            }

            // Si es T0 y es el ÚNICO perfil, lo conservamos (caso de un solo color simple)
            if (entry.getKey() == 0 && info.filamentProfiles.size() == 1) {
                return false;
            }

            // Si NO es T0, lo eliminamos si no tiene peso/largo Y no tiene características (Type/Color)
            boolean hasNoWeight = p.filamentAmountG == null || p.filamentAmountG.equals("N/D");
            boolean hasNoCharacteristics = p.filamentType.equals("N/D") && p.filamentColor == null;

            // Si el perfil tiene índice > 0, pero no tiene datos, lo eliminamos.
            return entry.getKey() != 0 && hasNoWeight && hasNoCharacteristics;
        });

        // 2. Asignar nombres de color y formatear cantidades
        for (Map.Entry<Integer, FilamentProfile> entry : info.filamentProfiles.entrySet()) {
            FilamentProfile profile = entry.getValue();
            if (profile.filamentColor != null) {
                profile.filamentColorName = getColorNameFromHex(profile.filamentColor);
            } else {
                profile.filamentColorName = "N/D";
            }
            if (profile.filamentAmountG == null) profile.filamentAmountG = "N/D";
            if (profile.filamentAmountM == null) profile.filamentAmountM = "N/D";
        }

        // 3. Postproceso para layerHeight
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


    // --- LÓGICA DE PARSEO DE GCODE (CORREGIDA PARA ASIGNACIÓN DE PESO/LARGO ROBUSTA) ---

    private void parseGCode(Path gcode, PrintInfo info) throws IOException {
        System.out.println("--- Iniciando Parseo de GCode ---");
        try (BufferedReader br = Files.newBufferedReader(gcode, StandardCharsets.UTF_8)) {
            String line;
            int currentTool = -1;
            boolean indexedDataFound = false;

            // Variables para acumular valores reportados en el header (por índice)
            Map<Integer, Double> accumulatedMM = new java.util.HashMap<>();
            Map<Integer, Double> accumulatedG = new java.util.HashMap<>();

            // Variables para valores legacy (solo si no hay indexados)
            Double osFilamentValueMM = null;
            Double osFilamentValueG = null;
            double filamentTotalValueM = -1;
            String filamentTotalUnitM = null;
            double filamentTotalValueG = -1;
            String filamentTotalUnitG = null;

            while ((line = br.readLine()) != null) {
                String l = line.trim();
                Matcher m;
                m = P_FILAMENT_TYPE_SEMICOLON.matcher(l);
                if (m.matches()) {
                    // Dividir por punto y coma
                    String[] values = m.group(1).split(";");
                    System.out.println("LOG: Detectado filament_type (;) : " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String type = values[i].trim();
                        if (!type.isEmpty() && !type.equalsIgnoreCase("N/D")) {
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentType = type;
                            indexedDataFound = true;
                        }
                    }
                }
                // Nuevo: Largo de Filamento (Comma-separated)
                m = P_FILAM_USED_MM_COMMA.matcher(l);
                if (m.matches()) {
                    // La lista de valores puede tener comas o puntos. Reemplazamos comas por puntos para que Double.parseDouble funcione correctamente en el Locale.US implícito del trimNum.
                    String[] values = m.group(1).split(",");
                    System.out.println("LOG: Detectado filament used [mm]: " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String mmStr = values[i].trim().replace(',', '.'); // Asegurar formato decimal con punto
                        try {
                            double mm = Double.parseDouble(mmStr);
                            // Asignar el valor de largo (en metros) al perfil del extrusor
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentAmountM = trimNum(mm / 1000.0) + " m";
                            indexedDataFound = true;
                        } catch (NumberFormatException e) { /* Ignorar valores no válidos */ }
                    }
                }

                // Nuevo: Peso de Filamento (Comma-separated)
                m = P_FILAM_USED_G_COMMA.matcher(l);
                if (m.matches()) {
                    String[] values = m.group(1).split(",");
                    System.out.println("LOG: Detectado filament used [g]: " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String gStr = values[i].trim().replace(',', '.'); // Asegurar formato decimal con punto
                        try {
                            double g = Double.parseDouble(gStr);
                            // Asignar el valor de peso (en gramos) al perfil del extrusor
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentAmountG = trimNum(g) + " g";
                            indexedDataFound = true;
                        } catch (NumberFormatException e) { /* Ignorar valores no válidos */ }
                    }
                }
                if ((m = P_FILAMENT_USED_MULTI.matcher(l)).matches()) {
                    int t = Integer.parseInt(m.group(1));
                    String mVal = trimNum(Double.parseDouble(m.group(2))) + " m";
                    String gVal = trimNum(Double.parseDouble(m.group(3))) + " g";

                    FilamentProfile fp = info.filamentProfiles.computeIfAbsent(t, FilamentProfile::new);
                    fp.filamentAmountM = mVal;
                    fp.filamentAmountG = gVal;

                    System.out.println("DEBUG: Tool " + t + " detectado | " + mVal + " | " + gVal);
                }

                // Búsqueda de tiempo, capas, objetos (GENERALES)
                if ((m = P_TIME_SECONDS.matcher(l)).matches()) {
                    info.timeHuman = humanTimeFromSeconds(Long.parseLong(m.group(1)));
                    continue;
                }
                if ((m = P_EXTRUDER_COLOUR.matcher(l)).matches()) {
                    String[] colors = m.group(1).split(";");
                    for (int i = 0; i < colors.length; i++) {
                        String hex = colors[i].trim();
                        FilamentProfile fp = info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new);
                        fp.filamentColor = hex;
                    }
                    System.out.println("DEBUG: Colores detectados para " + colors.length + " extruders");
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
                if ((m = P_TOTAL_LAYERS.matcher(l)).matches()) {
                    info.totalLayers = Integer.parseInt(m.group(1));
                }


                // --- 1. HANDLE COMMA-SEPARATED HEADER LISTS (ASIGNAR A TODOS LOS PROFILES) ---

                // Densidad (Comma-separated)
                m = P_FILAMENT_DENSITY_COMMA.matcher(l);
                if (m.matches()) {
                    String[] values = m.group(1).split(",");
                    System.out.println("LOG: Detectado filament_density: " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String density = values[i].trim();
                        if (!density.isEmpty() && !density.equalsIgnoreCase("N/A")) {
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentDensity = density + " g/cm³";
                            indexedDataFound = true;
                        }
                    }
                }

                // Diámetro (Comma-separated)
                m = P_FILAMENT_DIAMETER_COMMA.matcher(l);
                if (m.matches()) {
                    String[] values = m.group(1).split(",");
                    System.out.println("LOG: Detectado filament_diameter: " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String diameter = values[i].trim();
                        if (!diameter.isEmpty() && !diameter.equalsIgnoreCase("N/A")) {
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentDiameter = diameter + " mm";
                            indexedDataFound = true;
                        }
                    }
                }

                // Tipo de Filamento (Comma-separated)
                m = P_FILAMENT_TYPE_COMMA.matcher(l);
                if (m.matches()) {
                    String[] values = m.group(1).split(",");
                    System.out.println("LOG: Detectado filament_type: " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String type = values[i].trim();
                        if (!type.isEmpty() && !type.equalsIgnoreCase("N/D")) {
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentType = type;
                            indexedDataFound = true;
                        }
                    }
                }

                // Color del Filamento (Comma-separated)
                m = P_FILAMENT_COLOR_COMMA.matcher(l);
                if (m.matches()) {
                    String[] values = m.group(1).split(",");
                    System.out.println("LOG: Detectado filament_colour: " + values.length + " valores.");
                    for (int i = 0; i < values.length; i++) {
                        String color = values[i].trim();
                        if (!color.isEmpty() && !color.equalsIgnoreCase("N/D")) {
                            info.filamentProfiles.computeIfAbsent(i, FilamentProfile::new).filamentColor = color;
                            indexedDataFound = true;
                        }
                    }
                }


                // --- 2. EXTRACCIÓN DE PARÁMETROS INDEXADOS (Peso, Largo, Tipo, Color) ---
                // ACUMULADOR DE PESO/LARGO (CRÍTICO para multi-color)

                m = P_FILAM_USED_MM_INDEXED.matcher(l);
                if (m.matches()) {
                    int toolIdx = Integer.parseInt(m.group(1));
                    accumulatedMM.put(toolIdx, Double.parseDouble(m.group(2)));
                    indexedDataFound = true;
                    System.out.println("LOG: Acumulado MM en T" + toolIdx);
                }

                m = P_FILAM_USED_G_INDEXED.matcher(l);
                if (m.matches()) {
                    int toolIdx = Integer.parseInt(m.group(1));
                    accumulatedG.put(toolIdx, Double.parseDouble(m.group(2)));
                    indexedDataFound = true;
                    System.out.println("LOG: Acumulado G en T" + toolIdx);
                }

                // El resto de los indexados individuales (Tipo, Color, Densidad, Diámetro)
                m = P_FILAMENT_TYPE_INDEXED.matcher(l);
                if (m.matches()) {
                    int toolIdx = Integer.parseInt(m.group(1));
                    info.filamentProfiles.computeIfAbsent(toolIdx, FilamentProfile::new).filamentType = m.group(2);
                    indexedDataFound = true;
                }
                m = P_FILAMENT_COLOR_INDEXED.matcher(l);
                if (m.matches()) {
                    int toolIdx = Integer.parseInt(m.group(1));
                    info.filamentProfiles.computeIfAbsent(toolIdx, FilamentProfile::new).filamentColor = m.group(2);
                    indexedDataFound = true;
                }
                m = P_FILAMENT_DENSITY_INDEXED.matcher(l);
                if (m.matches()) {
                    int toolIdx = Integer.parseInt(m.group(1));
                    info.filamentProfiles.computeIfAbsent(toolIdx, FilamentProfile::new).filamentDensity = m.group(2) + " g/cm³";
                    indexedDataFound = true;
                }
                m = P_FILAMENT_DIAMETER_INDEXED.matcher(l);
                if (m.matches()) {
                    int toolIdx = Integer.parseInt(m.group(1));
                    info.filamentProfiles.computeIfAbsent(toolIdx, FilamentProfile::new).filamentDiameter = m.group(2) + " mm";
                    indexedDataFound = true;
                }


                // --- 3. BÚSQUEDA DE VALORES DE FILAMENTO LEGACY (Solo si NO hay datos indexados) ---
                if (!indexedDataFound) {

                    if ((m = P_FILAM_OS_MM.matcher(l)).matches()) {
                        osFilamentValueMM = Double.parseDouble(m.group(1));
                        System.out.println("LOG: Capturado Legacy MM: " + osFilamentValueMM);
                    }
                    if ((m = P_FILAM_OS_G.matcher(l)).matches()) {
                        osFilamentValueG = Double.parseDouble(m.group(1));
                        System.out.println("LOG: Capturado Legacy G: " + osFilamentValueG);
                    }
                    if ((m = P_FILAM_CURA_M.matcher(l)).matches()) {
                        filamentTotalValueM = Double.parseDouble(m.group(1));
                        filamentTotalUnitM = "m";
                        System.out.println("LOG: Capturado Cura M: " + filamentTotalValueM);
                    }
                    if ((m = P_FILAM_EQ_G.matcher(l)).matches()) {
                        filamentTotalValueG = Double.parseDouble(m.group(1));
                        filamentTotalUnitG = "g";
                        System.out.println("LOG: Capturado Eq G: " + filamentTotalValueG);
                    }

                    // Búsqueda de Densidad, Diámetro, Tipo y Color (Legacy, se aplican a T0)
                    FilamentProfile t0 = info.filamentProfiles.get(0);
                    if ((m = P_FILAMENT_DENSITY.matcher(l)).matches()) { t0.filamentDensity = m.group(1) + " g/cm³"; }
                    if ((m = P_FILAMENT_DIAMETER.matcher(l)).matches()) { t0.filamentDiameter = m.group(1) + " mm"; }
                    if ((m = P_FILAMENT_TYPE.matcher(l)).matches()) { t0.filamentType = m.group(1); }
                    else if ((m = P_FILAMENT_MATERIAL.matcher(l)).matches()) { t0.filamentType = m.group(1); }
                    if ((m = P_FILAMENT_COLOR.matcher(l)).matches()) { t0.filamentColor = m.group(1); }
                    else if ((m = P_MATERIAL_COLOR.matcher(l)).matches()) { t0.filamentColor = m.group(1); }
                }

                // PARÁMETROS DE CONFIGURACIÓN RESTANTES
                if ((m = P_LAYER_HEIGHT.matcher(l)).matches()) {
                    info.layerHeight = m.group(1) + " mm";
                }

                // Búsqueda de Piezas / Objetos y Cambios de Color
                if ((m = P_OBJECT.matcher(l)).matches()) {
                    String obj = m.group(2).trim();
                    if (!obj.equalsIgnoreCase("NONMESH")) {
                        info.pieces = Math.max(info.pieces, 1); // Simplificado para GCode
                    }
                }

                // --- CÓDIGO DE CAMBIOS DE COLOR ---
                if ((m = P_TOOL_CHANGE.matcher(l)).matches()) {
                    int newTool = Integer.parseInt(m.group(1));
                    // Solo contamos el cambio de color si la herramienta anterior fue inicializada
                    if (currentTool != -1 && newTool != currentTool) {
                        info.colorChanges++;
                    }
                    currentTool = newTool;
                } else if (P_COLOR_CHANGE_GC.matcher(l).find()) {
                    info.colorChanges++;
                }
            }

            // --- POSTPROCESO FINAL: ASIGNAR PESO/LARGO ---

            // Verificamos si se encontró ALGO de uso indexado.
            boolean anyIndexedWeightFound = !accumulatedG.isEmpty() || !accumulatedMM.isEmpty();
            System.out.println("LOG: anyIndexedWeightFound=" + anyIndexedWeightFound);

            if (anyIndexedWeightFound) {
                // 1. Asignar Peso/Largo de los acumuladores indexados. (Multi-color éxito)
                accumulatedMM.forEach((toolId, mm) -> {
                    FilamentProfile profile = info.filamentProfiles.computeIfAbsent(toolId, FilamentProfile::new);
                    if (mm > 0) profile.filamentAmountM = trimNum(mm / 1000.0) + " m";
                });
                accumulatedG.forEach((toolId, g) -> {
                            FilamentProfile profile = info.filamentProfiles.computeIfAbsent(toolId, FilamentProfile::new);
                            if (g > 0) profile.filamentAmountG = trimNum(g) + " g";
                        }
                );
            }

            // 2. Fallback Legacy para T0 (Se ejecuta si no se encontró Peso indexado, o si se trata de un solo color)
            FilamentProfile t0 = info.filamentProfiles.get(0);

            // Asignar metros
            if (osFilamentValueMM != null) {
                double valM = osFilamentValueMM / 1000.0;
                t0.filamentAmountM = trimNum(valM) + " m";
                System.out.println("LOG: Fallback T0 (M) aplicado: " + t0.filamentAmountM);
            } else if (filamentTotalUnitM != null) {
                t0.filamentAmountM = trimNum(filamentTotalValueM) + " " + filamentTotalUnitM;
                System.out.println("LOG: Fallback T0 (M) aplicado: " + t0.filamentAmountM);
            }

            // Asignar gramos
            if (osFilamentValueG != null) {
                t0.filamentAmountG = trimNum(osFilamentValueG) + " g";
                System.out.println("LOG: Fallback T0 (G) aplicado: " + t0.filamentAmountG);
            } else if (filamentTotalUnitG != null) {
                t0.filamentAmountG = trimNum(filamentTotalValueG) + " " + filamentTotalUnitG;
                System.out.println("LOG: Fallback T0 (G) aplicado: " + t0.filamentAmountG);
            }
        }
    }

    // Lógica de parseo 3MF (Simplificada)
    private void parse3MF(Path threeMF, PrintInfo info) {
        // La lógica del 3MF es simple y solo apunta a T0
        try (ZipFile zip = new ZipFile(threeMF.toFile())) {
            FilamentProfile t0 = info.filamentProfiles.get(0);

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
                            if (m.find()) t0.filamentType = m.group(1);
                            m = Pattern.compile("^filament_colour\\s*=\\s*(#[0-9a-fA-F]{6}|\\S+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) t0.filamentColor = m.group(1);
                            m = Pattern.compile("^filament_density\\s*=\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) t0.filamentDensity = m.group(1) + " g/cm³";
                            m = Pattern.compile("^filament_diameter\\s*=\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) t0.filamentDiameter = m.group(1) + " mm";
                            m = Pattern.compile("^estimated printing time.*=\\s*([0-9hms :]+)", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) info.timeHuman = normalizeHumanTime(m.group(1));

                            m = P_LAYER_HEIGHT.matcher(l);
                            if (m.find()) info.layerHeight = m.group(1) + " mm";

                            m = Pattern.compile("filament (used|total)\\s*=\\s*([0-9.]+)\\s*m", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) {
                                t0.filamentAmountM = trimNum(Double.parseDouble(m.group(2))) + " m";
                            }
                            m = Pattern.compile("filament (used|total)\\s*=\\s*([0-9.]+)\\s*g", Pattern.CASE_INSENSITIVE).matcher(l);
                            if (m.find()) {
                                t0.filamentAmountG = trimNum(Double.parseDouble(m.group(2))) + " g";
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


    // --- MethodS AUXILIARES (del extractor) ---

    private static String getColorNameFromHex(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            if (hex != null && !hex.isEmpty() && !hex.startsWith("#")) {
                return hex;
            }
            return "N/D";
        }
        // Lógica de traducción HEX -> Nombre (simplificada y expandida)
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);

            if (r > 240 && g > 240 && b > 240) return "Blanco";
            if (r < 15 && g < 15 && b < 15) return "Negro";

            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            if (max - min < 40 && max < 200) return "Gris";

            // Nueva regla para Turquesa/Teal (ej: #26A69A -> Turquesa)
            if (r < 60 && g > 120 && b > 120) return "Turquesa";

            if (r > 200 && g < 100 && b < 100) return "Rojo";
            if (g > 200 && r < 100 && b < 100) return "Verde";
            if (b > 200 && r < 100 && g < 100) return "Azul";

            if (r > 200 && g > 200 && b < 160) return "Amarillo";
            if (r > 200 && b > 200 && g < 100) return "Magenta";
            if (g > 200 && b > 200 && r < 100) return "Cian";

            if (r > 180 && g > 100 && g < 160 && b < 80) return "Naranja";
            if (r > 200 && b > 100 && g < 150) return "Rosa";
            if (max == r && min == b && g < 150) return "Marrón";
            String aux="Otro: "+hex;
            return aux;
        } catch (NumberFormatException e) {
            return "N/D";
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
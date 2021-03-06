package org.roux.utils;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.roux.application.ApplicationLibrary;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FileManager {

    private static List<String> FOLDERS = null;
    private static List<String> EXECUTABLES = null;
    private static List<String> BLACKLIST = null;
    private static List<String> BAN_WORD_FOLDERS = null;
    private static List<String> BAN_WORD_EXECUTABLES = null;

    public static final Integer DEFAULT_MAX_ENTRIES = 10;
    public static Integer MAX_ENTRIES;

    private static JSONObject root;

    static {
        parse();
    }

    public static List<Map<String, Object>> getApplications() {
        final Object result = root.get("applications");
        assert result instanceof List;
        assert ((List<?>) result).get(0) instanceof Map;
        return (List<Map<String, Object>>) result;
    }

    private static File loadData() throws IllegalArgumentException, IOException {
        final File file = new File("data.json");
        if(file.exists()) {
            return file;
        }
        System.out.println("Premier lancement !");
        final InputStream inputStream =
                FileManager.class.getClassLoader().getResourceAsStream("preset.json");
        if(inputStream == null) {
            throw new IllegalArgumentException("Preset file not found !");
        }
        FileUtils.copyInputStreamToFile(inputStream, file);
        return file;
    }

    public static void parse() {
        System.out.println("Parse...");
        try(final BufferedReader reader = new BufferedReader(new FileReader(loadData()))) {
            final JSONParser parser = new JSONParser();
            root = (JSONObject) parser.parse(reader);

            final Object maxEntries = root.get("maxEntries");
            MAX_ENTRIES = maxEntries != null ? ((Long) maxEntries).intValue() : DEFAULT_MAX_ENTRIES;
        } catch(final IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static List<Path> getFilesFromFolder(final Predicate<Path> customPredicate,
                                                final Path folder) {
        List<Path> list = new ArrayList<>();
        try {
            list = Files.walk(folder).parallel()
                    .filter(path -> path.toFile().isFile() && path.toFile().canExecute())
                    .filter(path -> ApplicationLibrary.isExtensionAllowed(path.toString()))
                    .filter(path -> !(path.toFile().isDirectory() && folderContainsBanWord(path)))
                    .filter(path -> !(path.toFile().isFile() && executableContainsBanWord(path)))
                    .filter(customPredicate)
                    .collect(Collectors.toList());
        } catch(final IOException exception) {
            exception.printStackTrace();
        }
        return list;
    }

    private static boolean folderContainsBanWord(final Path folder) {
        return getBanWordFolders().parallelStream()
                .anyMatch(s -> folder.toString().contains(s));
    }

    private static boolean executableContainsBanWord(final Path executable) {
        return getBanWordExecutables().parallelStream()
                .anyMatch(s -> executable.getFileName().toString().contains(s));
    }

    /**
     * Files ? Which one ? All of them. Well, all the executable one. Mucho timo & memory consumo so
     * !! WARNING !!
     */
    public static List<Path> getFilesFromFolders(final Predicate<Path> customPredicate) {
        final List<Path> folders = FileManager.getFolders().stream()
                .map(folder -> Paths.get(folder))
                .filter(path -> path.toFile().isDirectory())
                .collect(Collectors.toList());

        final List<Path> files = new ArrayList<>();
        for(final Path folder : folders) {
            files.addAll(getFilesFromFolder(customPredicate, folder));
        }
        return files;
    }

    public static List<Path> getFilesFromFolders() {
        return getFilesFromFolders(path -> true);
    }

    private static long countFilesInFolder(final Path folder) {
        long count = 0;
        try {
            count = Files.walk(folder)
                    //                    .filter(path -> !(path.toFile().isDirectory() &&
                    //                    folderContainsBanWord(path)))
                    //                    .filter(path -> !(path.toFile().isFile() &&
                    //                    executableContainsBanWord(path)))
                    .count();
        } catch(final IOException exception) {
            exception.printStackTrace();
        }
        return count;
    }

    public static long countFilesInFolders() {
        long count = 0;
        final List<Path> folders = FileManager.getFolders().stream()
                .map(folder -> Paths.get(folder))
                .filter(path -> path.toFile().isDirectory())
                .collect(Collectors.toList());
        for(final Path folder : folders) {
            count += countFilesInFolder(folder);
        }
        return count;
    }

    public static void save(final ApplicationLibrary applicationLibrary) {
        System.out.println("Saving...");
        final Map<String, Object> data = new HashMap<>();
        data.put("maxEntries", MAX_ENTRIES);
        data.put("folders", getFolders());
        data.put("executables", getExecutables());
        data.put("blacklist", getBlacklist());
        data.put("banWordFolders", getBanWordFolders());
        data.put("banWordExecutables", getBanWordExecutables());
        data.put("applications", applicationLibrary.getLibraryAsJsonFriendly());
        final JSONObject jsonObject = new JSONObject(data);
        try(final PrintWriter writer = new PrintWriter(new File("data.json"))) {
            writer.print(jsonObject.toJSONString());
            writer.flush();
        } catch(final IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getFolders() {
        if(FileManager.FOLDERS == null)
            FileManager.FOLDERS = getData("folders");
        return FileManager.FOLDERS;
    }

    public static void setFolders(final Collection<String> folders) {
        getFolders().clear();
        getFolders().addAll(folders);
    }

    public static List<String> getExecutables() {
        if(FileManager.EXECUTABLES == null)
            FileManager.EXECUTABLES = getData("executables");
        return FileManager.EXECUTABLES;
    }

    public static void setExecutables(final Collection<String> executables) {
        getExecutables().clear();
        getExecutables().addAll(executables);
    }

    public static List<String> getBlacklist() {
        if(FileManager.BLACKLIST == null)
            FileManager.BLACKLIST = getData("blacklist");
        return FileManager.BLACKLIST;
    }

    public static void setBlacklist(final Collection<String> blacklist) {
        getBlacklist().clear();
        getBlacklist().addAll(blacklist);
    }

    public static List<String> getBanWordFolders() {
        if(FileManager.BAN_WORD_FOLDERS == null)
            FileManager.BAN_WORD_FOLDERS = getData("banWordFolders");
        return FileManager.BAN_WORD_FOLDERS;
    }

    public static void setBanWordFolders(final Collection<String> banWordFolders) {
        getBanWordFolders().clear();
        getBanWordFolders().addAll(banWordFolders);
    }

    public static List<String> getBanWordExecutables() {
        if(FileManager.BAN_WORD_EXECUTABLES == null)
            FileManager.BAN_WORD_EXECUTABLES = getData("banWordExecutables");
        return FileManager.BAN_WORD_EXECUTABLES;
    }

    public static void setBanWordExecutables(final Collection<String> banWordExecutables) {
        getBanWordExecutables().clear();
        getBanWordExecutables().addAll(banWordExecutables);
    }

    /* Utils */

    private static List<String> getData(final String name) {
        final List<String> data = (List<String>) root.get(name);
        return data != null ? data : new ArrayList<>();
    }
}

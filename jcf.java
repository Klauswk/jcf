import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import static java.nio.file.FileVisitResult.CONTINUE;

public class JavaClassFinder {

    private static String className = null;
    private static boolean methods = false;
    private static boolean privateMethods = false;
    private static boolean debug = false;
    private static boolean pathInfo = true;
    private static boolean packageInfo = true;

    public static void main(String[] args) throws IOException {
        String[] cp = System.getProperty("java.class.path").split(File.pathSeparator);

        if (args.length == 0) {
            usage();
        }

        for (String arg : args) {
            if (arg.startsWith("-mp")) privateMethods = true;
            else if (arg.startsWith("-m")) methods = true;
            else if (arg.startsWith("-debug")) debug = true;
            else if (arg.startsWith("-path")) pathInfo = false;
            else if (arg.startsWith("-package")) packageInfo = false;
            else className = arg;
        }

        if (className == null) {
            usage();
        }

        rtClasses(className);

        for (String c : cp) {
            if (!c.contains(".jar")) {
                JavaClassFinder.printDebugln("Path: " + c);
                Path path = Paths.get(c);
                PrintFiles visitor = new PrintFiles(className, path);
                Files.walkFileTree(path, visitor);
            } else {
                JarFile jarFile = new JarFile(c);
                boolean firstFind = true;

                for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
                    JarEntry entry = it.next();

                    if (entry.getRealName().contains(".class") && entry.getRealName().contains(className)) {
                        if (firstFind) {
                            firstFind = false;
                            JavaClassFinder.printPath(c);
                        }
                        JavaClassFinder.printPackage(entry.getRealName());
                        JavaClassFinder.printJarMethods(c, entry.getRealName());
                    }
                }
            }
        }
    }

    private static void usage() {
        System.err.println("A class name is required\n");
        System.err.println("Usage: jcf ClassName ");
        System.err.println("-m : Get the methods of the class");
        System.err.println("-mp : Get the private methods of the class");
        System.err.println("-path : Doesn't print the path of the class");
        System.err.println("-package : Doesn't print the package of the class");
        System.exit(1);
    }

    public static void rtClasses(String shortName) {
        for (Package pack : Package.getPackages()) {
            try {
                String resolvedName = pack.getName() + "." + shortName;
                Class cla = Class.forName(resolvedName);
                URL url = cla.getResource(shortName + ".class");
                JavaClassFinder.printPath(url.toString());
                JavaClassFinder.printPackage(resolvedName);
                JavaClassFinder.printMethods(url.toString());
            } catch (ClassNotFoundException e) {
            }
        }
    }

    public static String fromPathToPackage(String path) {
        if (path.contains(".class")) {
            return path.replace("\\", ".").replaceAll("/", ".").replaceAll(".class", "");
        }
        return path;
    }

    public static class PrintFiles
            extends SimpleFileVisitor<Path> {

        private final Path rootPath;
        private final String className;

        public PrintFiles(String className, Path rootPath) {
            this.rootPath = rootPath;
            this.className = className;
        }

        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attr) {
            if (attr.isRegularFile() && file.getFileName().toString().contains(className)) {
                Path newPath = rootPath.relativize(file);
                JavaClassFinder.printPath(file.toString());
                JavaClassFinder.printPackage(newPath.toString());
                JavaClassFinder.printMethods(file.toString());
            }
            return CONTINUE;
        }
    }

    private static void printMethods(String path) {
        try {
            if (privateMethods) {
                JavaClassFinder.printDebugln("javap -p " + path);
                ProcessBuilder procBuilder = new ProcessBuilder("javap", "-p", path);
                procBuilder.inheritIO();
                Process proc = procBuilder.start();
                proc.waitFor();
            } else if (methods) {
                JavaClassFinder.printDebugln("javap " + path.toString());
                ProcessBuilder procBuilder = new ProcessBuilder("javap", path.toString());
                procBuilder.inheritIO();
                Process proc = procBuilder.start();
                proc.waitFor();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void printJarMethods(String jarPath, String classPath) {
        try {
            if (privateMethods) {
                JavaClassFinder.printDebugln("javap -p -classpath " + jarPath + " " + classPath);
                ProcessBuilder procBuilder = new ProcessBuilder("javap", "-p", "-classpath", jarPath.toString(), classPath.toString().replace(".class", ""));
                procBuilder.inheritIO();
                Process proc = procBuilder.start();
                proc.waitFor();
            } else if (methods) {
                JavaClassFinder.printDebugln("javap -classpath " + jarPath + " " + classPath);
                ProcessBuilder procBuilder = new ProcessBuilder("javap", "-classpath", jarPath.toString(), classPath.toString().replace(".class", ""));
                procBuilder.inheritIO();
                Process proc = procBuilder.start();
                proc.waitFor();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void printPath(String path) {
        if (pathInfo) System.out.println("Path: " + path);
    }

    private static void printPackage(String path) {
        if (pathInfo && packageInfo) System.out.print("    ");
        if (packageInfo) System.out.println("Package: " + fromPathToPackage(path));
    }

    private static void printDebugln(String line) {
        if (debug) {
            System.out.println(line);
        }
    }
}


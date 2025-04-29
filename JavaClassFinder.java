import java.io.*;
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
import java.util.stream.*;
import java.util.Arrays;
import static java.nio.file.FileVisitResult.CONTINUE;

public class JavaClassFinder {

    private static String className = null;
    private static boolean methods = false;
    private static boolean privateMethods = false;
    private static boolean debug = false;
    private static boolean pathInfo = true;
    private static boolean packageInfo = true;
    private static boolean source = false;
    private static boolean rtOnly = false;
    private static String jdkSource = null;

    public static void main(String[] args) throws IOException {
        String[] cp = System.getProperty("java.class.path").split(File.pathSeparator);

        if (args.length == 0) {
            usage();
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-mp")) privateMethods = true;
            else if (arg.startsWith("-m")) methods = true;
            else if (arg.startsWith("-debug")) debug = true;
            else if (arg.startsWith("-path")) pathInfo = false;
            else if (arg.startsWith("-package")) packageInfo = false;
            else if (arg.startsWith("-rt")) rtOnly = true;
            else if (arg.startsWith("-s")) { source = true; }
            else if (arg.startsWith("-js")) { 
              if (i + 1 >= args.length) {
                System.out.println("Missing jdk source location");
                usage();
                System.exit(1);
              }

              i++;
              Path jdkSourceLocation = Paths.get(args[i]);

              File jdkSourceFile = jdkSourceLocation.toFile();

              if (!jdkSourceFile.exists()) {
                System.err.println("File path '" + jdkSourceLocation + "' does not exist");
                usage();
                System.exit(1);
              }

              if (!jdkSourceFile.isDirectory()) {
                System.err.println("File path '" + jdkSourceLocation + "' is not a directory");
                System.exit(1);
              }

              Path javaClassesFolder = jdkSourceLocation.resolve("src/");

              File javaClassFolderFile = javaClassesFolder.toFile();

              if (!javaClassFolderFile.exists()) {
                System.err.println("File path '" + javaClassesFolder + "' does not exist");
                usage();
                System.exit(1);
              }

              if (!javaClassFolderFile.isDirectory()) {
                System.err.println("File path '" + javaClassesFolder + "' is not a directory");
                System.exit(1);
              }

              cp = Arrays.copyOf(cp, cp.length + 1);
              cp[cp.length - 1] = javaClassesFolder.toString();
            }
            else if (arg.startsWith("-h")) usage();
            else className = arg;
        }

        if (className == null) {
            usage();
        }

        rtClasses(className);

        for (String c : cp) {
            String pathToJar = c;
            if (!c.contains(".jar")) {
                JavaClassFinder.printDebugln("Path: " + c);
                Path path = Paths.get(c);
                File realFile = path.toFile();
                if (realFile.exists()) {
                  PrintFiles visitor = new PrintFiles(className, path);
                  Files.walkFileTree(path, visitor);
                }
            } else {
                JarFile jarFile = null; 
                if (source) {
                  String sourcePath = c.replaceAll(".jar", "-sources.jar");
                  File f = new File(sourcePath);
                  if (!f.exists()) {
                    System.err.println("File does not exist: " + sourcePath);
                    continue;
                  }
                  jarFile = new JarFile(sourcePath);
                  pathToJar = sourcePath;
                } else {
                  File f = new File(c);
                  if (!f.exists()) {
                    continue;
                  }
                  jarFile = new JarFile(c);
                }
                boolean firstFind = true;

                for (Iterator<JarEntry> it = jarFile.entries().asIterator(); it.hasNext(); ) {
                    JarEntry entry = it.next();
                    String realName = entry.getRealName();
                    String extension = source ? ".java" : ".class";
                    if (realName.contains(className.replaceAll("\\.", "/"))) {
                      if (realName.contains(extension)) { 
                        if (firstFind) {
                          firstFind = false;
                          JavaClassFinder.printPath(pathToJar);
                        }
                        JavaClassFinder.printPackage(realName);
                        JavaClassFinder.printJarMethods(pathToJar, realName);
                        JavaClassFinder.printJarSourceCode(jarFile, entry);
                      }
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
        System.err.println("-rt : Only search for class in the runtime");
        System.err.println("-s : Search for the source, only prints the first one");
        System.err.println("-js <path-to-jdk-root-source-folder>");
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

        if (rtOnly) System.exit(0); 
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
                JavaClassFinder.printSourceCode(file);
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

    private static void printSourceCode(Path path) {
      if (source) {
        try (InputStream s = new FileInputStream(path.toFile())) {
          System.out.println(new BufferedReader(new InputStreamReader(s)).lines().collect(Collectors.joining("\n"))); 
        } catch (IOException e) {
          e.printStackTrace();
        }
        System.exit(0);
      }
    }

    private static void printJarSourceCode(JarFile jarFile, JarEntry entry) {
      if (source) {
        try (InputStream s = jarFile.getInputStream(entry)) {
          System.out.println(new BufferedReader(new InputStreamReader(s)).lines().collect(Collectors.joining("\n"))); 
        } catch (IOException e) {
          e.printStackTrace();
        }
        System.exit(0);
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


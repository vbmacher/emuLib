package emulib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JarCreator {

    public void createJar(File target, File classFile, List<String> dependencies) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {

            System.out.println("Creating JAR file, class=" + classFile);
            createManifest(zos, dependencies);

            ZipEntry zipEntry = new ZipEntry(
                    classFile.getParentFile().getName() + File.separator + classFile.getName()
            );

            System.out.println("JAR entry: " + zipEntry);
            zos.putNextEntry(zipEntry);
            zos.write(Files.readAllBytes(classFile.toPath()));
            zos.closeEntry();
        }
        System.out.println();
    }

    private void createManifest(ZipOutputStream zos, List<String> dependencies) throws IOException {
        ZipEntry zipEntry = new ZipEntry("META-INF/MANIFEST.MF");
        zos.putNextEntry(zipEntry);

        String manifestContent = "Manifest-Version: 1.0\n"
                + "Implementation-Title: Dummy plug-in\n"
                + "Implementation-Version: 1.0.0\n"
                + "Implementation-Vendor-Id: net.sf.emustudio\n"
                + "Built-By: vbmacher\n"
                + "Build-Jdk: 1.8.0_45\n"
                + "Specification-Title: Dummy Plug-in\n"
                + "Specification-Version: 1.0.0\n"
                + "Archiver-Version: Plexus Archiver\n"
                + "Class-Path: ";

        StringBuilder classPath = new StringBuilder();
        for (String dep : dependencies) {
            classPath.append(dep).append(" ");
        }
        manifestContent += classPath.toString().concat("\n");

        System.out.println("Class-Path: " + classPath.toString());

        zos.write(manifestContent.getBytes());
        zos.closeEntry();
    }

}
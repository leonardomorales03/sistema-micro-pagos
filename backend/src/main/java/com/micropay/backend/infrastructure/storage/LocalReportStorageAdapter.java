package com.micropay.backend.infrastructure.storage;

import com.micropay.backend.domain.ports.out.ReportStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Adaptador de almacenamiento local de reportes (T25 / Requisito 28.1).
 * <p>
 * Escribe en el directorio configurable {@code micropay.reports.storage-dir}
 * (por defecto {@code ./build/reports}). En producción este adaptador puede
 * sustituirse por uno S3-compatible implementando el mismo puerto.
 * <p>
 * El nombre de fichero se sanea (sin separadores de ruta) para evitar path traversal.
 */
@Component
public class LocalReportStorageAdapter implements ReportStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalReportStorageAdapter.class);

    private final Path baseDir;

    public LocalReportStorageAdapter(@Value("${micropay.reports.storage-dir:./build/reports}") String storageDir) {
        this.baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String fileName, byte[] content) {
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(sanitize(fileName));
            Files.write(target, content == null ? new byte[0] : content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo almacenar el reporte '%s': %s"
                    .formatted(fileName, e.getMessage()), e);
        }
    }

    @Override
    public byte[] read(String path) {
        try {
            return Files.readAllBytes(resolveSafely(path));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el reporte '%s': %s"
                    .formatted(path, e.getMessage()), e);
        }
    }

    @Override
    public void delete(String path) {
        try {
            Files.deleteIfExists(resolveSafely(path));
        } catch (Exception e) {
            log.warn("No se pudo eliminar el reporte '{}': {}", path, e.getMessage());
        }
    }

    // ──────────────── helpers ────────────────

    /** Sanea el nombre eliminando cualquier componente de ruta (anti path-traversal). */
    static String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName de reporte es requerido");
        }
        String base = fileName.replace('\\', '/');
        int idx = base.lastIndexOf('/');
        if (idx >= 0) base = base.substring(idx + 1);
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IllegalArgumentException("fileName de reporte inválido: " + fileName);
        }
        return safe;
    }

    /** Resuelve un path garantizando que queda confinado dentro de {@code baseDir}. */
    private Path resolveSafely(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path de reporte es requerido");
        }
        Path p = Paths.get(path);
        Path resolved = p.isAbsolute() ? p.normalize() : baseDir.resolve(p).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("path de reporte fuera del directorio permitido");
        }
        return resolved;
    }
}

package com.micropay.backend.domain.ports.out;

/**
 * Port OUT — Storage de reportes generados (CSV).
 * Implementado por Infrastructure Adapter → LocalReportStorageAdapter
 * (en producción puede sustituirse por S3-compat).
 */
public interface ReportStoragePort {

    /**
     * Persiste el contenido y devuelve la ruta (opaca) bajo la que quedó almacenado.
     *
     * @param fileName nombre lógico del fichero (se sanea; sin path traversal)
     * @param content  bytes del CSV
     * @return path/ruta de almacenamiento
     */
    String store(String fileName, byte[] content);

    /** Lee el contenido almacenado en {@code path}. */
    byte[] read(String path);

    /** Elimina el contenido almacenado en {@code path} (best-effort). */
    void delete(String path);
}

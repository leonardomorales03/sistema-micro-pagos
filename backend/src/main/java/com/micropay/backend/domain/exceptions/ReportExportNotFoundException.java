package com.micropay.backend.domain.exceptions;

import java.util.UUID;

public final class ReportExportNotFoundException extends DomainException {

    public ReportExportNotFoundException(UUID id) {
        super("REPORT_EXPORT_NOT_FOUND", "Exportación de reporte con id '%s' no existe".formatted(id));
    }

    public ReportExportNotFoundException(String token) {
        super("REPORT_EXPORT_NOT_FOUND", "Export token '%s' no corresponde a ninguna exportación".formatted(token));
    }
}

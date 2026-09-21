package com.micropay.backend.domain.valueobjects;

import com.micropay.backend.domain.exceptions.InvalidValueObjectException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * DocumentNumber — Value Object con validación por país.
 * Soporte inicial CO (cédula colombiana, NIT, pasaporte).
 * <p>
 * Nota: la validación dígito-verificación NIT COLOMBIA se implementa
 * en Infrastructure Adapter (no en Value Object para mantener la lógica
 * de DV algoritmo aislada). Aquí solo validación de formato.
 */
public record DocumentNumber(String type, String number, String country) {

    public static final String CO_CC = "CO_CC";
    public static final String CO_NIT = "CO_NIT";
    public static final String CO_CE = "CO_CE";
    public static final String PASSPORT = "PASSPORT";

    private static final Pattern NUMBER = Pattern.compile("^[A-Za-z0-9\\-]{3,32}$");

    public DocumentNumber {
        Objects.requireNonNull(type, "type es requerido");
        Objects.requireNonNull(number, "number es requerido");
        Objects.requireNonNull(country, "country es requerido");
        number = number.strip();
        country = country.toUpperCase();
        type = type.strip().toUpperCase();

        if (!NUMBER.matcher(number).matches()) {
            throw new InvalidValueObjectException("DocumentNumber.number",
                    "formato inválido: " + number);
        }
        if (country.length() != 2) {
            throw new InvalidValueObjectException("DocumentNumber.country",
                    "ISO 3166-1 alpha-2 requerido, recibió: " + country);
        }
        if (type.isBlank()) {
            throw new InvalidValueObjectException("DocumentNumber.type",
                    "no puede estar vacío");
        }
    }
}

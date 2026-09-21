package com.micropay.backend.api.controllers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation para injectar UserId del token JWT en controllers.
 * Resuelto por [CurrentUserArgumentResolver] (Spring MVC config).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}

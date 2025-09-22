package com.anip.kyc.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentValidation {
    String value() default "default";
}

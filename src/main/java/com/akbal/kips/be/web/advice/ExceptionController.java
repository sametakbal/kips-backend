package com.akbal.kips.be.web.advice;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.web.controller.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;


/**
 * This class handles exceptions that occur during request validation.
 * It extends the BaseController class to provide common response handling functionality.
 */
@Slf4j
@RestControllerAdvice
public class ExceptionController extends BaseController {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationErrors(MethodArgumentNotValidException ex) {
        return createFieldErrorResponse(ex.getBindingResult());
    }


    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred: {}", ex.getMessage());
        return error(ex.getMessage());
    }

    private ApiResponse<Void> createFieldErrorResponse(BindingResult bindingResult) {
        String errorMessage = bindingResult.getFieldErrors()
                .stream()
                .map(fieldError -> String.format("%s %s", fieldError.getField(), fieldError.getDefaultMessage()))
                .collect(Collectors.joining(" , "));

        log.debug("Exception occurred while request validation: {}", errorMessage);

        return error(errorMessage);
    }
}

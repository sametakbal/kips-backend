package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import org.springframework.http.HttpStatus;

public class BaseController {

    public <T> ApiResponse<T> respond(T item) {
        return respond(item, HttpStatus.OK);
    }

    public <T> ApiResponse<T> respond(T item, HttpStatus status) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(item)
                .status(status)
                .build();
    }
}

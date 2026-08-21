package com.stock.crypto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AddCryptoRequest {

    @NotBlank(message = "symbol 不能为空")
    private String symbol;

    private String name;

    private String group;
}

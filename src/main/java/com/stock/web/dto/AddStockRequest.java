package com.stock.web.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AddStockRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    /** 可选，留空则自动从行情接口补全 */
    private String name;

    /** 可选分组 */
    private String group;
}

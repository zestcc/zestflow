package com.zestflow.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRequest {

    @NotBlank(message = "SKU不能为空")
    private String sku;

    @Min(value = 0, message = "数量不能为负")
    private int quantity;

    /** 操作类型：CHECK/UPDATE/BATCH_IMPORT */
    @Builder.Default
    private String operation = "CHECK";

    /** 批量导入时的商品列表（JSON） */
    private String itemsJson;
}

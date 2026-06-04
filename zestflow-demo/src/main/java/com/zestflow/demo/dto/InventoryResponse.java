package com.zestflow.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private String sku;
    private int availableQty;
    private boolean sufficient;
    private String status;
    private int importedCount;
    private List<String> processedItems;
    private String errorMessage;
    private long costMs;
}

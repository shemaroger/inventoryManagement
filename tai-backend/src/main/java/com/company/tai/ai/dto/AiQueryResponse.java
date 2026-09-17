package com.company.tai.ai.dto;

import com.company.tai.inventory.dto.ProductDto;
import com.company.tai.inventory.dto.StockItemDto;
import org.springframework.data.domain.Page;

import java.util.List;

public record AiQueryResponse(
        boolean understood,
        String message,
        AiUnderstoodFilter understoodFilter,
        Page<ProductDto> products,
        List<StockItemDto> stockItems
) {}

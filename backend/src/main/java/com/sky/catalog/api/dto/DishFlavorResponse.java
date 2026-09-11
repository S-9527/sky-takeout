package com.sky.catalog.api.dto;

import java.util.List;

import com.sky.catalog.domain.DishFlavor;

/** 对外的口味维度。对应 openapi 的 {@code DishFlavor}。 */
public record DishFlavorResponse(
        Long id,
        String name,
        List<String> options,
        Integer sortOrder
) {

    public static DishFlavorResponse from(DishFlavor flavor) {
        return new DishFlavorResponse(flavor.getId(), flavor.getName(), flavor.getOptions(), flavor.getSortOrder());
    }
}

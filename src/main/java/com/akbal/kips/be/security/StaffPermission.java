package com.akbal.kips.be.security;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum StaffPermission {
    CATALOG_EDIT,
    INVENTORY_ADJUST,
    ORDER_READ_FULFILLMENT,
    ORDER_SHIP,
    ORDER_READ_SUPPORT;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static StaffPermission fromName(String name) {
        return valueOf(name);
    }
}

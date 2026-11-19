package com.akbal.kips.be.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class Permissions {
    public static final String SHOP_OWN = "SHOP_OWN";
    public static final String CATALOG_EDIT = "CATALOG_EDIT";
    public static final String CATALOG_MANAGE = "CATALOG_MANAGE";
    public static final String INVENTORY_ADJUST = "INVENTORY_ADJUST";
    public static final String ORDER_READ_FULFILLMENT = "ORDER_READ_FULFILLMENT";
    public static final String ORDER_SHIP = "ORDER_SHIP";
    public static final String ORDER_READ_SUPPORT = "ORDER_READ_SUPPORT";
    public static final String STAFF_ROLE_MANAGE = "STAFF_ROLE_MANAGE";

    private static final Map<String, List<String>> BUILT_IN_ROLES = Map.of(
            "ROLE_USER", List.of(SHOP_OWN),
            "ROLE_ADMIN", List.of(CATALOG_EDIT, CATALOG_MANAGE, INVENTORY_ADJUST,
                    ORDER_READ_FULFILLMENT, ORDER_SHIP, ORDER_READ_SUPPORT, STAFF_ROLE_MANAGE)
    );

    private Permissions() {
    }

    public static Stream<SimpleGrantedAuthority> authorities(String role, Set<StaffPermission> staffPermissions) {
        var builtIn = BUILT_IN_ROLES.get(role);
        var grants = builtIn == null ? staffPermissions.stream().map(StaffPermission::name) : builtIn.stream();
        return Stream.concat(Stream.of(role), grants)
                .map(SimpleGrantedAuthority::new);
    }
}

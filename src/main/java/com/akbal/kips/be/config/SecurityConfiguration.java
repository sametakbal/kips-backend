package com.akbal.kips.be.config;

import com.akbal.kips.be.security.Permissions;
import com.akbal.kips.be.web.filter.JwtAuthenticationFilter;
import com.akbal.kips.be.service.auth.JwtService;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/products").hasAuthority(Permissions.CATALOG_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/product-categories").hasAuthority(Permissions.CATALOG_EDIT)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/*").hasAuthority(Permissions.CATALOG_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*").hasAuthority(Permissions.CATALOG_MANAGE)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/staff/catalog/products/*/content")
                            .hasAuthority(Permissions.CATALOG_EDIT)
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/inventory/products/*/adjust")
                            .hasAuthority(Permissions.INVENTORY_ADJUST)
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/fulfillment/orders", "/api/v1/staff/fulfillment/orders/*")
                            .hasAuthority(Permissions.ORDER_READ_FULFILLMENT)
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff/fulfillment/orders/*/ship")
                            .hasAuthority(Permissions.ORDER_SHIP)
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/support/orders/*")
                            .hasAuthority(Permissions.ORDER_READ_SUPPORT)
                        .requestMatchers("/api/v1/staff/**").denyAll()
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/**", "/api/v1/orders", "/api/v1/orders/**",
                                "/api/v1/addresses", "/api/v1/addresses/**",
                                "/api/v1/wishlist", "/api/v1/wishlist/**")
                            .hasAuthority(Permissions.SHOP_OWN)
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/user/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

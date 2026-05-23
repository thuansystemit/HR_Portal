package com.demo.app.platform.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MdcFilterConfig {

    @Bean
    public FilterRegistrationBean<MdcRequestFilter> mdcRequestFilterRegistration(MdcRequestFilter filter) {
        var reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setName("mdcRequestFilter");
        return reg;
    }

    // Prevent double-registration of MdcUserFilter as a standalone servlet filter.
    // It is registered inside the Spring Security chain via SecurityConfig.
    @Bean
    public FilterRegistrationBean<MdcUserFilter> mdcUserFilterRegistration(MdcUserFilter filter) {
        var reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}

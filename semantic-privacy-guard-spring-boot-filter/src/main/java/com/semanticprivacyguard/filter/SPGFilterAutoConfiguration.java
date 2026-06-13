package com.semanticprivacyguard.filter;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.config.SPGConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Spring Boot auto-configuration for the SPG servlet filter.
 *
 * <p>Activated when:</p>
 * <ol>
 *   <li>Running inside a Servlet-based web application (not reactive), AND</li>
 *   <li>{@code semantic-privacy-guard} is on the classpath, AND</li>
 *   <li>{@code spg.filter.enabled=true} (default).</li>
 * </ol>
 *
 * <p>Registered beans:</p>
 * <ul>
 *   <li>{@link SemanticPrivacyGuard} — the core guard instance (backs off if
 *       one already exists in the context).</li>
 *   <li>{@link SPGRequestFilter} — registered as a {@link FilterRegistrationBean}
 *       at {@code HIGHEST_PRECEDENCE + 10} so it runs before business logic
 *       but after security filters.</li>
 * </ul>
 *
 * <h2>Disabling</h2>
 * <pre>
 * # application.properties
 * spg.filter.enabled=false
 * </pre>
 *
 * <h2>Custom path patterns</h2>
 * <pre>
 * spg.filter.included-paths=/api/**
 * spg.filter.excluded-paths=/api/public/**,/actuator/**
 * </pre>
 *
 * @author Hemant Naik
 * @since 1.5.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(SemanticPrivacyGuard.class)
@ConditionalOnProperty(prefix = "spg.filter", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SPGFilterProperties.class)
public class SPGFilterAutoConfiguration {

    /**
     * Creates the core {@link SemanticPrivacyGuard} bean from filter properties.
     * Back off if the application declares its own {@code SemanticPrivacyGuard}
     * bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public SemanticPrivacyGuard semanticPrivacyGuard(SPGFilterProperties props) {
        SPGConfig config = SPGConfig.builder()
                .redactionMode(props.getRedactionMode())
                .mlConfidenceThreshold(props.getMlConfidenceThreshold())
                .minimumSeverity(props.getMinimumSeverity())
                .build();
        return SemanticPrivacyGuard.create(config);
    }

    /**
     * Registers {@link SPGRequestFilter} as a Spring Boot {@link FilterRegistrationBean}.
     *
     * <p>Runs at {@code HIGHEST_PRECEDENCE + 10} — after security filters but
     * before any business-logic filters. Override the order via
     * {@link FilterRegistrationBean#setOrder(int)} if you need different ordering.</p>
     */
    @Bean
    @ConditionalOnMissingBean(SPGRequestFilter.class)
    public FilterRegistrationBean<SPGRequestFilter> spgRequestFilter(
            SemanticPrivacyGuard spg, SPGFilterProperties props) {

        FilterRegistrationBean<SPGRequestFilter> reg =
                new FilterRegistrationBean<>(new SPGRequestFilter(spg, props));

        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.setName("spgRequestFilter");
        return reg;
    }
}

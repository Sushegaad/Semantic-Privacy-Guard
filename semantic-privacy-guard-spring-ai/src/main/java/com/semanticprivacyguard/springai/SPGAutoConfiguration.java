package com.semanticprivacyguard.springai;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.config.SPGConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Semantic Privacy Guard.
 *
 * <p>Activated when:</p>
 * <ol>
 *   <li>{@code semantic-privacy-guard} and {@code spring-ai-core} are on the
 *       classpath, AND</li>
 *   <li>{@code spg.enabled} is {@code true} (default).</li>
 * </ol>
 *
 * <p>Registers two beans:</p>
 * <ul>
 *   <li>{@link SemanticPrivacyGuard} — the core guard instance, built from
 *       {@link SPGProperties}. Back off if the application already declares
 *       its own bean.</li>
 *   <li>{@link SPGAdvisor} — the Spring AI advisor that redacts PII from
 *       prompts. Also backs off if the application provides its own.</li>
 * </ul>
 *
 * <h2>Disabling</h2>
 * <pre>
 * # application.properties
 * spg.enabled=false
 * </pre>
 *
 * @author Hemant Naik
 * @since 1.4.0
 */
@AutoConfiguration
@ConditionalOnClass({ SemanticPrivacyGuard.class,
                      org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor.class })
@ConditionalOnProperty(prefix = "spg", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SPGProperties.class)
public class SPGAutoConfiguration {

    /**
     * Creates a {@link SemanticPrivacyGuard} bean from the bound
     * {@link SPGProperties}.
     *
     * <p>Back off if the application registers its own {@code SemanticPrivacyGuard}
     * bean (e.g. to use custom patterns or NLP models).</p>
     *
     * @param props the auto-bound configuration properties
     * @return a fully initialised {@link SemanticPrivacyGuard}
     */
    @Bean
    @ConditionalOnMissingBean
    public SemanticPrivacyGuard semanticPrivacyGuard(SPGProperties props) {
        SPGConfig config = SPGConfig.builder()
                .redactionMode(props.getRedactionMode())
                .mlConfidenceThreshold(props.getMlConfidenceThreshold())
                .minimumSeverity(props.getMinimumSeverity())
                .build();
        return SemanticPrivacyGuard.create(config);
    }

    /**
     * Creates an {@link SPGAdvisor} bean wired with the auto-configured
     * (or user-provided) {@link SemanticPrivacyGuard}.
     *
     * <p>Back off if the application registers its own {@link SPGAdvisor}.</p>
     *
     * @param spg   the guard instance
     * @param props the auto-bound configuration properties
     * @return a fully initialised {@link SPGAdvisor}
     */
    @Bean
    @ConditionalOnMissingBean
    public SPGAdvisor spgAdvisor(SemanticPrivacyGuard spg, SPGProperties props) {
        return new SPGAdvisor(spg, props.isRedactSystemPrompt(), props.getAdvisorOrder());
    }
}

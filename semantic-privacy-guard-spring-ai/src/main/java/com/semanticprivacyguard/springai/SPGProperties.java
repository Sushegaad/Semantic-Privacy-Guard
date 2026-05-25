package com.semanticprivacyguard.springai;

import com.semanticprivacyguard.tokenizer.PIITokenizer.RedactionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot configuration properties for the Semantic Privacy Guard
 * Spring AI auto-configuration.
 *
 * <p>All properties are prefixed with {@code spg}:</p>
 * <pre>
 * spg.enabled=true
 * spg.redaction-mode=TOKEN
 * spg.ml-confidence-threshold=0.70
 * spg.minimum-severity=1
 * spg.redact-system-prompt=false
 * spg.advisor-order=-100
 * </pre>
 *
 * @author Hemant Naik
 * @since 1.4.0
 */
@ConfigurationProperties(prefix = "spg")
public class SPGProperties {

    /** Whether the SPG advisor bean is registered. Default: {@code true}. */
    private boolean enabled = true;

    /**
     * Redaction mode: {@code TOKEN} (default), {@code MASK}, or {@code BLANK}.
     * TOKEN mode replaces PII with structured tokens like {@code [EMAIL_1]}.
     */
    private RedactionMode redactionMode = RedactionMode.TOKEN;

    /**
     * Naive Bayes posterior probability threshold for the ML detection layer.
     * Values in {@code (0.0, 1.0]}; lower = higher recall, higher = higher precision.
     * Default: {@code 0.65}.
     */
    private double mlConfidenceThreshold = 0.65;

    /**
     * Minimum severity score (1–10) a PII match must have to be included.
     * Use {@code 6} to filter out IP addresses and organisations and focus on
     * email, phone, SSN, credit card, etc. Default: {@code 1} (all types).
     */
    private int minimumSeverity = 1;

    /**
     * Whether the system prompt is also scanned and redacted.
     * Usually {@code false} because system prompts are developer-authored and
     * rarely contain real PII. Default: {@code false}.
     */
    private boolean redactSystemPrompt = false;

    /**
     * Spring advisor chain order for the {@link SPGAdvisor} bean.
     * Lower values execute earlier. Default: {@link SPGAdvisor#DEFAULT_ORDER}.
     */
    private int advisorOrder = SPGAdvisor.DEFAULT_ORDER;

    // ── Getters / setters ─────────────────────────────────────────────────────

    public boolean isEnabled()                       { return enabled;                  }
    public void    setEnabled(boolean enabled)       { this.enabled = enabled;          }

    public RedactionMode getRedactionMode()          { return redactionMode;            }
    public void setRedactionMode(RedactionMode mode) { this.redactionMode = mode;       }

    public double getMlConfidenceThreshold()         { return mlConfidenceThreshold;    }
    public void   setMlConfidenceThreshold(double t) { this.mlConfidenceThreshold = t;  }

    public int  getMinimumSeverity()                 { return minimumSeverity;          }
    public void setMinimumSeverity(int s)            { this.minimumSeverity = s;        }

    public boolean isRedactSystemPrompt()                       { return redactSystemPrompt;              }
    public void    setRedactSystemPrompt(boolean redact)        { this.redactSystemPrompt = redact;       }

    public int  getAdvisorOrder()                    { return advisorOrder;             }
    public void setAdvisorOrder(int order)           { this.advisorOrder = order;       }
}

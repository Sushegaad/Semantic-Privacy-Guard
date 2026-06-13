package com.semanticprivacyguard.filter;

import com.semanticprivacyguard.tokenizer.PIITokenizer.RedactionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Spring Boot configuration properties for the SPG servlet filter.
 *
 * <pre>
 * # application.properties
 * spg.filter.enabled=true
 * spg.filter.redact-request-body=true
 * spg.filter.redact-response-body=true
 * spg.filter.redact-query-params=false
 * spg.filter.included-paths=/**
 * spg.filter.excluded-paths=/actuator/**,/health,/metrics
 * spg.filter.redaction-mode=TOKEN
 * spg.filter.minimum-severity=1
 * spg.filter.ml-confidence-threshold=0.65
 * </pre>
 *
 * @author Hemant Naik
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = "spg.filter")
public class SPGFilterProperties {

    /** Enable or disable the filter entirely. Default: {@code true}. */
    private boolean enabled = true;

    /**
     * Whether to redact PII from the request body (JSON/text content types).
     * Default: {@code true}.
     */
    private boolean redactRequestBody = true;

    /**
     * Whether to redact PII from the response body.
     * Default: {@code true}.
     */
    private boolean redactResponseBody = true;

    /**
     * Whether to redact PII found in URL query parameter values.
     * Default: {@code false} — query params are usually short and structured;
     * enable only if your API accepts freeform text in query strings.
     */
    private boolean redactQueryParams = false;

    /**
     * Ant-style path patterns that should be processed by the filter.
     * Default: {@code ["/**"]} (all paths).
     */
    private List<String> includedPaths = List.of("/**");

    /**
     * Ant-style path patterns that should be <em>skipped</em> by the filter.
     * Takes precedence over {@link #includedPaths}.
     * Default: Spring Boot actuator and common health check paths.
     */
    private List<String> excludedPaths = List.of("/actuator/**", "/health", "/metrics", "/favicon.ico");

    /**
     * Redaction mode applied to detected PII.
     * {@code TOKEN} (default) replaces with {@code [EMAIL_1]}-style tokens.
     * {@code MASK} replaces with {@code ****}.
     * {@code BLANK} removes the value entirely.
     */
    private RedactionMode redactionMode = RedactionMode.TOKEN;

    /**
     * Minimum PII severity (1–10) to include. Default: {@code 1} (all types).
     * Set to {@code 6} to skip low-severity types like IP addresses and
     * organization names, focusing on email, phone, SSN, and credit cards.
     */
    private int minimumSeverity = 1;

    /**
     * Naive Bayes confidence threshold for the ML detection layer.
     * Range {@code (0.0, 1.0]}. Lower = higher recall, higher = higher precision.
     * Default: {@code 0.65}.
     */
    private double mlConfidenceThreshold = 0.65;

    // ── Getters / setters ─────────────────────────────────────────────────────

    public boolean isEnabled()                            { return enabled;                    }
    public void    setEnabled(boolean enabled)            { this.enabled = enabled;            }

    public boolean isRedactRequestBody()                  { return redactRequestBody;          }
    public void    setRedactRequestBody(boolean v)        { this.redactRequestBody = v;        }

    public boolean isRedactResponseBody()                 { return redactResponseBody;         }
    public void    setRedactResponseBody(boolean v)       { this.redactResponseBody = v;       }

    public boolean isRedactQueryParams()                  { return redactQueryParams;          }
    public void    setRedactQueryParams(boolean v)        { this.redactQueryParams = v;        }

    public List<String> getIncludedPaths()                { return includedPaths;              }
    public void         setIncludedPaths(List<String> p)  { this.includedPaths = p;            }

    public List<String> getExcludedPaths()                { return excludedPaths;              }
    public void         setExcludedPaths(List<String> p)  { this.excludedPaths = p;            }

    public RedactionMode getRedactionMode()               { return redactionMode;              }
    public void          setRedactionMode(RedactionMode m){ this.redactionMode = m;            }

    public int  getMinimumSeverity()                      { return minimumSeverity;            }
    public void setMinimumSeverity(int s)                 { this.minimumSeverity = s;          }

    public double getMlConfidenceThreshold()              { return mlConfidenceThreshold;      }
    public void   setMlConfidenceThreshold(double t)      { this.mlConfidenceThreshold = t;    }
}

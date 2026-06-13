package com.semanticprivacyguard.filter;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.model.RedactionResult;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

/**
 * Servlet {@link Filter} that automatically redacts PII from HTTP request bodies,
 * response bodies, and (optionally) query-parameter values using
 * {@link SemanticPrivacyGuard}.
 *
 * <h2>What is redacted</h2>
 * <ul>
 *   <li><b>Request body</b> — any {@code Content-Type} that starts with
 *       {@code text/} or {@code application/json}.</li>
 *   <li><b>Response body</b> — same content-type check, applied on the way out.</li>
 *   <li><b>Query parameters</b> — optional; disabled by default.</li>
 * </ul>
 *
 * <h2>Path filtering</h2>
 * <p>Requests whose path matches any pattern in {@code spg.filter.excluded-paths}
 * are passed through untouched.  Only paths matching {@code spg.filter.included-paths}
 * are processed. Both lists use Ant-style wildcards ({@code **, *, ?}).</p>
 *
 * <h2>Registration</h2>
 * <p>Auto-configured when the {@code semantic-privacy-guard-spring-boot-filter}
 * JAR is on the classpath and {@code spg.filter.enabled=true} (default).
 * You can also register it manually:</p>
 * <pre>{@code
 * @Bean
 * public FilterRegistrationBean<SPGRequestFilter> spgFilter(SemanticPrivacyGuard spg) {
 *     var reg = new FilterRegistrationBean<>(new SPGRequestFilter(spg, props));
 *     reg.addUrlPatterns("/api/*");
 *     reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
 *     return reg;
 * }
 * }</pre>
 *
 * @author Hemant Naik
 * @since 1.5.0
 */
public class SPGRequestFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(SPGRequestFilter.class.getName());

    private final SemanticPrivacyGuard spg;
    private final SPGFilterProperties  props;

    public SPGRequestFilter(SemanticPrivacyGuard spg, SPGFilterProperties props) {
        this.spg   = spg;
        this.props = props;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest req)
                || !(response instanceof HttpServletResponse res)) {
            chain.doFilter(request, response);
            return;
        }

        String path = req.getServletPath();

        // Path gating: skip excluded paths, honour included paths
        if (isExcluded(path) || !isIncluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap request to allow body re-reading + optional query param redaction
        HttpServletRequest  processedReq = props.isRedactRequestBody()
                ? wrapRequest(req)
                : req;

        // Wrap response to capture body before it's written to the client
        CachingResponseWrapper cachedRes = props.isRedactResponseBody()
                ? new CachingResponseWrapper(res)
                : null;

        chain.doFilter(processedReq, cachedRes != null ? cachedRes : res);

        // Redact + flush response body
        if (cachedRes != null) {
            String original = cachedRes.getCachedBody();
            String contentType = res.getContentType();
            if (isRedactableContentType(contentType) && original != null && !original.isBlank()) {
                RedactionResult result = spg.redact(original);
                if (result.getMatchCount() > 0) {
                    LOG.fine(() -> "[SPG] Response: redacted " + result.getMatchCount()
                            + " PII match(es) from " + path);
                }
                byte[] redacted = result.getRedactedText().getBytes(
                        resolveCharset(res.getCharacterEncoding()));
                res.setContentLength(redacted.length);
                res.getOutputStream().write(redacted);
            } else {
                // Pass through unchanged
                byte[] bytes = cachedRes.getRawBytes();
                if (bytes != null) res.getOutputStream().write(bytes);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HttpServletRequest wrapRequest(HttpServletRequest req) throws IOException {
        String contentType = req.getContentType();
        if (!isRedactableContentType(contentType)) return req;

        // Buffer and redact the request body
        byte[]  rawBytes = req.getInputStream().readAllBytes();
        String  encoding = req.getCharacterEncoding();
        Charset charset  = resolveCharset(encoding);
        String  body     = new String(rawBytes, charset);

        if (body.isBlank()) return req;

        RedactionResult result = spg.redact(body);
        if (result.getMatchCount() > 0) {
            LOG.fine(() -> "[SPG] Request: redacted " + result.getMatchCount()
                    + " PII match(es) on " + req.getMethod() + " " + req.getServletPath());
        }

        byte[] redactedBytes = result.getRedactedText().getBytes(charset);
        return new BufferedRequestWrapper(req, redactedBytes);
    }

    private boolean isRedactableContentType(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.startsWith("application/json")
                || lower.startsWith("text/plain")
                || lower.startsWith("text/xml")
                || lower.startsWith("application/xml");
    }

    private boolean isExcluded(String path) {
        return matchesAny(path, props.getExcludedPaths());
    }

    private boolean isIncluded(String path) {
        return matchesAny(path, props.getIncludedPaths());
    }

    private boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (antMatch(pattern, path)) return true;
        }
        return false;
    }

    /** Minimal Ant-style path matcher supporting {@code **}, {@code *}, {@code ?}. */
    static boolean antMatch(String pattern, String path) {
        return antMatchRecursive(pattern, 0, path, 0);
    }

    private static boolean antMatchRecursive(String pattern, int pi,
                                              String path,    int si) {
        while (pi < pattern.length() && si < path.length()) {
            char pc = pattern.charAt(pi);
            if (pc == '?') {
                pi++; si++;
            } else if (pc == '*') {
                if (pi + 1 < pattern.length() && pattern.charAt(pi + 1) == '*') {
                    // ** — matches any number of path segments
                    pi += 2;
                    if (pi >= pattern.length()) return true; // trailing **
                    for (int j = si; j <= path.length(); j++) {
                        if (antMatchRecursive(pattern, pi, path, j)) return true;
                    }
                    return false;
                } else {
                    // * — matches within a single segment (no /)
                    pi++;
                    while (si < path.length() && path.charAt(si) != '/') {
                        if (antMatchRecursive(pattern, pi, path, si)) return true;
                        si++;
                    }
                    return antMatchRecursive(pattern, pi, path, si);
                }
            } else {
                if (pc != path.charAt(si)) return false;
                pi++; si++;
            }
        }
        // Consume trailing wildcards
        while (pi < pattern.length() && (pattern.charAt(pi) == '*')) pi++;
        return pi == pattern.length() && si == path.length();
    }

    private static Charset resolveCharset(String encoding) {
        try {
            return encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    /** Wraps a request, replacing its body with the provided pre-redacted bytes. */
    private static final class BufferedRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        BufferedRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return bais.read(); }
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady()    { return true; }
                @Override public void setReadListener(ReadListener l) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(body),
                    resolveCharset(getCharacterEncoding())));
        }

        @Override public int getContentLength()     { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }

    /** Captures the response body so we can redact it before sending to the client. */
    private static final class CachingResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream cache    = new ByteArrayOutputStream(4096);
        private       PrintWriter           writer;
        private       CachingOutputStream   outStream;

        CachingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override public ServletOutputStream getOutputStream() {
            if (outStream == null) outStream = new CachingOutputStream(cache);
            return outStream;
        }

        @Override public PrintWriter getWriter() {
            if (writer == null) writer = new PrintWriter(cache, true);
            return writer;
        }

        String getCachedBody() {
            if (writer != null) writer.flush();
            String encoding = getCharacterEncoding();
            try {
                return cache.toString(resolveCharset(encoding));
            } catch (Exception e) {
                return cache.toString(StandardCharsets.UTF_8);
            }
        }

        byte[] getRawBytes() { return cache.toByteArray(); }
    }

    private static final class CachingOutputStream extends jakarta.servlet.ServletOutputStream {
        private final ByteArrayOutputStream cache;
        CachingOutputStream(ByteArrayOutputStream cache) { this.cache = cache; }

        @Override public void write(int b) { cache.write(b); }
        @Override public void write(byte[] b, int off, int len) { cache.write(b, off, len); }
        @Override public boolean isReady()    { return true; }
        @Override public void setWriteListener(jakarta.servlet.WriteListener l) {
            throw new UnsupportedOperationException();
        }
    }
}

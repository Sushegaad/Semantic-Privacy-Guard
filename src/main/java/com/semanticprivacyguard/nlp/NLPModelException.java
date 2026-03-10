package com.semanticprivacyguard.nlp;

/**
 * Thrown when Apache OpenNLP model files cannot be found or loaded.
 *
 * <p>This exception is raised at {@code SemanticPrivacyGuard} construction time
 * when {@code nlpEnabled(true)} is set in the configuration but one or more
 * required model files are missing or unreadable.</p>
 *
 * <h2>Common causes and fixes</h2>
 * <ul>
 *   <li><b>Classpath models not present</b> — download {@code en-ner-person.bin}
 *       from the Apache OpenNLP model repository and place it at
 *       {@code src/main/resources/models/en-ner-person.bin}.</li>
 *   <li><b>Filesystem directory not found</b> — verify the path passed to
 *       {@code SPGConfig.builder().nlpModelsDirectory(path)} exists and is
 *       readable.</li>
 *   <li><b>Corrupt model file</b> — delete and re-download the model.</li>
 * </ul>
 *
 * <p>See the README section <em>NLP Setup</em> for full download and
 * configuration instructions.</p>
 *
 * @author Hemant Naik
 * @since 1.1.0
 */
public class NLPModelException extends Exception {

    /**
     * Constructs a new {@code NLPModelException} with the given detail message.
     *
     * @param message human-readable description of the failure
     */
    public NLPModelException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code NLPModelException} with a detail message and cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying I/O or classpath error
     */
    public NLPModelException(String message, Throwable cause) {
        super(message, cause);
    }
}

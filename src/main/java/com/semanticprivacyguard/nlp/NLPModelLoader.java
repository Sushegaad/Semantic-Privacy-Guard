package com.semanticprivacyguard.nlp;

import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads Apache OpenNLP model files for use by {@link com.semanticprivacyguard.detector.NLPDetector}.
 *
 * <h2>Model files</h2>
 *
 * <table>
 *   <caption>OpenNLP model files used by Semantic Privacy Guard</caption>
 *   <tr><th>File</th><th>Role</th><th>Required?</th></tr>
 *   <tr><td>{@code en-ner-person.bin}</td>
 *       <td>Person name detection</td><td>Yes</td></tr>
 *   <tr><td>{@code en-ner-organization.bin}</td>
 *       <td>Organisation name detection</td><td>No</td></tr>
 *   <tr><td>{@code en-token.bin}</td>
 *       <td>MaxEnt tokeniser (trained with same pipeline as NER models)</td>
 *       <td>No — falls back to whitespace tokenisation</td></tr>
 * </table>
 *
 * <h2>Obtaining the models</h2>
 *
 * <p>Download English models from the Apache OpenNLP model repository:</p>
 * <pre>
 * # Apache OpenNLP 1.5 models (recommended for opennlp-tools 2.x)
 * https://opennlp.sourceforge.net/models-1.5/
 *
 * Required file:
 *   en-ner-person.bin
 *
 * Recommended additional files:
 *   en-ner-organization.bin
 *   en-token.bin
 * </pre>
 *
 * <h2>Classpath placement</h2>
 *
 * <p>Place the downloaded model files in your project at:</p>
 * <pre>
 * src/main/resources/
 *   models/
 *     en-ner-person.bin        ← required
 *     en-ner-organization.bin  ← recommended
 *     en-token.bin             ← recommended
 * </pre>
 *
 * <h2>Filesystem placement</h2>
 *
 * <p>Alternatively, place model files in any directory and pass its path to
 * {@code SPGConfig.builder().nlpModelsDirectory(Path.of("/opt/nlp-models"))}.</p>
 *
 * @author Hemant Naik
 * @since 1.1.0
 * @see com.semanticprivacyguard.detector.NLPDetector
 */
public final class NLPModelLoader {

    private static final String PERSON_MODEL  = "en-ner-person.bin";
    private static final String ORG_MODEL     = "en-ner-organization.bin";
    private static final String TOKEN_MODEL   = "en-token.bin";
    private static final String CLASSPATH_DIR = "models/";

    private NLPModelLoader() {}

    // ── Public factory methods ────────────────────────────────────────────────

    /**
     * Loads models from the classpath under the {@code models/} resource prefix.
     *
     * <p>Looks for {@code /models/en-ner-person.bin} (required),
     * {@code /models/en-ner-organization.bin} (optional), and
     * {@code /models/en-token.bin} (optional) on the classpath.</p>
     *
     * @return a {@link LoadedModels} record ready for use in {@link com.semanticprivacyguard.detector.NLPDetector}
     * @throws NLPModelException if the required person model cannot be found or
     *                           loaded from the classpath
     */
    public static LoadedModels fromClasspath() throws NLPModelException {
        TokenNameFinderModel personModel =
            loadNameFinderFromClasspath(CLASSPATH_DIR + PERSON_MODEL, true);

        TokenNameFinderModel orgModel =
            loadNameFinderFromClasspath(CLASSPATH_DIR + ORG_MODEL, false);

        TokenizerModel tokenizerModel =
            loadTokenizerFromClasspath(CLASSPATH_DIR + TOKEN_MODEL);

        return new LoadedModels(personModel, orgModel, tokenizerModel);
    }

    /**
     * Loads models from the specified filesystem directory.
     *
     * <p>{@code en-ner-person.bin} is required; {@code en-ner-organization.bin}
     * and {@code en-token.bin} are optional and silently skipped if absent.</p>
     *
     * @param directory directory containing the {@code .bin} model files
     * @return a {@link LoadedModels} record ready for use in {@link com.semanticprivacyguard.detector.NLPDetector}
     * @throws NLPModelException if the required person model cannot be found or
     *                           loaded from {@code directory}
     */
    public static LoadedModels fromDirectory(Path directory) throws NLPModelException {
        if (!Files.isDirectory(directory)) {
            throw new NLPModelException(
                "NLP models directory does not exist or is not a directory: " + directory);
        }

        TokenNameFinderModel personModel =
            loadNameFinderFromFile(directory.resolve(PERSON_MODEL), true);

        TokenNameFinderModel orgModel =
            loadNameFinderFromFile(directory.resolve(ORG_MODEL), false);

        TokenizerModel tokenizerModel =
            loadTokenizerFromFile(directory.resolve(TOKEN_MODEL));

        return new LoadedModels(personModel, orgModel, tokenizerModel);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static TokenNameFinderModel loadNameFinderFromClasspath(
            String resource, boolean required) throws NLPModelException {
        InputStream is = NLPModelLoader.class.getClassLoader()
                             .getResourceAsStream(resource);
        if (is == null) {
            if (required) {
                throw new NLPModelException(
                    "Required OpenNLP model not found on classpath: " + resource
                  + "\nDownload it from https://opennlp.sourceforge.net/models-1.5/ "
                  + "and place it at src/main/resources/" + resource);
            }
            return null;
        }
        try (InputStream stream = is) {
            return new TokenNameFinderModel(stream);
        } catch (IOException e) {
            throw new NLPModelException(
                "Failed to load OpenNLP model from classpath: " + resource, e);
        }
    }

    private static TokenizerModel loadTokenizerFromClasspath(
            String resource) throws NLPModelException {
        InputStream is = NLPModelLoader.class.getClassLoader()
                             .getResourceAsStream(resource);
        if (is == null) return null;   // optional — caller falls back to whitespace
        try (InputStream stream = is) {
            return new TokenizerModel(stream);
        } catch (IOException e) {
            throw new NLPModelException(
                "Failed to load OpenNLP tokenizer model from classpath: " + resource, e);
        }
    }

    private static TokenNameFinderModel loadNameFinderFromFile(
            Path file, boolean required) throws NLPModelException {
        if (!Files.exists(file)) {
            if (required) {
                throw new NLPModelException(
                    "Required OpenNLP model file not found: " + file
                  + "\nDownload it from https://opennlp.sourceforge.net/models-1.5/");
            }
            return null;
        }
        try (InputStream is = Files.newInputStream(file)) {
            return new TokenNameFinderModel(is);
        } catch (IOException e) {
            throw new NLPModelException(
                "Failed to load OpenNLP model file: " + file, e);
        }
    }

    private static TokenizerModel loadTokenizerFromFile(Path file) throws NLPModelException {
        if (!Files.exists(file)) return null;  // optional
        try (InputStream is = Files.newInputStream(file)) {
            return new TokenizerModel(is);
        } catch (IOException e) {
            throw new NLPModelException(
                "Failed to load OpenNLP tokenizer model file: " + file, e);
        }
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Holds the OpenNLP model objects loaded from disk or classpath.
     *
     * <p>{@code personModel} is guaranteed non-null (it is required).
     * {@code organizationModel} and {@code tokenizerModel} may be {@code null}
     * if the corresponding model files were absent; callers degrade gracefully
     * when these are {@code null}.</p>
     *
     * @param personModel       MaxEnt NER model for person names (never {@code null})
     * @param organizationModel MaxEnt NER model for organisation names (may be {@code null})
     * @param tokenizerModel    MaxEnt tokenizer model (may be {@code null};
     *                          {@link opennlp.tools.tokenize.WhitespaceTokenizer} is used
     *                          as fallback)
     */
    public record LoadedModels(
        TokenNameFinderModel personModel,
        TokenNameFinderModel organizationModel,
        TokenizerModel       tokenizerModel
    ) {}
}

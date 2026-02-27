package dev.ebullient.ironsworn;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import Datasworn.AtlasEntry;
import Datasworn.MoveCategory;
import Datasworn.OracleTablesCollection;
import Datasworn.Rules;
import Datasworn.RulesPackageRuleset;
import io.quarkus.runtime.Startup;

@Startup
@Singleton
public class DataswornService {
    private static final Logger log = Logger.getLogger(DataswornService.class);

    // SnakeYAML rejects dots in anchor/alias names (e.g. &i18n.common_noun)
    private static final Pattern ANCHOR_DOT = Pattern.compile("([&*])i18n\\.");

    private final ObjectMapper jsonMapper;
    private final Yaml yaml;
    private final Map<String, AtlasEntry> locations = new LinkedHashMap<>();
    private final Map<String, MoveCategory> moves = new LinkedHashMap<>();
    private final Map<String, OracleTablesCollection> oracles = new LinkedHashMap<>();
    private Rules rules;

    public DataswornService() {
        jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        var loaderOptions = new LoaderOptions();
        loaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
        yaml = new Yaml(loaderOptions);

        loadRules();
        loadAtlas();
        loadMoves();
        loadOracles();

        log.infof("Loaded %d move categories and %d oracle collections", moves.size(), oracles.size());
    }

    private void loadRules() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("rules/rules.yaml")) {
            if (is == null) {
                log.warn("rules/rules.yaml not found on classpath");
                return;
            }
            RulesPackageRuleset ruleset = loadYaml(is, RulesPackageRuleset.class);
            rules = ruleset.getRules();
        } catch (Exception e) {
            log.errorf(e, "Failed to load rules.yaml: %s", e.getMessage());
        }
    }

    private void loadAtlas() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("rules/atlas.yaml")) {
            if (is == null) {
                log.warn("rules/atlas.yaml not found on classpath");
                return;
            }
            RulesPackageRuleset ruleset = loadYaml(is, RulesPackageRuleset.class);
            if (ruleset.getAtlas() != null) {
                var collection = ruleset.getAtlas().get("ironlands");
                if (collection != null) {
                    locations.putAll(collection.getContents());
                }
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to load moves.yaml: %s", e.getMessage());
        }
    }

    private void loadMoves() {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("rules/moves.yaml")) {
            if (is == null) {
                log.warn("rules/moves.yaml not found on classpath");
                return;
            }
            RulesPackageRuleset ruleset = loadYaml(is, RulesPackageRuleset.class);
            if (ruleset.getMoves() != null) {
                moves.putAll(ruleset.getMoves());
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to load moves.yaml: %s", e.getMessage());
        }
    }

    private void loadOracles() {
        try {
            var oraclesUrl = Thread.currentThread().getContextClassLoader()
                    .getResource("rules/oracles");
            if (oraclesUrl == null) {
                log.warn("rules/oracles directory not found on classpath");
                return;
            }
            Path oraclesDir = Path.of(oraclesUrl.toURI());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(oraclesDir, "*.yaml")) {
                for (Path file : stream) {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        content = ANCHOR_DOT.matcher(content).replaceAll("$1i18n_");
                        RulesPackageRuleset ruleset = loadYaml(content, RulesPackageRuleset.class);
                        if (ruleset.getOracles() != null) {
                            oracles.putAll(ruleset.getOracles());
                        }
                    } catch (Exception e) {
                        log.errorf(e, "Failed to load oracle file %s: %s", file.getFileName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.errorf(e, "Failed to load oracles: %s", e.getMessage());
        }
    }

    /**
     * Parse YAML via SnakeYAML (which handles merge keys and anchors correctly),
     * then convert through Jackson for typed deserialization.
     */
    private <T> T loadYaml(Object yamlInput, Class<T> type) {
        Object raw = (yamlInput instanceof InputStream is) ? yaml.load(is) : yaml.load((String) yamlInput);
        return jsonMapper.convertValue(raw, type);
    }

    public Rules getRules() {
        return rules;
    }

    public Map<String, AtlasEntry> getAtlas() {
        return locations;
    }

    public Map<String, MoveCategory> getMoves() {
        return moves;
    }

    public Map<String, OracleTablesCollection> getOracles() {
        return oracles;
    }
}

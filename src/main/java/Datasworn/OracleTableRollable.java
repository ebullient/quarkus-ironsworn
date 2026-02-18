package Datasworn;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class OracleTableRollable {
    @JsonProperty("_id")
    private OracleRollableId id;

    @JsonProperty("_source")
    private SourceInfo source;

    @JsonProperty("column_labels")
    private OracleTableRollableTableTextColumnLabels columnLabels;

    @JsonProperty("dice")
    private DiceExpression dice;

    @JsonProperty("name")
    private Label name;

    @JsonProperty("oracle_type")
    private String oracleType;

    @JsonProperty("rows")
    private List<OracleTableRowText> rows;

    @JsonProperty("type")
    private OracleTableRollableTableTextType type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("_comment")
    private String comment;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("canonical_name")
    private Label canonicalName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("description")
    private MarkdownString description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("icon")
    private SvgImageUrl icon;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("match")
    private OracleMatchBehavior match;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("recommended_rolls")
    private OracleTableRollableTableTextRecommendedRolls recommendedRolls;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("replaces")
    private OracleRollableId replaces;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("suggestions")
    private Suggestions suggestions;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("summary")
    private MarkdownString summary;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("tags")
    private Map<String, Map<String, Tag>> tags;

    public OracleTableRollable() {
    }

    public OracleRollableId getId() {
        return id;
    }

    public void setId(OracleRollableId id) {
        this.id = id;
    }

    public SourceInfo getSource() {
        return source;
    }

    public void setSource(SourceInfo source) {
        this.source = source;
    }

    public OracleTableRollableTableTextColumnLabels getColumnLabels() {
        return columnLabels;
    }

    public void setColumnLabels(OracleTableRollableTableTextColumnLabels columnLabels) {
        this.columnLabels = columnLabels;
    }

    public DiceExpression getDice() {
        return dice;
    }

    public void setDice(DiceExpression dice) {
        this.dice = dice;
    }

    public Label getName() {
        return name;
    }

    public void setName(Label name) {
        this.name = name;
    }

    public String getOracleType() {
        return oracleType;
    }

    public void setOracleType(String oracleType) {
        this.oracleType = oracleType;
    }

    public List<OracleTableRowText> getRows() {
        return rows;
    }

    public void setRows(List<OracleTableRowText> rows) {
        this.rows = rows;
    }

    public OracleTableRollableTableTextType getType() {
        return type;
    }

    public void setType(OracleTableRollableTableTextType type) {
        this.type = type;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Label getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(Label canonicalName) {
        this.canonicalName = canonicalName;
    }

    public MarkdownString getDescription() {
        return description;
    }

    public void setDescription(MarkdownString description) {
        this.description = description;
    }

    public SvgImageUrl getIcon() {
        return icon;
    }

    public void setIcon(SvgImageUrl icon) {
        this.icon = icon;
    }

    public OracleMatchBehavior getMatch() {
        return match;
    }

    public void setMatch(OracleMatchBehavior match) {
        this.match = match;
    }

    public OracleTableRollableTableTextRecommendedRolls getRecommendedRolls() {
        return recommendedRolls;
    }

    public void setRecommendedRolls(OracleTableRollableTableTextRecommendedRolls recommendedRolls) {
        this.recommendedRolls = recommendedRolls;
    }

    public OracleRollableId getReplaces() {
        return replaces;
    }

    public void setReplaces(OracleRollableId replaces) {
        this.replaces = replaces;
    }

    public Suggestions getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(Suggestions suggestions) {
        this.suggestions = suggestions;
    }

    public MarkdownString getSummary() {
        return summary;
    }

    public void setSummary(MarkdownString summary) {
        this.summary = summary;
    }

    public Map<String, Map<String, Tag>> getTags() {
        return tags;
    }

    public void setTags(Map<String, Map<String, Tag>> tags) {
        this.tags = tags;
    }
}

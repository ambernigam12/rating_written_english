
package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Block.BlockClass;
import CoCubes.Logging.LoggingFunctions;
import java.sql.Connection;
import java.sql.ResultSet;
import org.languagetool.tokenizers.Tokenizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import CoCubes.Language.English.QuestionClass.DifficultyLevelEnum;
import CoCubes.Language.English.SubjectiveResponseEvaluationEngine.ScoringParameters;
import org.languagetool.JLanguageTool;

public abstract class ResponseBaseClass extends BlockClass {

    /* Readonly section */
    protected final int id;
    protected final String submissionText;

    protected int questionId;
    protected int dbWordCount;
    protected SubmissionStatusEnum status;
    protected float maxScore;
    protected float aggregateScore;
    protected short timeTaken;
    protected List<ParagraphClass> paragraphs;
    protected int sentenceCount;
    //Case Insensistive Word Count for typing
    protected int wordCount;
    protected int syllableCount;
    protected boolean isDuplicateParaFound;
    protected int uniqueWordCount;
    protected List<String> evaluationRemarks;
    protected boolean canUpdate;

    protected abstract String GetEvaluationRemarks(QuestionClass question);

    public boolean GetDataFromReader(ResultSet rs) {
        boolean rValue = false;
        try {
            questionId = rs.getInt("QuestionId");
            maxScore = rs.getFloat("MaxScore");
            timeTaken = rs.getShort("TimeTaken");
            dbWordCount = rs.getShort("WordCount");
            rValue = true;
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while reading Submission Id: " + id + System.lineSeparator() + e.getMessage(), "ResponseBaseClass", "GetSubmission");
        }
        return rValue;
    }

    protected ResponseBaseClass(int id, String submissionText) {
        this.id = id;
        this.submissionText = submissionText;
        paragraphs = new ArrayList<>();
        evaluationRemarks = new ArrayList<>();
    }

    @Override
    public abstract void SetupBlockElements(Tokenizer blockTokenizer, HashMap<String, Integer> unusedWordFrequency);

    protected int Id() {
        return id;
    }

    protected int QuestionId() {
        return questionId;
    }

    public SubmissionStatusEnum Status() {
        return status;
    }

    public float AggregateScore() {
        return aggregateScore;
    }

    public boolean IsDuplicateParaFound() {
        return isDuplicateParaFound;
    }

    protected abstract boolean EvaluateParameters(QuestionClass question, JLanguageTool americanEnglishSpellingTool, JLanguageTool britishEnglishSpellingTool, JLanguageTool americanEnglishGrammarTool, JLanguageTool britishEnglishGrammarTool, HashMap<Character, Byte> codes);

    protected abstract boolean Score(QuestionClass question, HashMap<DifficultyLevelEnum, HashMap<ScoringParameters, Integer>> hashScoringParameters);

    protected abstract boolean StaticAnalysis(QuestionClass question, HashMap<Integer, ResponseBaseClass> hashTotalSubmissions);

    protected boolean Finalize() {
        this.canUpdate = true;
        return true;
    }

    public boolean IsScoreNormalizationRequired() {
        return maxScore > 0 && (aggregateScore / maxScore >= ApplicationConstants.CutOffPercentForReEvaluation);
    }

    protected abstract boolean UpdateSubmission(QuestionClass question, Connection con);

    /**
     * @return the submissionText
     */
    public String getSubmissionText() {
        return submissionText;
    }

    public enum SubmissionStatusEnum {

        NotPicked((byte) 255),
        Failed((byte) 0),
        Picked((byte) ApplicationConstants.ApplicationId),
        Success((byte) 1);
        private final byte statusCode;

        private SubmissionStatusEnum(byte statusCode) {
            this.statusCode = statusCode;
        }

        protected byte Value() {
            return statusCode;
        }
    }

    protected enum LanguageEnum {

        English("en"),
        AmericanEnglish("en-US"),
        BritishEnglish("en-GB");
        private final String language;

        private LanguageEnum(String language) {
            this.language = language;
        }

        protected String Value() {
            return language;
        }
    }
}


package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Logging.LoggingFunctions;
import CoCubes.Constants.SqlConstants;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.zip.CRC32;
import static CoCubes.Helper.HelperFunctions.GetEvaluationTypeEnumFromValue;
import CoCubes.Language.English.SubjectiveResponseEvaluationEngine.EvaluationTypeEnum;
import org.languagetool.tokenizers.Tokenizer;

public class QuestionClass extends ParagraphClass {

    private final int questionId;
    private float maxMarks;
    private int minLength;
    private int maxLength;
    private DifficultyLevelEnum difficultyLevel;
    private final HashMap<String, Float> hashKeywords;
    private final WordClassCollection wordLevelHash;
    private long hash;
    private EvaluationTypeEnum evaluationType;
    private boolean areParametersSet;

    private void SetWordLeveHash(HashMap<Character, Byte> codes, int offset) {
        wordLevelHash.SplitIntoWordHashes(text, codes, offset);
    }

    private void SetHash() {
        CRC32 crc = new CRC32();
        crc.update(text.getBytes());
        hash = crc.getValue();
    }

    /* private void SetEvaluationType(int level) {
     evaluationType = EvaluationTypeEnum.NoEvaluation;
     if (level == EvaluationTypeEnum.LanguageEvaluation.Value()) {
     evaluationType = EvaluationTypeEnum.LanguageEvaluation;
     } else if (level == EvaluationTypeEnum.TypingEvaluation.Value()) {
     evaluationType = EvaluationTypeEnum.TypingEvaluation;
     }

     }*/
    protected void SetDifficulty(byte level) {
        switch (level) {
            case 0:
            case 1:
                difficultyLevel = DifficultyLevelEnum.Easy;
                break;
            case 2:
            case 3:
                difficultyLevel = DifficultyLevelEnum.Medium;
                break;
            default:
                difficultyLevel = DifficultyLevelEnum.Difficult;
                break;
        }
    }

    protected WordClassCollection WordLevelHash() {
        return wordLevelHash;
    }

    protected long Hash() {
        return hash;
    }

    protected boolean GetQuestionFromDatabase(Connection con) {
        boolean rValue = false;
        boolean isLocalConnection = false;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            if (con == null || con.isValid(1) == false) {
                con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                isLocalConnection = true;
            }
            statement = con.prepareStatement("select MinLimit, MaxLimit, QType, DifficultyLevel, Question from ObjectiveQBT with (nolock) where QuestionId=? and IsDeleted=0;");
            statement.setInt(1, questionId);
            rs = statement.executeQuery();
            if (rs.next()) {
                SetDifficulty(rs.getByte("DifficultyLevel"));
                evaluationType = GetEvaluationTypeEnumFromValue((byte) rs.getShort("QType"));
                if (evaluationType == EvaluationTypeEnum.LanguageEvaluation) {
                    if (GetOptionParameters(con)) {
                        minLength = rs.getShort("MinLimit");
                        maxLength = rs.getShort("MaxLimit");
                        rValue = true;
                    }
                } else //Typing
                {
                    CRC32 crc = new CRC32();
                    text = rs.getString("Question");
                    crc.update(text.getBytes());
                    hash = crc.getValue();
                    rValue = true;
                }
            }
            
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while fetching question parameters for Question Id: " + questionId + System.lineSeparator() + e.getMessage(), "QuestionClass", "GetQuestion");
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing ResultSet" + System.lineSeparator() + e.getMessage(), "QuestionClass", "GetQuestion");
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "QuestionClass", "GetQuestion");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception ex) {
                    LoggingFunctions.InsertError("Error closing connection" + System.lineSeparator() + ex.getMessage(), "QuestionClass", "GetQuestion");
                }
            }
        }
        return rValue;
    }

    protected boolean GetOptionParameters(Connection con) {
        boolean rValue = false;
        boolean isLocalConnection = false;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            if (con == null || con.isValid(1) == false) {
                con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                isLocalConnection = true;
            }
            statement = con.prepareStatement("select OptionText from ObjectiveOptionsTable with (nolock) where QuestionId=? and OptionValue=?;");
            int counter = 1;
            statement.setInt(counter++, questionId);
            statement.setByte(counter++, ObjectiveOptionTypeEnum.Keywords.value);
            rs = statement.executeQuery();
            if (rs.next()) {
                for (String keyword : rs.getString("OptionText").split(",")) {
                    String trimKeyword = keyword.trim().toLowerCase();
                    if (trimKeyword.length() > ApplicationConstants.MinCharCountPerWord && !hashKeywords.containsKey(trimKeyword)) {
                        hashKeywords.put(trimKeyword, 1f);
                    }
                }
            }
            rValue = true;
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while fetching option parameters for Question Id: " + questionId + System.lineSeparator() + e.getMessage(), "QuestionClass", "GetOptionParameters");
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing ResultSet" + System.lineSeparator() + e.getMessage(), "QuestionClass", "GetOptionParameters");
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "QuestionClass", "GetOptionParameters");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception ex) {
                    LoggingFunctions.InsertError("Error closing connection" + System.lineSeparator() + ex.getMessage(), "QuestionClass", "GetOptionParameters");
                }
            }
        }
        return rValue;
    }

    protected QuestionClass(int questionId) {
        this.questionId = questionId;
        this.hashKeywords = new HashMap<>();
        wordLevelHash = new WordClassCollection();
        evaluationType = EvaluationTypeEnum.NoEvaluation;
    }

    protected int QuestionId() {
        return questionId;
    }

    protected HashMap<String, Float> Keywords() {
        return hashKeywords;
    }

    protected float MaxMarks() {
        return maxMarks;
    }

    protected int MinLength() {
        return minLength;
    }

    protected int MaxLength() {
        return maxLength;
    }

    protected void SetMinLength(int minLength) {
        this.minLength = minLength;
    }

    protected void SetMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    protected EvaluationTypeEnum EvaluationType() {
        return evaluationType;
    }

    protected void SetParameters(HashMap<Character, Byte> codes, Tokenizer sentenceTokenizer) {
        if (evaluationType == EvaluationTypeEnum.TypingEvaluation && areParametersSet == false) {
            SetWordLeveHash(codes, codes.size() + 1);
            SetHash();
            SetupBlockElements(sentenceTokenizer, null);
            areParametersSet = true;
        }
    }

    protected DifficultyLevelEnum DifficultyLevel() {
        return difficultyLevel;
    }

    public float GetDifficultyLevelMistakeFactor() {
        final float EasyLevelMistakeFactor = 0;
        final float MediumLevelMistakeFactor = 1f;
        final float DifficultLevelMistakeFactor = 1.75f;
        switch (difficultyLevel) {
            case Easy:
                return EasyLevelMistakeFactor;
            case Medium:
                return MediumLevelMistakeFactor;
            case Difficult:
                return DifficultLevelMistakeFactor;
        }
        return 0;
    }

    public enum DifficultyLevelEnum {

        Easy,
        Medium,
        Difficult
        //private short difficultyLevel;

        /* private DifficultyLevelEnum(short difficultyLevel) {
         //this.difficultyLevel = difficultyLevel;
         }*/
    }

    public enum ObjectiveOptionTypeEnum {

        MaxOption((byte) 8),
        Keywords((byte) 254),
        Answer((byte) 255);
        private byte value;

        private ObjectiveOptionTypeEnum(byte value) {
            this.value = value;
        }
    }

}

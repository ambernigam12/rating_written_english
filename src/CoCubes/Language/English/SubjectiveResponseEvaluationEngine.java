package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.ShutDownEvent.ShutDownThread;
import CoCubes.Helper.HelperFunctions;
import CoCubes.Logging.LoggingFunctions;
import CoCubes.Constants.SqlConstants;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.language.AmericanEnglish;
import org.languagetool.language.BritishEnglish;
import org.languagetool.rules.Rule;
import org.languagetool.rules.spelling.SpellingCheckRule;
import org.languagetool.tokenizers.Tokenizer;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalTime;
import java.util.*;
import CoCubes.Language.English.QuestionClass.DifficultyLevelEnum;
import CoCubes.Language.English.ResponseBaseClass.SubmissionStatusEnum;
import org.languagetool.rules.patterns.AbstractPatternRule;

public class SubjectiveResponseEvaluationEngine {

    static int difficultyLevel = 3;

    public static void main(String[] args) {
        LoggingFunctions.InsertLog("Starting Application at: " + new Date());
        HelperFunctions.writeIntoFile("Id,Word_Count,Lexical_Density,Readability,Word_Count,Grammar_Error,Spelling_Error,Unique_Grammar_Error,Unique_Spelling_Error,Keyword_Match_Count,Vocabulary_Score,Vocabulary_Score_Out_Off,Readability_Score,Readability_Score_Out_Off,Length_Score,Length_Score_Out_Off,Grammar_Score,Grammar_Score_Out_Off,Spelling_Score,Spelling_Score_Out_Off,Keyword_Score,Keyword_Score_Out_Off,Language,Word_Count,Sentence_Count,Paragraphs_Size,Time_Taken,Typography_Errors_1,Miscellaneous_Errors_1,Typography_Errors_2,Redundant_Phrases,Capitalization_Errors,Major_Errors,Common_Replacement_Errors,Punctuation_Errors_1,Punctuation_Errors_2,Style_Based_Errors,Miscellaneous_Errors_2,Cumulative_Optional_Features_Percent", ApplicationConstants.FeatureFileName, false);

        //Attaching a closing hook, should be used to remove anything that needs cleaning
        Runtime runner = Runtime.getRuntime();
        runner.addShutdownHook(new ShutDownThread());

        boolean canProceed = false;
        HashMap<Integer, ResponseBaseClass> hashTotalSubmissions = new HashMap<>();
        HashMap<Integer, QuestionClass> hashQuestions = new HashMap<>();
        HashMap<DifficultyLevelEnum, HashMap<ScoringParameters, Integer>> hashScoringParameters = PrepareScoringParameterHash();
        JLanguageTool americanEnglishSpellingTool = null;
        JLanguageTool britishEnglishSpellingTool = null;
        JLanguageTool americanEnglishGrammarTool = null;
        JLanguageTool britishEnglishGrammarTool = null;
        HashMap<Integer, List<EnglishLanguageResponseClass>> toppers = new HashMap<>();
        Tokenizer sentenceTokenizer = null;
        try {
            Class.forName(SqlConstants.MsSqlDriverString);
            Language americanLanguage = new AmericanEnglish();
            Language britishLanguage = new BritishEnglish();
            americanEnglishSpellingTool = new JLanguageTool(americanLanguage);
            britishEnglishSpellingTool = new JLanguageTool(britishLanguage);
            americanEnglishGrammarTool = new JLanguageTool(americanLanguage);
            britishEnglishGrammarTool = new JLanguageTool(britishLanguage);
            /*File nGramFile = new File("n-grams");
             americanEnglishSpellingTool.activateLanguageModelRules(nGramFile);
             americanEnglishGrammarTool.activateLanguageModelRules(nGramFile);
             britishEnglishSpellingTool.activateLanguageModelRules(nGramFile);
             britishEnglishGrammarTool.activateLanguageModelRules(nGramFile);*/
            sentenceTokenizer = americanLanguage.getSentenceTokenizer();
            SetRules(americanEnglishSpellingTool, britishEnglishSpellingTool, americanEnglishGrammarTool, britishEnglishGrammarTool);
            List<String> ignoreWords = HelperFunctions.GetPhraseListFromFile(ApplicationConstants.CustomSpellingFile, false);
            if (ignoreWords != null) {
                if (ignoreWords.size() > 0) {
                    AddKeywordToIgnoreList(ignoreWords, americanEnglishSpellingTool, britishEnglishSpellingTool);
                }
                canProceed = GetIgnorePhrases(americanEnglishSpellingTool, britishEnglishSpellingTool);
            }
        } catch (Exception ex) {
            LoggingFunctions.InsertError("Submission evaluation terminated as Language Tool threw an exception." + System.lineSeparator() + ex.getMessage(), "SubjectiveResponseEvaluationEngine", "main");
        }
        if (canProceed) {
            LoggingFunctions.InsertLog("Dictionary Created, Looking for submissions at: " + new Date());
            HashMap<Character, Byte> codes = HelperFunctions.GetCharCodes();
            UpdateSubmissisonsInLimboOnStartup();
            LocalTime exitTime = LocalTime.of(ApplicationConstants.StopHour, ApplicationConstants.StopMinute);
            Connection con = null;
            try {
                while (LocalTime.now().compareTo(exitTime) < 1) {
                    // LoggingFunctions.InsertLog("Checking: " + LocalTime.now());
                    if (con == null || con.isValid(1) == false) {
                        try {
                            con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                        } catch (Exception ex) {
                            LoggingFunctions.InsertError("Exception occurred while making connection: " + System.lineSeparator() + ex.getMessage(), "SubjectiveResponseEvaluationEngine", "main");
                        }
                    }
                    HashMap<Integer, ResponseBaseClass> hashCurrentSubmissions = GetSubmissions(hashQuestions, hashTotalSubmissions, con);
                    if (hashCurrentSubmissions != null && hashCurrentSubmissions.size() > 0 && hashQuestions.size() > 0) {
                        HashSet<String> ignoreWordList = new HashSet<>();
                        GetQuestion(hashQuestions, ignoreWordList, con);
                        if (ignoreWordList.size() > 0) {
                            AddKeywordToIgnoreList(new ArrayList<>(ignoreWordList), americanEnglishSpellingTool, britishEnglishSpellingTool);
                        }
                        //Update Stage 1
                        UpdateSubmissionStatus(hashCurrentSubmissions, con);
                        if (EvaluateSubmissions(americanEnglishSpellingTool, britishEnglishSpellingTool, americanEnglishGrammarTool, britishEnglishGrammarTool, sentenceTokenizer, hashCurrentSubmissions, hashQuestions, hashScoringParameters, hashTotalSubmissions, codes)) {
                            ContextAnalysisAndUpdateEvaluationDetails(hashCurrentSubmissions, hashQuestions, con, toppers, sentenceTokenizer);
                        }
                        LoggingFunctions.InsertIntermediateLog(ignoreWordList.size(), hashCurrentSubmissions);
                    } else {
                        try {
                            if (con != null) {
                                con.close();
                                con = null;
                            }
                            break;
                        } catch (Exception ex) {
                            LoggingFunctions.InsertError("Exception occurred while closing the connection: " + System.lineSeparator() + ex.getMessage(), "SubjectiveResponseEvaluationEngine", "main");
                        }
                    }
                    LoggingFunctions.InsertTrackerLog();

                    //TODO: remove the below code before releasing
                    /*if (hashCurrentSubmissions == null || hashCurrentSubmissions.size() == 0)
                     {
                     DBUpdateBatch.UpdateDifficultyLevel(difficultyLevel, 5000);
                     hashCurrentSubmissions = new HashMap<>();
                     hashTotalSubmissions = new HashMap<>();
                     hashQuestions = new HashMap<>();
                     difficultyLevel += 2;
                     if (difficultyLevel > 7)
                     break;
                     }
                     */
                }
            } catch (Exception ex) {
                LoggingFunctions.InsertError(ex.getMessage(), "SubjectiveResponseEvaluationEngine", "main");
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (Exception e) {
                        LoggingFunctions.InsertError("Error occurred while closing the connection" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "main");
                    }
                }
            }
        }
        LoggingFunctions.InsertFinalLog(hashTotalSubmissions);
    }

    private static void SetRules(JLanguageTool americanEnglishSpellingTool, JLanguageTool britishEnglishSpellingTool, JLanguageTool americanEnglishGrammarTool, JLanguageTool britishEnglishGrammarTool) {
        List<String> lstSpellingRuleIds = new ArrayList<>();
        List<String> lstGrammarRuleIds = new ArrayList<>();

        for (Rule rule : americanEnglishSpellingTool.getAllActiveRules()) {
            if (rule instanceof SpellingCheckRule) {

                lstSpellingRuleIds.add(rule.getId());
            } else {
                lstGrammarRuleIds.add(rule.getId());
            }
        }
        americanEnglishGrammarTool.disableRules(lstSpellingRuleIds);
        americanEnglishSpellingTool.disableRules(lstGrammarRuleIds);

        lstSpellingRuleIds = new ArrayList<>();
        lstGrammarRuleIds = new ArrayList<>();

        for (Rule rule : britishEnglishSpellingTool.getAllActiveRules()) {
            if (rule instanceof SpellingCheckRule) {

                lstSpellingRuleIds.add(rule.getId());
            } else {
                lstGrammarRuleIds.add(rule.getId());
            }
        }
        britishEnglishGrammarTool.disableRules(lstSpellingRuleIds);
        britishEnglishSpellingTool.disableRules(lstGrammarRuleIds);

        try {
            File customRules = new File(ApplicationConstants.CustomRulesFile);
            if (customRules.exists()) {
                //Load custom defined rules
                for (AbstractPatternRule rule : americanEnglishGrammarTool.loadPatternRules(customRules.getAbsolutePath())) {
                    americanEnglishGrammarTool.addRule(rule);
                    britishEnglishGrammarTool.addRule(rule);
                }
            }
        } catch (Exception ex) {
            LoggingFunctions.InsertError("Exception occurred while trying to load user rules." + System.lineSeparator() + ex.getMessage(), "SubjectiveResponseEvaluationEngine", "SetRules");
        }
    }

    private static void AddKeywordToIgnoreList(List<String> keywords, JLanguageTool americanEnglishSpellingTool, JLanguageTool britishEnglishSpellingTool) {
        for (Rule rule : americanEnglishSpellingTool.getAllActiveRules()) {
            if (rule instanceof SpellingCheckRule) {
                ((SpellingCheckRule) rule).addIgnoreTokens(keywords);
                ((SpellingCheckRule) rule).setConvertsCase(true);
                break;
            }
        }
        for (Rule rule : britishEnglishSpellingTool.getAllActiveRules()) {
            if (rule instanceof SpellingCheckRule) {
                ((SpellingCheckRule) rule).addIgnoreTokens(keywords);
                ((SpellingCheckRule) rule).setConvertsCase(true);
                break;
            }
        }
    }

    private static boolean GetIgnorePhrases(JLanguageTool american, JLanguageTool british) {
        List<String> phrases = HelperFunctions.GetPhraseListFromFile(ApplicationConstants.AcceptPhraseFile, true);
        boolean rValue = false;
        if (phrases != null) {
            for (Rule rule : american.getAllActiveRules()) {
                if (rule instanceof SpellingCheckRule) {
                    ((SpellingCheckRule) rule).acceptPhrases(phrases);
                }
            }
            for (Rule rule : british.getAllActiveRules()) {
                if (rule instanceof SpellingCheckRule) {
                    ((SpellingCheckRule) rule).acceptPhrases(phrases);
                }
            }
            rValue = true;
        }
        return rValue;
    }

    private static HashMap<DifficultyLevelEnum, HashMap<ScoringParameters, Integer>> PrepareScoringParameterHash() {
        HashMap<DifficultyLevelEnum, HashMap<ScoringParameters, Integer>> hashScoringParameters = new HashMap<>();

        //Easy
        HashMap<ScoringParameters, Integer> hashScoringParametersEasyLevel = new HashMap<>();
        hashScoringParametersEasyLevel.put(ScoringParameters.Grammar, 25);
        hashScoringParametersEasyLevel.put(ScoringParameters.Spelling, 25);
        hashScoringParametersEasyLevel.put(ScoringParameters.Vocabulary, 20);
        hashScoringParametersEasyLevel.put(ScoringParameters.Readability, 20);
        hashScoringParametersEasyLevel.put(ScoringParameters.Keyword, 10);
        hashScoringParametersEasyLevel.put(ScoringParameters.WordLimit, -15);
        hashScoringParameters.put(DifficultyLevelEnum.Easy, hashScoringParametersEasyLevel);

        //Medium
        HashMap<ScoringParameters, Integer> hashScoringParametersMediumLevel = new HashMap<>();
        hashScoringParametersMediumLevel.put(ScoringParameters.Grammar, 30);
        hashScoringParametersMediumLevel.put(ScoringParameters.Spelling, 25);
        hashScoringParametersMediumLevel.put(ScoringParameters.Vocabulary, 20);
        hashScoringParametersMediumLevel.put(ScoringParameters.Readability, 15);
        hashScoringParametersMediumLevel.put(ScoringParameters.Keyword, 10);
        hashScoringParametersMediumLevel.put(ScoringParameters.WordLimit, -15);
        hashScoringParameters.put(DifficultyLevelEnum.Medium, hashScoringParametersMediumLevel);

        //Difficult
        HashMap<ScoringParameters, Integer> hashScoringParametersDifficultLevel = new HashMap<>();
        hashScoringParametersDifficultLevel.put(ScoringParameters.Grammar, 30);
        hashScoringParametersDifficultLevel.put(ScoringParameters.Spelling, 25);
        hashScoringParametersDifficultLevel.put(ScoringParameters.Vocabulary, 20);
        hashScoringParametersDifficultLevel.put(ScoringParameters.Readability, 15);
        hashScoringParametersDifficultLevel.put(ScoringParameters.Keyword, 10);
        hashScoringParametersDifficultLevel.put(ScoringParameters.WordLimit, -15);
        hashScoringParameters.put(DifficultyLevelEnum.Difficult, hashScoringParametersDifficultLevel);

        return hashScoringParameters;
    }

    private static HashMap<Integer, ResponseBaseClass> GetSubmissions(HashMap<Integer, QuestionClass> hashQuestions,
            HashMap<Integer, ResponseBaseClass> hashTotalSubmissions, Connection con) {
        boolean isLocalConnection = false;
        PreparedStatement statement = null;
        ResultSet rs = null;
        HashMap<Integer, ResponseBaseClass> hashCurrentSubmissions = null;
        try {
            int counter = 1;
            if (con == null || con.isValid(1) == false) {
                con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                isLocalConnection = true;
            }
            statement = con.prepareStatement("UPDATE EnglishSubmissionEvaluationTable SET EvaluationStatus=?, EvaluationStartedOn=? WHERE Id IN (SELECT TOP(?) Id FROM EnglishSubmissionEvaluationTable WHERE EvaluationStatus=? order by Id)");
            statement.setInt(counter++, ApplicationConstants.ApplicationId);
            statement.setTime(counter++, new java.sql.Time(System.currentTimeMillis()));
            statement.setInt(counter++, ApplicationConstants.SubmissionToFetch);
            statement.setByte(counter++, SubmissionStatusEnum.NotPicked.Value());
            if (statement.executeUpdate() > 0) {
                hashCurrentSubmissions = new HashMap<>();
                statement = con.prepareStatement("SELECT Id, SubjectiveAnswer, QuestionId, MaxScore, TimeTaken, WordCount, EvaluationType FROM EnglishSubmissionEvaluationTable WITH(NOLOCK) WHERE EvaluationStatus=?");
                counter = 1;
                statement.setInt(counter++, ApplicationConstants.ApplicationId);
                rs = statement.executeQuery();
                while (rs.next()) {
                    ResponseBaseClass submission = HelperFunctions.GetSubmission(rs);
                    if (submission != null && submission.GetDataFromReader(rs)) {
                        submission.status = SubmissionStatusEnum.Picked;
                        hashTotalSubmissions.put(submission.Id(), submission);
                        hashCurrentSubmissions.put(submission.Id(), submission);
                        if (!hashQuestions.containsKey(submission.QuestionId())) {
                            hashQuestions.put(submission.QuestionId(), null);
                        }
                    }
                }

            }
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while fetching submissions" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "GetSubmissions");
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
                LoggingFunctions.InsertError("Error occurred while closing ResultSet" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "GetSubmissions");
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "GetSubmissions");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing Connection" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "GetSubmissions");
                }
            }
        }
        return hashCurrentSubmissions;
    }

    private static boolean GetQuestion(HashMap<Integer, QuestionClass> hashQuestions, HashSet<String> ignoreWordList,
            Connection con) {
        boolean rvalue = true;
        for (int questionId : hashQuestions.keySet()) {
            if (hashQuestions.get(questionId) == null) {
                QuestionClass question = new QuestionClass(questionId);
                if (question.GetQuestionFromDatabase(con)) {
                    hashQuestions.put(questionId, question);
                    if (question.Keywords().size() > 0) {
                        ignoreWordList.addAll(question.Keywords().keySet());
                    }
                }
                rvalue = false;
            }
        }
        return rvalue;
    }

    private static boolean EvaluateSubmissions(JLanguageTool americanEnglishSpellingTool, JLanguageTool britishEnglishSpellingTool, JLanguageTool americanEnglishGrammarTool, JLanguageTool britishEnglishGrammarTool, Tokenizer sentenceTokenizer, HashMap<Integer, ResponseBaseClass> hashCurrentSubmissions,
            HashMap<Integer, QuestionClass> hashQuestions, HashMap<DifficultyLevelEnum, HashMap<ScoringParameters, Integer>> hashScoringParameters, HashMap<Integer, ResponseBaseClass> hashTotalSubmissions, HashMap<Character, Byte> codes) {
        int updateSubmissionCount = 0;
        for (int submissionId : hashCurrentSubmissions.keySet()) {
            int questionId = hashCurrentSubmissions.get(submissionId).QuestionId();
            QuestionClass question;
            if ((question = hashQuestions.get(questionId)) != null) {
                question.SetParameters(codes, sentenceTokenizer);
                ResponseBaseClass submission = hashCurrentSubmissions.get(submissionId);
                submission.SetupBlockElements(sentenceTokenizer, null);
                if (submission.EvaluateParameters(question, americanEnglishSpellingTool, britishEnglishSpellingTool, americanEnglishGrammarTool, britishEnglishGrammarTool, codes)) {
                    submission.StaticAnalysis(question, hashTotalSubmissions);
                    submission.Score(question, hashScoringParameters);
                }
                submission.Finalize();
                updateSubmissionCount++;
            } else {
                LoggingFunctions.InsertError("Parameters not set for Submission Id: " + submissionId + " as question info was not present for Question Id: " + questionId, "SubjectiveResponseEvaluationEngine", "EvaluateSubmissions");
            }
        }
        return updateSubmissionCount > 0;
    }

    private static int ContextAnalysisAndUpdateEvaluationDetails(HashMap<Integer, ResponseBaseClass> hashTotalSubmissions, HashMap<Integer, QuestionClass> hashQuestions, Connection con, HashMap<Integer, List<EnglishLanguageResponseClass>> toppers, Tokenizer sentenceTokenizer) {
        //Context match through n-grams
        ContextMatchAnalysis(hashTotalSubmissions, toppers, con, sentenceTokenizer);
        int totalUpdates = 0;
        ResponseBaseClass submission;
        QuestionClass question;
        for (int submissionId : hashTotalSubmissions.keySet()) {
            submission = hashTotalSubmissions.get(submissionId);
            if ((question = hashQuestions.get(submission.QuestionId())) != null) {
                if (submission.UpdateSubmission(question, con)) {
                    totalUpdates++;
                } else {
                    LoggingFunctions.InsertError("Submission Id: " + submissionId + " could not be updated as parameters could not be picked for Question Id: " + submission.QuestionId(), "SubjectiveResponseEvaluationEngine", "UpdateEvaluationDetails");
                }
            }
        }
        return totalUpdates;
    }

    protected enum ScoringParameters {

        Grammar,
        Spelling,
        Vocabulary,
        Readability,
        Keyword,
        WordLimit
    }

    public enum EvaluationTypeEnum {

        NoEvaluation((byte) 0),
        LanguageEvaluation((byte) 9),
        TypingEvaluation((byte) 10);
        private final byte value;

        private EvaluationTypeEnum(byte value) {
            this.value = value;
        }

        public byte Value() {
            return value;
        }

    }

    private static boolean UpdateSubmissisonsInLimboOnStartup() {
        boolean rValue = false;
        Connection con = null;
        PreparedStatement statement = null;
        try {
            con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
            statement = con.prepareStatement("update EnglishSubmissionEvaluationTable set EvaluationStatus=? where EvaluationStatus >= ? and EvaluationStatus <= ?");
            int counter = 1;
            statement.setByte(counter++, SubmissionStatusEnum.NotPicked.Value());
            statement.setInt(counter++, ApplicationConstants.ApplicationId - ApplicationConstants.IntermediateStates);
            statement.setInt(counter++, ApplicationConstants.ApplicationId);
            rValue = statement.executeUpdate() > 0;
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while updating submissions in limbo" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "UpdateSubmissisonsInLimbo");
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "UpdateSubmissisonsInLimbo");
                }
            }
            if (con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing Connection" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "UpdateSubmissisonsInLimbo");
                }
            }
        }
        return rValue;
    }

    private static int UpdateSubmissionStatus(HashMap<Integer, ResponseBaseClass> hashCurrentSubmissions, Connection con) {
        int updateCount = 0;
        boolean isLocalConnection = false;
        PreparedStatement statement = null;
        try {
            if (con == null || con.isValid(1) == false) {
                con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                isLocalConnection = true;
            }
            statement = con.prepareStatement("update EnglishSubmissionEvaluationTable set EvaluationStatus=EvaluationStatus-1 where Id=?");
            for (int submissionId : hashCurrentSubmissions.keySet()) {
                statement.setInt(1, submissionId);
                updateCount += statement.executeUpdate();
            }
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while updating submissions status" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "UpdateSubmissionStatus");
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "UpdateSubmissionStatus");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing Connection" + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "UpdateSubmissionStatus");
                }
            }
        }
        return updateCount;
    }

    private static void ContextMatchAnalysis(HashMap<Integer, ResponseBaseClass> hashTotalSubmissions, HashMap<Integer, List<EnglishLanguageResponseClass>> questionSpecificToppers, Connection con, Tokenizer sentenceTokenizer) {
        List<ResponseBaseClass> entries = new ArrayList<>();
        for (Map.Entry<Integer, ResponseBaseClass> entry : hashTotalSubmissions.entrySet()) {
            entries.add(entry.getValue());
        }
        for (ResponseBaseClass entry : entries) {
            int numNgrams = ((EnglishLanguageResponseClass) entry).ngrams.size();
            float[] cosineSimilarities = new float[numNgrams];
            List<EnglishLanguageResponseClass> toppersForCurrentQuestion = questionSpecificToppers.get(entry.questionId);
            if (toppersForCurrentQuestion == null) {
                toppersForCurrentQuestion = HelperFunctions.getToppersForQuestion(entry.questionId, con, entry.maxScore, sentenceTokenizer);
                questionSpecificToppers.put(entry.questionId, toppersForCurrentQuestion);
            }
            for (EnglishLanguageResponseClass toppersForCurrentQuestion1 : toppersForCurrentQuestion) {
                if (toppersForCurrentQuestion1.id != entry.id) {
                    for (int k = 0; k < numNgrams; k++) {
                        float currentCosineSimilarity = toppersForCurrentQuestion1.ngrams.get(k).GetCosineSimilarity(((EnglishLanguageResponseClass) entry).ngrams.get(k));
                        if (currentCosineSimilarity > cosineSimilarities[k]) {
                            cosineSimilarities[k] = currentCosineSimilarity;
                        }
                    }
                }
            }
            /*for (int k = 0; k < numNgrams; k++) {
             System.out.print("," + cosineSimilarities[k]);
             }
             System.out.println();*/
        }
    }
}

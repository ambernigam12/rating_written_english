package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Constants.SqlConstants;
import CoCubes.Helper.HelperFunctions;
import CoCubes.Logging.LoggingFunctions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.CRC32;
import static CoCubes.Helper.HelperFunctions.SplitIntoLowerCaseWords;
import CoCubes.Language.English.QuestionClass.DifficultyLevelEnum;
import java.sql.DriverManager;
import org.languagetool.JLanguageTool;
import org.languagetool.tokenizers.Tokenizer;

public class EnglishTypingResponseClass extends ResponseBaseClass {

    static final byte EasyLowWPM = 7;
    static final byte EasyAvgWPM = 15;
    static final byte EasyHighWPM = 25;
    static final byte MediumLowWPM = 20;
    static final byte MediumAvgWPM = 40;//40
    static final byte MediumHighWPM = 55;//55
    static final byte DifficultLowWPM = 30;
    static final byte DifficultAvgWPM = 60;
    static final byte DifficultHighWPM = 80;

    private final HashMap<OperationTypeEnum, Integer> operationCounts;
    private short typingSpeed;
    private float typingScore;
    private float insertionScore;
    private float deletionScore;
    private float substitutionScore;
    private float caseScore;
    private float outOfInsertionScore;
    private float outOfDeletionScore;
    private float outOfSubstitutionScore;
    private float outOfCaseScore;
    private float outOfTypingScore;
    private final long hash;
    private short accuracy;
    private int errorCount;
    private int responseWordCount;

    private int ComputeMistakesAndCorrectWordCount(WordClassCollection questionWords, HashMap<Character, Byte> codes, int offset) {
        //see if this has the utility to be moved to the class level
        WordClassCollection responseWords = new WordClassCollection();
        responseWords.SplitIntoWordHashes(submissionText, codes, offset);
        responseWordCount = responseWords.size();
        int[][] distance = new int[responseWords.size() + 1][questionWords.size() + 1];
        for (int i = 0; i <= responseWords.size(); i++) {
            distance[i][0] = i;
        }
        for (int j = 1; j <= questionWords.size(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= responseWords.size(); i++) {
            WordClass responseCurrentWord = responseWords.get(i - 1);
            for (int j = 1; j <= questionWords.size(); j++) {
                WordClass questionCurrentWord = questionWords.get(j - 1);
                distance[i][j] = HelperFunctions.Minimum(distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1] + (responseCurrentWord.LowerCaseWordHash() != (questionCurrentWord.LowerCaseWordHash()) ? 1 : 0));
            }
        }

        return SegregateMistakeCount(distance, questionWords, responseWords);
    }

    private int SegregateMistakeCount(int[][] editDistances, List<WordClass> questionWords, List<WordClass> responseWords) {
        //for future usage
        WordClassCollection responseUnionQuestionWords = new WordClassCollection();
        int i = responseWords.size(), j = questionWords.size();
        int exactMatchCount = 0;
        int insertCount = 0;
        int deleteCount = 0;
        int substitutionCount = 0;
        int caseErrorCount = 0;
        int caseDontCareMatch = 0;
        errorCount = 0;
        while (i >= 0 && j >= 0) {
            OperationTypeEnum operation = OperationTypeEnum.CaseInsensitiveMatch;
            boolean isSubmissionWord = true;
            int initialI = i;
            int initialJ = j;
            int initialDistance = editDistances[i][j];
            int upperCellDistance = GetDistance(i - 1, j, editDistances);
            int leftCellDistance = GetDistance(i, j - 1, editDistances);
            int diagonalCellDistance = GetDistance(i - 1, j - 1, editDistances);
            if (i > 0 && j > 0 && (diagonalCellDistance <= upperCellDistance && diagonalCellDistance <= leftCellDistance)) {
                if (diagonalCellDistance < initialDistance) {
                    operation = OperationTypeEnum.Substitution;
                    substitutionCount++;
                } else {
                    caseDontCareMatch++;
                    if (responseWords.get(i - 1).Hash() == (questionWords.get(j - 1).Hash())) {
                        exactMatchCount++;
                    }
                }
                //else a match
                i--;
                j--;
            } else if (j > 0 && leftCellDistance <= upperCellDistance && leftCellDistance <= diagonalCellDistance) {
                operation = OperationTypeEnum.Deletion;
                deleteCount++;
                j--;
                isSubmissionWord = false;
            } else if (i > 0) {
                operation = OperationTypeEnum.Insertion;
                insertCount++;
                i--;
            }
            if (operation == OperationTypeEnum.CaseInsensitiveMatch && initialI > 0 && initialJ > 0) {
                int hashDiff = questionWords.get(initialJ - 1).Hash() - responseWords.get(initialI - 1).Hash();
                //Case Type Error Count
                if (questionWords.get(initialJ - 1).Text().equals(responseWords.get(initialI - 1).Text()) == false && hashDiff % ApplicationConstants.DiffBetweenLowerAndUpperCaseAlphabets == 0) {
                    int currentCaseErrors = hashDiff / ApplicationConstants.DiffBetweenLowerAndUpperCaseAlphabets;
                    if (currentCaseErrors == 0) {
                        //+1 -1 case
                        currentCaseErrors = 2;
                    }
                    operation = OperationTypeEnum.CaseErrors;
                    caseErrorCount += Math.min(ApplicationConstants.UpperCapCaseErrorsPerWord, Math.max(1, Math.abs(currentCaseErrors)));
                }
            } //i and j not initialI and initialJ because we've to skip to the deletion operation while checking for merged words
            else if (i >= 0 && j >= 0 && operation == OperationTypeEnum.Deletion && substitutionCount > 0 && AreWordsMerged(i, j, questionWords, responseWords)) {
                substitutionCount--;
            }
            WordClass currentWord = isSubmissionWord && initialI > 0 ? responseWords.get(initialI - 1) : initialJ > 0 ? questionWords.get(initialJ - 1) : null;
            if (currentWord != null) {
                currentWord.SetOperation(operation);
                responseUnionQuestionWords.add(currentWord);
                if (operation == OperationTypeEnum.Insertion || operation == OperationTypeEnum.Substitution || operation == OperationTypeEnum.CaseErrors) {
                    errorCount++;
                }
            } else {
                break;
            }
        }
        operationCounts.put(OperationTypeEnum.Insertion, insertCount);
        operationCounts.put(OperationTypeEnum.Deletion, deleteCount);
        operationCounts.put(OperationTypeEnum.Substitution, substitutionCount);
        operationCounts.put(OperationTypeEnum.CaseErrors, caseErrorCount);
        operationCounts.put(OperationTypeEnum.CaseInsensitiveMatch, caseDontCareMatch);
        operationCounts.put(OperationTypeEnum.ExactMatch, exactMatchCount);
        return errorCount;
    }

    private void SetAccuracy() {
        if (responseWordCount > 0) {
            accuracy = (short) Math.min(100, Math.max(0, ((float) operationCounts.get(OperationTypeEnum.ExactMatch) / responseWordCount) * 100));
        }
    }

    private int GetDistance(int i, int j, int[][] editDistance) {
        if (i < 0 || j < 0 || i > editDistance.length || j > editDistance[0].length) {
            return Integer.MAX_VALUE;
        }
        return editDistance[i][j];
    }

    private boolean AreWordsMerged(int i, int j, List<WordClass> questionWords, List<WordClass> responseWords) {
        boolean areWordsMerged = false;
        int skippedWordCount = 0;
        if (i >= 0 && j >= 0 && j < questionWords.size() && i < responseWords.size()) {
            String mergedPassageWords = "";
            while (j < questionWords.size() && mergedPassageWords.length() <= responseWords.get(i).Text().length()) {
                skippedWordCount++;
                mergedPassageWords = mergedPassageWords + questionWords.get(j++).Text();
                if (mergedPassageWords.equals(responseWords.get(i).Text()) && skippedWordCount > 1) {
                    areWordsMerged = true;
                    break;
                }
            }
        }
        return areWordsMerged;
    }

    private void SetTypingParameters(DifficultyLevelEnum difficulty) {
        int totalCorrectWords = (int) ((float) submissionText.length() / ApplicationConstants.AverageWordLength - errorCount);
        if (totalCorrectWords >= 0) {
            typingSpeed = timeTaken > 0 ? (short) (totalCorrectWords / (float) timeTaken * 60) : 0;
        } else {
            LoggingFunctions.InsertError("Typing Speed formula returned negative value for Submission Id: " + id + System.lineSeparator() + "Using original formula of Typing Speed as of now.", "EnglishTypingResponseClass", "SetTypingParameters");
            typingSpeed = timeTaken > 0 ? (short) (Math.max(0, Math.ceil(wordCount / (float) timeTaken * 60))) : 0;
        }
        if (typingSpeed > 0) {
            float percent = 0;
            byte lowTypingSpeed = 0;
            byte avgTypingSpeed = 0;
            byte highTypingSpeed = 0;
            switch (difficulty) {
                case Easy:
                    lowTypingSpeed = EasyLowWPM;
                    avgTypingSpeed = EasyAvgWPM;
                    highTypingSpeed = EasyHighWPM;
                    break;
                case Medium:
                    lowTypingSpeed = MediumLowWPM;
                    avgTypingSpeed = MediumAvgWPM;
                    highTypingSpeed = MediumHighWPM;
                    break;
                case Difficult:
                    lowTypingSpeed = DifficultLowWPM;
                    avgTypingSpeed = DifficultAvgWPM;
                    highTypingSpeed = DifficultHighWPM;
                    break;
            }
            if (typingSpeed >= highTypingSpeed) {
                percent = 100;
            } else if (typingSpeed >= avgTypingSpeed) {
                percent = ApplicationConstants.MinPercentScoreAboveMedium + (100 - ApplicationConstants.MinPercentScoreAboveMedium) * (float) (typingSpeed - avgTypingSpeed) / (highTypingSpeed - avgTypingSpeed);
            } else if (typingSpeed >= lowTypingSpeed) {
                percent = ApplicationConstants.MinPercentScoreAboveLow + (ApplicationConstants.MinPercentScoreAboveMedium - ApplicationConstants.MinPercentScoreAboveLow) * (float) (typingSpeed - lowTypingSpeed) / (avgTypingSpeed - lowTypingSpeed);
            }
            outOfTypingScore = ApplicationConstants.SpeedContributionToOverallPercent * maxScore / 100;
            typingScore = HelperFunctions.RoundNumber(percent * outOfTypingScore / 100, (byte) 2);
        }
    }

    private void SetInsertionScore(int insertions, int maxAllowedMistakes, float similarityFactor) {
        outOfInsertionScore = maxScore * OperationTypeEnum.Insertion.Weight() * (100 - ApplicationConstants.SpeedContributionToOverallPercent) / 10000;
        if (maxAllowedMistakes <= 0) {
            insertionScore = 0;
        } else {
            insertionScore = HelperFunctions.RoundNumber((100 - Math.min(100, Math.max(0, (float) 100 * insertions / maxAllowedMistakes))) * outOfInsertionScore * similarityFactor / 100, (byte) 2);
        }
    }

    private void SetDeletionScore(int deletions, int maxAllowedMistakes, float similarityFactor) {
        outOfDeletionScore = maxScore * OperationTypeEnum.Deletion.Weight() * (100 - ApplicationConstants.SpeedContributionToOverallPercent) / 10000;
        if (maxAllowedMistakes <= 0) {
            deletionScore = 0;
        } else {
            deletionScore = HelperFunctions.RoundNumber((100 - Math.min(100, Math.max(0, (float) 100 * deletions / maxAllowedMistakes))) * outOfDeletionScore * similarityFactor / 100, (byte) 2);
        }
    }

    private void SetSubstitutionScore(int substitutions, int maxAllowedMistakes, float similarityFactor) {
        outOfSubstitutionScore = maxScore * OperationTypeEnum.Substitution.Weight() * (100 - ApplicationConstants.SpeedContributionToOverallPercent) / 10000;
        if (maxAllowedMistakes <= 0) {
            substitutionScore = 0;
        } else {
            substitutionScore = HelperFunctions.RoundNumber((100 - Math.min(100, Math.max(0, (float) 100 * substitutions / maxAllowedMistakes))) * outOfSubstitutionScore * similarityFactor / 100, (byte) 2);
        }
    }

    private void SetCaseErrorScore(int caseErrors, int maxAllowedMistakes, float similarityFactor) {
        outOfCaseScore = maxScore * OperationTypeEnum.CaseErrors.Weight() * (100 - ApplicationConstants.SpeedContributionToOverallPercent) / 10000;
        if (maxAllowedMistakes <= 0) {
            caseScore = 0;
        } else {
            caseScore = HelperFunctions.RoundNumber((100 - Math.min(100, Math.max(0, (float) 100 * caseErrors / maxAllowedMistakes))) * outOfCaseScore * similarityFactor / 100, (byte) 2);
        }
    }

    private static int GetMaxMistakesAllowed(int questionWordCount) {
        return questionWordCount * ApplicationConstants.MaxAllowedMistakesPercent / 100;
    }

    @Override
    protected String GetEvaluationRemarks(QuestionClass question) {
        StringBuilder strEvaluationRemarks = new StringBuilder();
        byte avgWPM = 0;
        switch (question.DifficultyLevel()) {
            case Difficult:
                avgWPM = DifficultAvgWPM;
                break;
            case Medium:
                avgWPM = MediumAvgWPM;
                break;
            case Easy:
                avgWPM = EasyAvgWPM;
                break;
        }
        if (status == SubmissionStatusEnum.Success) {
            strEvaluationRemarks.append("TT").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%05d", timeTaken)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("IC").append(ApplicationConstants.SecondLevelSplitter).append(operationCounts.get(OperationTypeEnum.Insertion)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("DC").append(ApplicationConstants.SecondLevelSplitter).append(operationCounts.get(OperationTypeEnum.Deletion)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("SUC").append(ApplicationConstants.SecondLevelSplitter).append(operationCounts.get(OperationTypeEnum.Substitution)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("CC").append(ApplicationConstants.SecondLevelSplitter).append(operationCounts.get(OperationTypeEnum.CaseErrors)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("LN").append(ApplicationConstants.SecondLevelSplitter).append(typingSpeed).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("IS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", insertionScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfInsertionScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("DS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", deletionScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfDeletionScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("SUS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", substitutionScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfSubstitutionScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("CS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", caseScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfCaseScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("TS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", typingScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfTypingScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("AW").append(ApplicationConstants.SecondLevelSplitter).append(avgWPM).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("QWC").append(ApplicationConstants.SecondLevelSplitter).append(question.wordCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    //WC -> Case Insensistive Word Count
                    append("WC").append(ApplicationConstants.SecondLevelSplitter).append(wordCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("EWC").append(ApplicationConstants.SecondLevelSplitter).append(operationCounts.get(OperationTypeEnum.ExactMatch)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("RW").append(ApplicationConstants.SecondLevelSplitter).append(responseWordCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("TY").append(ApplicationConstants.SecondLevelSplitter).append(1).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);

        }
        for (String remark : evaluationRemarks) {
            remark = remark.replace(ApplicationConstants.FirstLevelSplitter, " ");
            remark = remark.replace(ApplicationConstants.SecondLevelSplitter, " ");
            strEvaluationRemarks.append("RM").append(ApplicationConstants.SecondLevelSplitter).append(remark).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
        }
        return strEvaluationRemarks.toString();
    }

    public EnglishTypingResponseClass(int submissionId, String submissionText) {
        super(submissionId, submissionText);
        this.operationCounts = new HashMap<>();
        for (OperationTypeEnum operation : OperationTypeEnum.values()) {
            this.operationCounts.put(operation, 0);
        }
        CRC32 crc = new CRC32();
        crc.update(submissionText.getBytes());
        hash = crc.getValue();
    }

    @Override
    protected boolean EvaluateParameters(QuestionClass question, JLanguageTool americanEnglishSpellingTool, JLanguageTool britishEnglishSpellingTool, JLanguageTool americanEnglishGrammarTool, JLanguageTool britishEnglishGrammarTool, HashMap<Character, Byte> codes) {
        WordClassCollection passageWords = question.WordLevelHash();
        if (question.Hash() != hash) {
            ComputeMistakesAndCorrectWordCount(passageWords, codes, codes.size() + 1);
            wordCount = operationCounts.get(OperationTypeEnum.CaseInsensitiveMatch);
            SetAccuracy();
        } else {
            accuracy = 100;
            wordCount = responseWordCount = question.wordCount;
            operationCounts.put(OperationTypeEnum.ExactMatch, wordCount);
            operationCounts.put(OperationTypeEnum.CaseInsensitiveMatch, wordCount);
        }
        status = SubmissionStatusEnum.Success;
        return true;
    }

    @Override
    protected boolean Score(QuestionClass question, HashMap<DifficultyLevelEnum, HashMap<SubjectiveResponseEvaluationEngine.ScoringParameters, Integer>> hashScoringParameters) {
        float similarityFactor = GetCosineSimilarity(question);
        int maxAllowedMistakes = GetMaxMistakesAllowed(question.WordLevelHash().size());
        SetInsertionScore(operationCounts.get(OperationTypeEnum.Insertion), maxAllowedMistakes, similarityFactor);
        SetDeletionScore(operationCounts.get(OperationTypeEnum.Deletion), maxAllowedMistakes, similarityFactor);
        SetSubstitutionScore(operationCounts.get(OperationTypeEnum.Substitution), maxAllowedMistakes, similarityFactor);
        SetCaseErrorScore(operationCounts.get(OperationTypeEnum.CaseErrors), maxAllowedMistakes, similarityFactor);
        SetTypingParameters(question.DifficultyLevel());
        aggregateScore = Math.max(Math.min(insertionScore + deletionScore + substitutionScore + caseScore + typingScore, maxScore), 0);
        return true;
    }

    @Override
    protected boolean StaticAnalysis(QuestionClass question, HashMap<Integer, ResponseBaseClass> hashTotalSubmissions) {
        float avgWPM = 0;
        switch (question.DifficultyLevel()) {
            case Difficult:
                avgWPM = DifficultAvgWPM;
                break;
            case Medium:
                avgWPM = MediumAvgWPM;
                break;
            case Easy:
                avgWPM = EasyAvgWPM;
                break;
        }
        if (timeTaken <= 0) {
            timeTaken = (short) (-1 * timeTaken);
            if (submissionText.length() > 0 && avgWPM > 0) {
                if (dbWordCount == 0) {
                    timeTaken = (short) ((wordCount / avgWPM) * 60d + timeTaken);
                } else if (wordCount > dbWordCount) {
                    timeTaken = (short) (((wordCount - dbWordCount) / avgWPM) * 60d + timeTaken);
                }
            }
        }
        return true;
    }

    @Override
    public void SetupBlockElements(Tokenizer sentenceTokenizer, HashMap<String, Integer> unusedWordFrequency) {
        List<String> lstParas = new ArrayList<>();

        for (String para : submissionText.split(ApplicationConstants.ParagraphSplitRegex, -1)) {
            String trimPara = para.trim();
            if (trimPara.length() > 0) {
                lstParas.add(trimPara);
            }
        }
        for (String para : lstParas) {
            List<String> lstSentences = sentenceTokenizer.tokenize(para);
            SetFrequency(lstSentences);
        }
        SetMagnitude();
    }

    private void SetFrequency(List<String> lstSentences) {
        for (String sentence : lstSentences) {
            List<String> lstWords = SplitIntoLowerCaseWords(sentence);
            for (String word : lstWords) {
                if (!wordFrequency.containsKey(word)) {
                    wordFrequency.put(word, 1);
                } else {
                    wordFrequency.put(word, wordFrequency.get(word) + 1);
                }
            }
        }
    }

    @Override
    protected boolean UpdateSubmission(QuestionClass question, Connection con) {
        boolean rValue = false;
        PreparedStatement statement = null;
        boolean isLocalConnection = false;
        try {
            int counter = 1;
            if (con == null || con.isValid(1) == false) {
                con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                isLocalConnection = true;
            }
            statement = con.prepareStatement("UPDATE EnglishSubmissionEvaluationTable SET TimeTaken=?, Score=?, WordCount=?, Remarks=?, EvaluationCompletedOn=?, EvaluationStatus=? WHERE Id=?");
            //timetaken => accuracy
            statement.setShort(counter++, accuracy);
            statement.setFloat(counter++, aggregateScore);
            //wordcount => typing speed
            statement.setInt(counter++, typingSpeed);
            statement.setString(counter++, GetEvaluationRemarks(question));
            statement.setTime(counter++, new java.sql.Time(System.currentTimeMillis()));
            statement.setInt(counter++, status.Value());
            statement.setInt(counter++, id);
            rValue = statement.executeUpdate() > 0;
            if (!rValue) {
                LoggingFunctions.InsertError("Submission Id: " + id + " could not be updated", "EnglishTypingResponseClass", "UpdateSubmission");
            }
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while updating the Submission Id: " + id + "\r\n" + e.getMessage(), "EnglishTypingResponseClass", "UpdateSubmission");
        }
        finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "EnglishTypingResponseClass", "UpdateSubmission");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing Connection" + System.lineSeparator() + e.getMessage(), "EnglishTypingResponseClass", "UpdateSubmission");
                }
            }
        }
        return rValue;
    }

}

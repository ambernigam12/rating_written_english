
package CoCubes.Helper;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Constants.SqlConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import static CoCubes.Helper.HelperFunctions.IsVowel;
import CoCubes.Language.English.EnglishLanguageResponseClass;
import CoCubes.Language.English.EnglishTypingResponseClass;
import CoCubes.Language.English.NGramClass;
import CoCubes.Language.English.ResponseBaseClass;
import CoCubes.Language.English.SubjectiveResponseEvaluationEngine.EvaluationTypeEnum;
import static CoCubes.Language.English.SubjectiveResponseEvaluationEngine.EvaluationTypeEnum.LanguageEvaluation;
import static CoCubes.Language.English.SubjectiveResponseEvaluationEngine.EvaluationTypeEnum.NoEvaluation;
import static CoCubes.Language.English.SubjectiveResponseEvaluationEngine.EvaluationTypeEnum.TypingEvaluation;
import CoCubes.Logging.LoggingFunctions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.languagetool.tokenizers.Tokenizer;

public class HelperFunctions {

    public static int TryParseInteger(String param) {
        int value;
        try {
            value = Integer.valueOf(param);
        } catch (Exception e) {
            value = 0;
        }
        return value;
    }

    public static boolean IsAlphaNumeric(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    public static boolean IsVowel(char ch) {
        return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u'
                || ch == 'A' || ch == 'E' || ch == 'I' || ch == 'O' || ch == 'U';
    }

    public static float RoundNumber(float number, byte decimalPlaces) {
        long precisionFactor = 1l;
        for (int i = 0; i < decimalPlaces; i++) {
            precisionFactor *= 10l;
        }
        return ((float) Math.round(number * precisionFactor)) / precisionFactor;
    }

    public static double RoundNumber(double number, byte decimalPlaces) {
        long precisionFactor = 1l;
        for (int i = 0; i < decimalPlaces; i++) {
            precisionFactor *= 10l;
        }
        return ((double) Math.round(number * precisionFactor)) / precisionFactor;
    }

    public static List<String> SplitTextBySeperator(String text, String separator) {
        List<String> lstParas = new ArrayList<>();
        //TODO: Why is -1 used in split?
        for (String para : text.split(separator, -1)) {
            String trimPara = para.trim();
            if (trimPara.length() > 0) {
                lstParas.add(trimPara);
            }
        }
        return lstParas;
    }

    public static List<String> SplitIntoLowerCaseWords(String text) {
        List<String> lstWords = new ArrayList<>();
        text = text.toLowerCase();
        for (String word : text.split(ApplicationConstants.WordSplitRegex)) {
            if (word.length() >= ApplicationConstants.MinCharCountPerWord) {
                lstWords.add(word);
            }
        }
        return lstWords;
    }

    public static int CountSyllables(String word) {
        int count = 0;
        boolean isPreviousVowel = false;
        for (int i = 0; i < word.length(); i++) {
            boolean isCurrentVowel = IsVowel(word.charAt(i));
            if (!isPreviousVowel && isCurrentVowel) {
                count++;
            }
            isPreviousVowel = isCurrentVowel;
        }
        return Math.max(1, count);
    }

    public static int Minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    public static HashMap<Character, Byte> GetCharCodes() {
        HashMap<Character, Byte> codes = new HashMap<>();
        byte j = 1;
        for (byte i = 0; i < 26; i++, j++) {
            codes.put((char) (i + 65), j);
            codes.put((char) (i + 97), j);
        }
        for (byte i = 0; i < 10; i++) {
            codes.put((char) (i + 48), j++);
        }
        codes.put(' ', (byte) (j++));
        codes.put('_', (byte) (j++));
        codes.put('?', (byte) (j++));
        codes.put(',', (byte) (j++));
        codes.put('!', (byte) (j++));
        codes.put('-', (byte) (j++));
        codes.put('\'', (byte) (j++));
        codes.put('"', (byte) (j++));
        codes.put('(', (byte) (j++));
        codes.put(')', (byte) (j++));
        return codes;
    }

    public static HashSet<String> FillIgnoreWordsForSimilarityEvaluation() {
        HashSet<String> hashIgnore = new HashSet();
        //hashIgnore.add("a");//hashIgnore.add("an");//hashIgnore.add("is");//hashIgnore.add("of");//hashIgnore.add("at");//hashIgnore.add("in");//hashIgnore.add("at");//hashIgnore.add("it");//hashIgnore.add("on");
        hashIgnore.add("the");
        hashIgnore.add("are");
        hashIgnore.add("was");
        hashIgnore.add("were");
        hashIgnore.add("has");
        hashIgnore.add("have");
        hashIgnore.add("had");
        hashIgnore.add("and");
        return hashIgnore;
    }

    public static EvaluationTypeEnum GetEvaluationTypeEnumFromValue(byte value) {
        EvaluationTypeEnum evaluationType = NoEvaluation;
        if (value == EvaluationTypeEnum.LanguageEvaluation.Value()) {
            evaluationType = LanguageEvaluation;
        } else if (value == EvaluationTypeEnum.TypingEvaluation.Value()) {
            evaluationType = TypingEvaluation;
        }
        return evaluationType;
    }

    public static List<Pair<Integer, Integer>> DetectSpellingChains(List<Pair<Integer, Integer>> individualMistakes) {//, List<Pair<Integer, Integer>> existingChains
        List<Pair<Integer, Integer>> chains = new ArrayList<>();
        int chainStartIndex = Integer.MIN_VALUE;
        int currentChainLength = 0;
        Pair<Integer, Integer> previousMistakeIndex = null;
        for (int i = 0; i < individualMistakes.size(); i++) {
            Pair<Integer, Integer> currentMistakeIndex = individualMistakes.get(i);
            boolean isMistakeAddedInChain = false;
            if (previousMistakeIndex != null && Math.abs(currentMistakeIndex.getKey() - previousMistakeIndex.getValue()) <= ApplicationConstants.MaxAllowedCharCountDiffBetweenTwoMistakesInChain) {
                currentChainLength++;
                if (chainStartIndex == Integer.MIN_VALUE) {
                    chainStartIndex = previousMistakeIndex.getKey();
                }
                isMistakeAddedInChain = true;
            }
            if (isMistakeAddedInChain == false || i == individualMistakes.size() - 1) {
                int endIndex = previousMistakeIndex != null && isMistakeAddedInChain == false ? previousMistakeIndex.getValue() : currentMistakeIndex.getValue();
                if (currentChainLength > ApplicationConstants.MinWordCountToQualifyForSpellingChain) {// && IsContained(chainStartIndex, endIndex, existingChains) == false -> removed as it is redundant
                    chains.add(new Pair<>(chainStartIndex, endIndex));
                }
                currentChainLength = 0;
                chainStartIndex = Integer.MIN_VALUE;
            }
            previousMistakeIndex = currentMistakeIndex;
        }
        return chains;
    }

    private static String lcp(String s, String t) {
        int n = Math.min(s.length(), t.length());
        for (int i = 0; i < n; i++) {
            if (s.charAt(i) != t.charAt(i)) {
                return s.substring(0, i);
            }
        }
        return s.substring(0, n);
    }

    private static String LongestValidRepeatingString(String s) {
        int n = s.length();
        String[] suffixes = new String[n];
        for (int i = 0; i < n; i++) {
            suffixes[i] = s.substring(i, n);
        }
        Arrays.sort(suffixes);
        String lrs = "";
        for (int i = 0; i < n - 1; i++) {
            String x = lcp(suffixes[i], suffixes[i + 1]);
            if (x.length() > lrs.length()) {
                lrs = x;
            }
        }
        return lrs.length() > ApplicationConstants.MinCharCountLongestRepeatingString ? lrs : "";
    }

    public static List<Pair<Integer, Integer>> DetectRepetitiveChains(String submission) {
        List<Pair<Integer, Integer>> chains = new ArrayList<>();
        String pristineSubmission = submission = submission.toLowerCase();
        String repeatedString = LongestValidRepeatingString(submission);
        while (repeatedString.length() > ApplicationConstants.MinCharCountLongestRepeatingString) {
            boolean isFirstUsage = true;
            int currentIndex = pristineSubmission.indexOf(repeatedString);
            while (currentIndex >= 0) {
                if (isFirstUsage == false) {
                    if (currentIndex >= 0 && IsOverlap(currentIndex, currentIndex + repeatedString.length(), chains) == false) {
                        chains.add(new Pair<>(currentIndex, currentIndex + repeatedString.length() - 1));
                    }
                } else {
                    isFirstUsage = false;
                }
                currentIndex = pristineSubmission.indexOf(repeatedString, currentIndex + 1);//repeatedString.length());
            }
            submission = StringUtils.replaceOnce(submission, repeatedString, "");
            repeatedString = LongestValidRepeatingString(submission);
        }
        return chains;
    }

    public static boolean IsOverlap(int lowerIndex, int higherIndex, List<Pair<Integer, Integer>> repetitiveChain) {
        boolean rValue = false;
        for (Pair<Integer, Integer> element : repetitiveChain) {
            if (!((element.getKey() < lowerIndex && element.getValue() < lowerIndex) || (element.getKey() > higherIndex && element.getValue() > higherIndex))) {
                rValue = true;
                break;
            }
        }
        return rValue;
    }

    public static int ChainsAccountedForWordCount(int wordCount, List<Pair<Integer, Integer>> repetitiveChain, int id, String submission) {
        for (Pair<Integer, Integer> element : repetitiveChain) {
            wordCount -= SplitIntoLowerCaseWords(submission.substring(element.getKey(), element.getValue() + 1)).size();//(element.getValue() - element.getKey()) / ApplicationConstants.AverageWordLength;
        }
        if (wordCount <= 0) {
            LoggingFunctions.InsertError("Word Count <= 0 after accounting for chains in the Submission Id: " + id, "HelperFunctions", "RepetitiveChainsAccountedForWordCount");
            wordCount = 0;
        }
        return wordCount;
    }

    public static boolean tryParseFloat(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String readFile(String path) {
        boolean rValue = false;
        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(Paths.get(path));
            rValue = true;
        } catch (IOException ex) {
            LoggingFunctions.InsertError(ex.getMessage() + System.lineSeparator(), "SubjectiveResponseEvaluationEngine", "readFile");
        }
        return rValue ? new String(encoded, StandardCharsets.US_ASCII) : null;
    }

    private static List<String> ngrams(int n, String str) {
        List<String> ngrams = new ArrayList<>();
        String[] words = SplitIntoLowerCaseWords(str).toArray(new String[0]);
        for (int i = 0; i < words.length - n + 1; i++) {
            ngrams.add(concat(words, i, i + n));
        }
        return ngrams;
    }

    private static String concat(String[] words, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append((i > start ? " " : "")).append(words[i]);
        }
        return sb.toString();
    }

    private static final int MaxNGrams = 6;

    public static HashMap<Integer, NGramClass> getNGrams(Tokenizer blockTokenizer, String submission) {
        HashMap<Integer, NGramClass> ngrams = new HashMap<>();
        for (int n = 0; n < MaxNGrams; n++) {
            HashMap<String, Integer> individualNgram = new HashMap<>();
            for (String ngram : ngrams(n + 1, submission.toLowerCase())) {
                if (individualNgram.containsKey(ngram) == false) {
                    individualNgram.put(ngram, 1);
                } else {
                    individualNgram.put(ngram, individualNgram.get(ngram) + 1);
                }
            }
            NGramClass ngramObj = new NGramClass();
            ngramObj.SetupBlockElements(blockTokenizer, individualNgram);
            ngrams.put(n, ngramObj);
        }
        return ngrams;
    }

    public static List<EnglishLanguageResponseClass> getToppersForQuestion(int questionId, Connection con, float maxScore, Tokenizer blockTokenizer) {
        List<EnglishLanguageResponseClass> toppers = new ArrayList<>();
        boolean isLocalConnection = false;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            if (con == null || con.isValid(1) == false) {
                con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
                isLocalConnection = true;
            }
            statement = con.prepareStatement("SELECT Id, SubjectiveAnswer, QuestionId, MaxScore, TimeTaken, WordCount, EvaluationType FROM EnglishSubmissionEvaluationTable WITH(NOLOCK) WHERE EvaluationStatus=? and score>=?");
            int counter = 1;
            statement.setInt(counter++, 1);
            statement.setFloat(counter++, ApplicationConstants.MinTopperScoreForContextAnalysis * maxScore);
            rs = statement.executeQuery();
            while (rs.next()) {
                EnglishLanguageResponseClass submission = ((EnglishLanguageResponseClass) GetSubmission(rs));
                if (submission != null) {
                    submission.ngrams = HelperFunctions.getNGrams(blockTokenizer, submission.getSubmissionText());
                    toppers.add(submission);
                }
            }
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while fetching submissions" + System.lineSeparator() + e.getMessage(), "HelperFunctions", "getToppersForQuestion");
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (Exception e) {
                LoggingFunctions.InsertError("Error occurred while closing ResultSet" + System.lineSeparator() + e.getMessage(), "HelperFunctions", "getToppersForQuestion");
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "HelperFunctions", "getToppersForQuestion");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing Connection" + System.lineSeparator() + e.getMessage(), "HelperFunctions", "getToppersForQuestion");
                }
            }
        }
        return toppers;
    }

    public static ResponseBaseClass GetSubmission(ResultSet rs) {
        ResponseBaseClass submission = null;
        int submissionId = -1;
        EvaluationTypeEnum evaluationType;
        String submissionText;

        try {
            submissionId = rs.getInt("Id");
            evaluationType = GetEvaluationTypeEnumFromValue(rs.getByte("EvaluationType"));
            submissionText = rs.getString("SubjectiveAnswer");
            switch (evaluationType) {
                case LanguageEvaluation:
                    submission = new EnglishLanguageResponseClass(submissionId, submissionText);
                    break;
                case TypingEvaluation:
                    submission = new EnglishTypingResponseClass(submissionId, submissionText);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while reading Submission Id: " + submissionId + System.lineSeparator() + e.getMessage(), "SubjectiveResponseEvaluationEngine", "GetSubmission");
        }
        return submission;
    }

    public static List<String> GetPhraseListFromFile(String filePath, boolean isSpaceAccepted) {
        String text = HelperFunctions.readFile(filePath);
        List<String> lstIgnoreWords = null;
        if (text != null) {
            lstIgnoreWords = new ArrayList<>();
            text = text.trim();
            if (text.length() > 0) {
                for (String word : text.split(System.lineSeparator())) {
                    if (word.trim().length() > 0) {
                        if (word.contains(" ") && isSpaceAccepted == false) {
                            lstIgnoreWords = null;
                            LoggingFunctions.InsertError(("Invalid word in Ignore Word List File!" + System.lineSeparator()), "SubjectiveResponseEvaluationEngine", "GetPhraseListFromFile");
                            break;
                        }
                        lstIgnoreWords.add(word);
                    }
                }
            }
        }
        return lstIgnoreWords;
    }
    public static boolean writeIntoFile(String content, String filePath, boolean isAppend) {
        boolean rValue = false;
        try {
            content += System.lineSeparator();
            File file = new File(filePath);
            file.createNewFile();
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), isAppend);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();
            fw.close();
            rValue = true;
        } catch (Exception e) {
            System.out.println(e.getMessage() + " : " + content.trim());
        }
        return rValue;
    }

    public static String ReadEvalStringAndConvertToCSV(String line) {
        StringBuilder writer = new StringBuilder();
        String[] str1 = line.split("\\|");
        String str3 = "";
        for (int j = 0; j < str1.length - 1; j++) {
            String[] str2 = str1[j].split("\\$");

            for (int k = 1; k < str2.length; k++) {
                //todo: pick from constants
                if (str2[0].equals("EC") || str2[0].equals("EP") || str2[0].equals("PS") || str2[0].equals("PI") || str2[0].equals("RC") || str2[0].equals("SCN")) {
                    str3 += ", " + str2[0] + "_" + str2[k];
                } else {
                    writer.append(str2[k]).append(", ");
                }
            }
        }
        String[] str2 = str1[str1.length - 1].split("\\$");

        for (int k = 1; k < str2.length - 1; k++) {
            writer.append(str2[k]).append(", ");
        }
        writer.append(str2[str2.length - 1]);
        writer.append(str3);
        writer.append(System.getProperty("line.separator"));
        return writer.toString();
    }

}

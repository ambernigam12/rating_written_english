
package CoCubes.Logging;

import CoCubes.Constants.EmailConstants;
import CoCubes.Constants.SqlConstants;
import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Language.English.EnglishLanguageResponseClass;
import CoCubes.Language.English.EnglishTypingResponseClass;
import CoCubes.Language.English.ResponseBaseClass;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

public class LoggingFunctions {

    private static final String ErrorLogFile = "log\\EnglishEvaluation_ErrorLog.csv";
    private static final String LogFile = "log\\EnglishEvaluation.log";
    public static final String TrackerLogFile = "log\\EnglishEvaluation.slg";
    private static int totalEnglishLanguageSubmissionCount = 0;
    private static int totalTypingSubmissionCount = 0;
    private static int englishLanguageValidSubmissionCount = 0;
    private static int typingValidSubmissionCount = 0;
    private static int englishLanguageInvalidSubmissionCount = 0;
    private static int typingInvalidSubmissionCount = 0;
    private static float maxScoreEnglish = Integer.MIN_VALUE;
    private static float minScoreEnglish = Integer.MAX_VALUE;
    private static float maxScoreTyping = Integer.MIN_VALUE;
    private static float minScoreTyping = Integer.MAX_VALUE;
    private static int duplicateParaCases = 0;
    private static int scoreNormalizationCases = 0;

    public static boolean InsertLog(String content) {
        boolean rValue = false;
        try {
            content += System.lineSeparator();
            File file = new File(LogFile);
            file.createNewFile();
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();
            fw.close();
            rValue = true;
        } catch (IOException ex) {
            File logDirectory = new File(LogFile).getParentFile();
            if (!logDirectory.exists()) {
                logDirectory.mkdir();
            }
        } catch (Exception e) {
            InsertError(content.trim() + "$$$" + e.getMessage(), "LoggingFunctions", "InsertLog");
        }
        return rValue;
    }

    public static boolean InsertFinalLog(HashMap<Integer, ResponseBaseClass> hashSubmissions) {
        ReinitializeStats();
        SetStats(hashSubmissions);
        return InsertLog(englishLanguageValidSubmissionCount + " English and " + typingValidSubmissionCount + " Typing submissions evaluated." + System.lineSeparator() + "End: " + new Date());
    }

    private static void ReinitializeStats() {
        totalEnglishLanguageSubmissionCount = 0;
        totalTypingSubmissionCount = 0;
        englishLanguageValidSubmissionCount = 0;
        typingValidSubmissionCount = 0;
        englishLanguageInvalidSubmissionCount = 0;
        typingInvalidSubmissionCount = 0;
        maxScoreEnglish = Integer.MIN_VALUE;
        minScoreEnglish = Integer.MAX_VALUE;
        maxScoreTyping = Integer.MIN_VALUE;
        minScoreTyping = Integer.MAX_VALUE;
        duplicateParaCases = 0;
        scoreNormalizationCases = 0;
    }

    public static boolean InsertIntermediateLog(int ignoreWordsCount, HashMap<Integer, ResponseBaseClass> hashSubmissions) {
        SetStats(hashSubmissions);
        return InsertLog(englishLanguageValidSubmissionCount + " English and " + typingValidSubmissionCount + " Typing submissions evaluated" + ", keywords:" + ignoreWordsCount + ", at: " + new Date());
    }

    public static boolean InsertError(String content, String className, String functionName) {
        boolean rValue = false;
        try {
            File file = new File(ErrorLogFile);
            file.createNewFile();
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            String log = "\"" + content + "\"" + "," + functionName + "," + className + "," + new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()) + System.lineSeparator();
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(log);
            bw.close();
            fw.close();
            rValue = true;
        } catch (IOException ex) {
            File logDirectory = new File(ErrorLogFile).getParentFile();
            if (!logDirectory.exists()) {
                logDirectory.mkdir();
            }
        } catch (Exception e) {
            //Suppress exception
        }
        return rValue;
    }

    public static boolean InsertTrackerLog() {
        boolean rValue = false;
        try {
            File file = new File(TrackerLogFile);
            file.createNewFile();
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), false);
            BufferedWriter bw = new BufferedWriter(fw);
            //String.valueOf so that it does not write char value of integer
            bw.write(String.format("%-15d", englishLanguageValidSubmissionCount + typingValidSubmissionCount));
            bw.close();
            fw.close();
            rValue = true;
        } catch (IOException ex) {
            //Exception is suppressed
            File logDirectory = new File(TrackerLogFile).getParentFile();
            if (!logDirectory.exists()) {
                logDirectory.mkdir();
            }
        } catch (Exception e) {
            InsertError("Exception occurred while modifying Tracker File." + "$$$" + e.getMessage(), "LoggingFunctions", "InsertTrackerLog");
        }
        return rValue;
    }

    private static void SetStats(HashMap<Integer, ResponseBaseClass> hashSubmissions) {
        for (ResponseBaseClass submission : hashSubmissions.values()) {
            if (submission instanceof EnglishLanguageResponseClass) {
                if (submission.Status() == EnglishLanguageResponseClass.SubmissionStatusEnum.Success) {
                    englishLanguageValidSubmissionCount++;
                    if (submission.IsDuplicateParaFound()) {
                        duplicateParaCases++;
                    }
                    if (submission.IsScoreNormalizationRequired()) {
                        scoreNormalizationCases++;
                    }
                    if (submission.AggregateScore() > maxScoreEnglish) {
                        maxScoreEnglish = submission.AggregateScore();
                    }
                    if (submission.AggregateScore() < minScoreEnglish) {
                        minScoreEnglish = submission.AggregateScore();
                    }
                } else if (submission.Status() == EnglishLanguageResponseClass.SubmissionStatusEnum.Failed) {
                    englishLanguageInvalidSubmissionCount++;
                }
                totalEnglishLanguageSubmissionCount++;
            } else if (submission instanceof EnglishTypingResponseClass) {
                if (submission.Status() == EnglishLanguageResponseClass.SubmissionStatusEnum.Success) {
                    typingValidSubmissionCount++;
                    if (submission.AggregateScore() > maxScoreTyping) {
                        maxScoreTyping = submission.AggregateScore();
                    }
                    if (submission.AggregateScore() < minScoreTyping) {
                        minScoreTyping = submission.AggregateScore();
                    }
                } else if (submission.Status() == EnglishLanguageResponseClass.SubmissionStatusEnum.Failed) {
                    typingInvalidSubmissionCount++;
                }
                totalTypingSubmissionCount++;
            }
        }
    }

}

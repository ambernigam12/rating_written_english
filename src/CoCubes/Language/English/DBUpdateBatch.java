
package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Constants.SqlConstants;
import CoCubes.Helper.HelperFunctions;
import CoCubes.Logging.LoggingFunctions;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 *
 * @author Awesome Geeks
 */
public class DBUpdateBatch {

    public static boolean UpdateDifficultyLevel(int nextDifficultyLevel, int numberOfRowsToEvaluate) {
        StringBuilder printText = new StringBuilder();
        Connection con = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        boolean rValue = false;
        try {

            con = DriverManager.getConnection(SqlConstants.MsSqlConnectionString, SqlConstants.SqlUserName, SqlConstants.SqlPassword);
            int counter = 1;
            statement = con.prepareStatement("select id, WordCount, Score, Remarks from EnglishSubmissionEvaluationTable WITH(NOLOCK) WHERE (EvaluationStatus=? or EvaluationStatus=?) and id<=?");
            statement.setByte(counter++, ResponseBaseClass.SubmissionStatusEnum.Success.Value());
            statement.setByte(counter++, ResponseBaseClass.SubmissionStatusEnum.Failed.Value());
            statement.setInt(counter++, numberOfRowsToEvaluate);
            rs = statement.executeQuery();
            while (rs.next()) {
                printText.append(rs.getInt(1)).append(",").append(rs.getInt(2)).append(",").append(rs.getDouble(3)).append(",").append(rs.getString(4));
                printText.append(System.lineSeparator());
            }
            boolean update = true;
            if (nextDifficultyLevel > 1)
                update = WriteFile(printText.toString(), String.format("%d_%d.csv", nextDifficultyLevel - 2, System.currentTimeMillis()));
            if (update && nextDifficultyLevel < 7) {
                statement = con.prepareStatement("update EnglishSubmissionEvaluationTable set EvaluationStatus=? where id <= ?");
                counter = 1;
                statement.setByte(counter++, ResponseBaseClass.SubmissionStatusEnum.NotPicked.Value());
                statement.setInt(counter++, numberOfRowsToEvaluate);
                rValue = statement.executeUpdate() > 0;
                if (rValue) {
                    counter = 1;
                    statement = con.prepareStatement("update ObjectiveQBT set difficultyLevel=?");
                    statement.setInt(counter++, nextDifficultyLevel);
                    rValue = statement.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
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

    private static boolean WriteFile(String text, String fileName) {
        boolean rValue = false;
        try (PrintWriter out = new PrintWriter(fileName)) {
            out.println(text);
            rValue = true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return rValue;
    }

}

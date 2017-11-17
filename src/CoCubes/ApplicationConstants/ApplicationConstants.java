
package CoCubes.ApplicationConstants;

public class ApplicationConstants {

    public static final int ApplicationId = 127;
    public static final int MinCharCountPerWord = 1;
    public static final int MinWordLimit = 140;
    public static final float MinAcceptableSimilarity = .85f;
    public static final int MinUniqueLength = 10;
    public static final int MinUniqueLengthTyping = 10;
    public static final byte SubmissionToFetch = 10;
    public static final String CustomRulesFile = "rules\\custom_grammar_rules.xml";
    public static final String CustomSpellingFile = "spellings\\ignore.txt";
    public static final String AcceptPhraseFile = "phrases\\ignore.txt";
    public static final int MinLengthWordForSimilarityEvaluation = 3;
    public static final int[] IndexEquivalents = {97, 89, 83, 79, 73, 71, 67, 61, 59, 53, 47, 43, 41, 37, 31, 29, 23, 19, 17, 13, 11, 7, 5, 3};
    //such large value to avoid conflicts which are very much possible due to n^2 comparisons
    public static final int DiffBetweenLowerAndUpperCaseAlphabets = 29;
    public static int SpeedContributionToOverallPercent = 30;
    public static byte MaxAllowedMistakesPercent = 50;
    public static byte MinPercentScoreAboveMedium = 70;
    public static byte MinPercentScoreAboveLow = 30;
    public static byte StopHour = 23;
    public static byte StopMinute = 55;
    public static byte UpperCapCaseErrorsPerWord = 5;
    public static int[] GrammarSpellingPercentCut = {0, 5, 12, 22, 37, 55, 75, 100};
    //public static int[] GrammarSpellingPercentCutMedium = {0, 7, 18, 25, 45, 65, 85, 100};
    //public static int[] GrammarSpellingPercentCutDifficult = {0, 10, 20, 40, 60, 85, 100};
    //0 7 18 25 45 65 85 100
    //0 10 20 40 60 85 100
    public static float CutOffPercentForReEvaluation = .75f;
    public static final float MinVocabulary = 1;
    public static final float MaxOptimalLexicalDensity = 65;
    public static final float Epsilon = .001f;
    public static final float OptimalReadability = 12;
    public static final float MinAcceptableReadability = 2.0f;
    //public static final float NonUniqueErrorWeight = .33f;
    public static final String FirstLevelSplitter = "|";
    public static final String SecondLevelSplitter = "$";
    public static String WordSplitRegex = "[\r\n\\s.?!,;&\"]+";
    public static final String ParagraphSplitRegex = "[\r\n]+";
    public static int AverageWordLength = 5;
    public static float[] MistakeIndexer = {0f, 1f, 1.8f, 2.4f, 2.8f, 3f};
    public static int IntermediateStates = 1;
    public static final float MinLexicalDensityThresholdForHundredPercentEval = .25f;
    public static final float MinRedabilityThresholdForHundredPercentEval = 4.f;

    public static final int MinWordCountToQualifyForChain = 5;
    public static final int MaxAllowedCharCountDiffBetweenTwoMistakesInChain = 7;
    public static final int MinCharCountLongestRepeatingString = 70;
    public static final int MinWordCountToQualifyForSpellingChain = 80;
    public static final float WordCountBufferForLengthScoring = .8f;
    public static final float MinTopperScoreForContextAnalysis = .8f;
    public static final int[] LexicalDensityFivePercentPenalizerForOverallScore = {0, 10, 20, 35, 50, 80, 100};
    public static final int[] ReadabilityUnitPenalizerForOverallScore = {0, 20, 50, 75, 100};
    public static final String FeatureFileName = "features.csv";

}

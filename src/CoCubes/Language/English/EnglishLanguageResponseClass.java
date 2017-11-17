
package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import CoCubes.Constants.SqlConstants;
import CoCubes.Helper.HelperFunctions;
import static CoCubes.Helper.HelperFunctions.DetectRepetitiveChains;
import CoCubes.Logging.LoggingFunctions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.languagetool.tokenizers.Tokenizer;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import CoCubes.Language.English.QuestionClass.DifficultyLevelEnum;
import CoCubes.Language.English.SubjectiveResponseEvaluationEngine.ScoringParameters;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.util.Pair;
import org.languagetool.JLanguageTool;
import org.languagetool.rules.Category;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;

public class EnglishLanguageResponseClass extends ResponseBaseClass {

    final private int SpellingKey = 1;
    final private int GrammarKey = 2;
    final private int LDKey = 3;
    final private int ReadabilityKey = 4;
    final private float MinRequiredAllAlphabetWordPercent = .5f;
    private float evalPercent = 1.f;
    protected float vocabulary;
    protected float readability;
    protected int keywordMatchCount;
    protected int grammarError;
    protected int spellingError;
    protected int uniqueGrammarError;
    protected int uniqueSpellingError;
    protected float vocabularyScore;
    protected float readabilityScore;
    protected float grammarScore;
    protected float spellingScore;
    protected float lengthScore;
    protected float keywordScore;
    protected float outOfVocabularyScore;
    protected float outOfReadabilityScore;
    protected float outOfGrammarScore;
    protected float outOfSpellingScore;
    protected float outOfLengthScore;
    protected float outOfKeywordScore;
    protected LanguageEnum language;
    protected boolean isPlagiarismCase;
    protected int cheatedWithSubmissionId;
    protected float plagiarismIndex;
    protected float normalizedGrammarError;
    protected float normalizedSpellingError;
    protected float scaledReadability;
    private int evaluationCriteria = 0;
    private boolean isWordCountCriterionNotMeeting;
    private List<Pair<Integer, Integer>> spellMistakes;
    private List<Pair<Integer, Integer>> grammarMistakes;
    public HashMap<Integer, NGramClass> ngrams;
    public LinkedHashMap<String, Integer> grammarErrorBucketing;
    //todo: move locally if possible
    private List<Pair<Integer, Integer>> spellingChain;
    private List<Pair<Integer, Integer>> repetitiveChain;

    private static String GetSanitizedText(String text) {
        StringBuilder sbSanitizedText = new StringBuilder();
        if (text != null && text.length() > 0) {
            boolean isLowerCase = false;
            int previousIndex = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == ' ' || text.charAt(i) == '?' || text.charAt(i) == '.' || text.charAt(i) == '!' || i == text.length() - 1) {
                    sbSanitizedText.append(text.charAt(previousIndex));
                    if (i > previousIndex) {
                        sbSanitizedText.append(isLowerCase ? text.substring(previousIndex + 1, i + 1) : text.substring(previousIndex + 1, i + 1).toLowerCase());
                    }
                    previousIndex = i + 1;
                    isLowerCase = false;
                } else if (text.charAt(i) >= 97 && text.charAt(i) <= 122) {
                    isLowerCase = true;
                }
            }
        }
        return sbSanitizedText.toString();
    }

    private boolean SetSpellingMistakeCount(JLanguageTool americanEnglishTool, JLanguageTool britishEnglishTool) {
        boolean rValue = false;
        spellingError = 0;
        if (wordCount < ApplicationConstants.MinWordLimit) {
            isWordCountCriterionNotMeeting = true;
            evaluationRemarks.add("Submission is too short to evaluate, minimum words required " + ApplicationConstants.MinWordLimit);
        } else {
            try {
                spellMistakes = new ArrayList<>();
                HashMap<String, Integer> lstSpellingMistakesFoundCommonAndUnique = new HashMap<>();
                HashSet<String> lstSpellingMistakesFoundBritishEnglish = new HashSet<>();
                String text = GetSanitizedText(submissionText);
                List<RuleMatch> matches = britishEnglishTool.check(text);
                for (RuleMatch match : matches) {
                    String rule = match.getShortMessage();
                    if (rule == null) {
                        rule = match.getMessage();
                    }
                    if (rule != null && match.getToPos() - match.getFromPos() > 0 && HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), repetitiveChain) == false) {
                        String word = text.substring(match.getFromPos(), match.getToPos());
                        if (!lstSpellingMistakesFoundBritishEnglish.contains(word)) {
                            lstSpellingMistakesFoundBritishEnglish.add(word);
                        } else {
                            lstSpellingMistakesFoundBritishEnglish.add(word);
                        }
                    }
                }
                matches = americanEnglishTool.check(text);
                boolean languageSet = false;
                for (RuleMatch match : matches) {
                    String rule = match.getShortMessage();
                    if (rule == null) {
                        rule = match.getMessage();
                    }
                    if (rule != null && match.getToPos() - match.getFromPos() > 0 && HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), repetitiveChain) == false //&& HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), spellMistakes) == false
                            ) {
                        String word = text.substring(match.getFromPos(), match.getToPos());
                        if (lstSpellingMistakesFoundBritishEnglish.contains(word)) {
                            //System.out.println(rule + "\t" + submissionText.substring(match.getFromPos(), match.getToPos() + 1));

                            spellingError++;
                            spellMistakes.add(new Pair<>(match.getFromPos(), match.getToPos() - 1));
                            if (!lstSpellingMistakesFoundCommonAndUnique.containsKey(word)) {
                                lstSpellingMistakesFoundCommonAndUnique.put(word, 1);
                            } else {
                                lstSpellingMistakesFoundCommonAndUnique.put(word, lstSpellingMistakesFoundCommonAndUnique.get(word) + 1);
                            }
                        } else if (!languageSet) {
                            //added since words like "decrement" -> "decrements" is considered as a spell error in en-US
                            List<String> suggestions = match.getSuggestedReplacements();
                            if (suggestions == null || suggestions.isEmpty() || !suggestions.contains(word + "s")) {
                                language = LanguageEnum.BritishEnglish;
                                languageSet = true;
                            }
                        }
                    }
                }
                for (String word : lstSpellingMistakesFoundCommonAndUnique.keySet()) {
                    int currentMistakeFrequency = lstSpellingMistakesFoundCommonAndUnique.get(word);
                    normalizedSpellingError += currentMistakeFrequency < ApplicationConstants.MistakeIndexer.length ? ApplicationConstants.MistakeIndexer[currentMistakeFrequency] : ApplicationConstants.MistakeIndexer[ApplicationConstants.MistakeIndexer.length - 1];
                }
                uniqueSpellingError = lstSpellingMistakesFoundCommonAndUnique.size();
                spellingChain = HelperFunctions.DetectSpellingChains(spellMistakes);
                wordCount = HelperFunctions.ChainsAccountedForWordCount(wordCount, spellingChain, id, text);
                rValue = true;
            } catch (Exception ex) {
                LoggingFunctions.InsertError("Error while processing the Submission Id: " + id + System.lineSeparator() + ex.getMessage(), "EnglishLanguageResponseClass", "SetSpellingMistakeCount");
            }
        }
        return rValue;
    }

    private boolean SetGrammarMistakeCount(JLanguageTool americanEnglishTool) {
        boolean rValue = false;
        grammarError = 0;
        if (wordCount < ApplicationConstants.MinWordLimit) {
            isWordCountCriterionNotMeeting = true;
            evaluationRemarks.add("Submission is too short to evaluate, minimum words required " + ApplicationConstants.MinWordLimit);
        } else {
            try {
                grammarMistakes = new ArrayList<>();
                List<RuleMatch> matches = americanEnglishTool.check(GetConditionedSubmissionText(submissionText));
                HashMap<String, Float> lstGrammarRulesFound = new HashMap<>();
                for (RuleMatch match : matches) {
                    //System.out.println(match.getRule().getCategory());
                    String ruleName = match.getShortMessage();
                    if (ruleName == null) {
                        ruleName = match.getMessage();
                    }
                    //assumption is name won't be null
                    if (ruleName != null && HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), repetitiveChain) == false
                            && HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), spellingChain) == false //&& HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), grammarMistakes) == false 
                            //&& HelperFunctions.IsOverlap(match.getFromPos(), match.getToPos(), spellMistakes) == false
                            ) {
                        float mistakeWeight = 1.f;
                        Rule currentRule = match.getRule();
                        if (currentRule != null) {
                            Category category = currentRule.getCategory();
                            if (category != null && category.getName() != null) {
                                //System.out.println(category.getName() + "$$$" + currentRule.getLocQualityIssueType().name() + "$$$" + currentRule.getDescription() + "$$$" + ruleName + "$$$" + submissionText.substring(match.getFromPos(), match.getToPos()));
                                String mistakeType = category.getName();// + "$$$" + currentRule.getLocQualityIssueType().name();
                                if (grammarErrorBucketing.containsKey(mistakeType)) {
                                    grammarErrorBucketing.put(mistakeType, grammarErrorBucketing.get(mistakeType) + 1);
                                } else {
                                    grammarErrorBucketing.put(DefaultGrammarString, grammarErrorBucketing.get(DefaultGrammarString) + 1);
                                    System.out.println("Other grammar error found is: " + mistakeType);
                                }
                                switch (category.getName().trim().toLowerCase()) {
                                    case "miscellaneous":
                                        mistakeWeight = .7f;
                                        break;
                                }
                            }
                        }
                        if (!lstGrammarRulesFound.containsKey(ruleName)) {
                            lstGrammarRulesFound.put(ruleName, mistakeWeight);
                        } else {
                            lstGrammarRulesFound.put(ruleName, lstGrammarRulesFound.get(ruleName) + mistakeWeight);
                        }
                        grammarError++;
                        grammarMistakes.add(new Pair<>(match.getFromPos(), match.getToPos() - 1));
                    }
                }
                for (String word : lstGrammarRulesFound.keySet()) {
                    int currentMistakeFrequency = (int) Math.round((lstGrammarRulesFound.get(word)));
                    normalizedGrammarError += currentMistakeFrequency < ApplicationConstants.MistakeIndexer.length ? ApplicationConstants.MistakeIndexer[currentMistakeFrequency] : ApplicationConstants.MistakeIndexer[ApplicationConstants.MistakeIndexer.length - 1];
                }
                uniqueGrammarError = lstGrammarRulesFound.size();
                rValue = true;
            } catch (Exception ex) {
                LoggingFunctions.InsertError("Error while processing the Submission Id: " + id + System.lineSeparator() + ex.getMessage(), "EnglishLanguageResponseClass", "SetGrammarMistakeCount");
            }
        }
        return rValue;
    }

    private String GetConditionedSubmissionText(String submissionText) {
        StringBuilder conditionedSubmissionText = new StringBuilder(submissionText.replaceAll("([^\\s])\"", "$1" + (char) 0x201D).replaceAll("\"([^\\s])", Character.toString((char) 0x201C) + "$1"));
        boolean isPreviousQuoteStart = false;
        for (int index = 0; index < conditionedSubmissionText.length(); index++) {
            if (conditionedSubmissionText.charAt(index) == ((char) 0x201C)) {
                isPreviousQuoteStart = true;
            } else if (conditionedSubmissionText.charAt(index) == ((char) 0x201D)) {
                isPreviousQuoteStart = false;
            } else if (conditionedSubmissionText.charAt(index) == '"') {
                conditionedSubmissionText.setCharAt(index, isPreviousQuoteStart ? (char) 0x201D : (char) 0x201C);
                isPreviousQuoteStart = !isPreviousQuoteStart;
            }
        }
        return conditionedSubmissionText.toString();
    }

    private void SetGrammarScore(float weight, float percentOfEvaluation, boolean isReEvaluate, QuestionClass question) {
        float percent = 0;
        float difficultyLevelMistakeFactor = 0;
        int reEvaluateScore = 0;
        if (grammarError > 0) {
            if (isReEvaluate == false) {
                difficultyLevelMistakeFactor = question.GetDifficultyLevelMistakeFactor();
            } else {
                reEvaluateScore = 1;
            }
        }
        normalizedGrammarError += reEvaluateScore + difficultyLevelMistakeFactor;
        int normalizedGrammarErrorIndex = (int) Math.round(normalizedGrammarError);
        if (normalizedGrammarErrorIndex < ApplicationConstants.GrammarSpellingPercentCut.length) {
            percent = 100 - ApplicationConstants.GrammarSpellingPercentCut[normalizedGrammarErrorIndex];
        }
        outOfGrammarScore = maxScore * weight / 100f;
        grammarScore = HelperFunctions.RoundNumber(percentOfEvaluation * percent * outOfGrammarScore / 100, (byte) 2);
    }

    private void SetSpellingScore(float weight, float percentOfEvaluation, boolean isReEvaluate, QuestionClass question) {
        float percent = 0;
        float difficultyLevelMistakeFactor = 0;
        int reEvaluateScore = 0;
        if (spellingError > 0) {
            if (isReEvaluate == false) {
                difficultyLevelMistakeFactor = question.GetDifficultyLevelMistakeFactor();
            } else {
                reEvaluateScore = 1;
            }
        }
        normalizedSpellingError += reEvaluateScore + difficultyLevelMistakeFactor;
        int normalizedSpellingErrorIndex = (int) Math.floor(normalizedSpellingError);
        if (normalizedSpellingErrorIndex < ApplicationConstants.GrammarSpellingPercentCut.length) {
            percent = 100 - ApplicationConstants.GrammarSpellingPercentCut[normalizedSpellingErrorIndex];
        }
        outOfSpellingScore = maxScore * weight / 100f;
        spellingScore = HelperFunctions.RoundNumber(percentOfEvaluation * percent * outOfSpellingScore / 100, (byte) 2);
    }

    private void SetVocabScore(float weight, float percentOfEvaluation) {
        float percent = 0;
        final float c1 = 100;
        vocabulary = ((float) uniqueWordCount) / wordCount;
        if (vocabulary >= ApplicationConstants.MinVocabulary / 100) {
            percent = Math.max(0, 100 - (Math.abs(GetOptimalVocab(wordCount) - vocabulary * c1)));
        }
        outOfVocabularyScore = maxScore * weight / 100f;
        vocabularyScore = HelperFunctions.RoundNumber(percentOfEvaluation * percent * outOfVocabularyScore / 100, (byte) 2);
    }

    private float GetOptimalVocab(int length) {
        final float c1 = 100f;
        return Math.max(ApplicationConstants.MinVocabulary, ApplicationConstants.MaxOptimalLexicalDensity - (length / c1));
    }

    private void SetReadabilityScore(float weight, float percentOfEvaluation, DifficultyLevelEnum difficultyLevel) {
        final int EasyLevelShiftReadability = 0;
        final int MediumLevelShiftReadability = 1;
        final int DifficultLevelShiftReadability = 2;
        final int GradePitStop1 = 3;
        final int GradePitStop2 = 9;
        final int GradePitStop3 = 12;
        final float PercentPitStop1 = 0;
        final float PercentPitStop2 = 40;
        final float PercentPitStop3 = 100;
        final float readabiltyImpedanceFactor = 5;
        float percent = 0;
        if (sentenceCount > 0 && wordCount > 0) {
            readability = (float) Math.max(0, .39 * wordCount / sentenceCount + 11.8 * syllableCount / wordCount - 15.59);
        }
        int difficultyLevelFactor = 0;
        switch (difficultyLevel) {
            case Easy:
                difficultyLevelFactor = EasyLevelShiftReadability;
                break;
            case Medium:
                difficultyLevelFactor = MediumLevelShiftReadability;
                break;
            case Difficult:
                difficultyLevelFactor = DifficultLevelShiftReadability;
                break;
        }
        float readabilityWithDifficultyLevelFactor = readability - difficultyLevelFactor;
        if (readabilityWithDifficultyLevelFactor >= GradePitStop1 && readabilityWithDifficultyLevelFactor < GradePitStop2) {
            percent = PercentPitStop1 + (PercentPitStop2 - PercentPitStop1) / (GradePitStop2 - GradePitStop1) * (readabilityWithDifficultyLevelFactor - GradePitStop1);
        } else if (readabilityWithDifficultyLevelFactor >= GradePitStop2 && readabilityWithDifficultyLevelFactor < GradePitStop3) {
            percent = PercentPitStop2 + (PercentPitStop3 - PercentPitStop2) / (GradePitStop3 - GradePitStop2) * (readabilityWithDifficultyLevelFactor - GradePitStop2);
        } else if (readabilityWithDifficultyLevelFactor >= GradePitStop3) {
            percent = PercentPitStop3 - readabiltyImpedanceFactor * (readabilityWithDifficultyLevelFactor - GradePitStop3);
        }
        percent = Math.max(0, Math.min(100, percent));
        outOfReadabilityScore = maxScore * weight / 100;
        readabilityScore = HelperFunctions.RoundNumber(percentOfEvaluation * percent * outOfReadabilityScore / 100, (byte) 2);
        ScaleReadability();
    }

    private void ScaleReadability() {
        if (readability <= ApplicationConstants.OptimalReadability) {
            scaledReadability = (float) (.0022 * Math.pow(readability, 2) + .0318 * readability + .0698);
        } //redundant check to account for log(0)
        else if (readability > 0) {
            scaledReadability = (float) (Math.log(21 * readability) / Math.log(1000));
        }
        scaledReadability = Math.max(0, Math.min(1, scaledReadability));
    }

    //No such bucket as length, just deduction
    //max (buffer uptil 80% min length of question)
    private void DeductLengthScore(float weight, float percentOfEvaluation, int questionMinLength) {
        int idealLength = Math.max(ApplicationConstants.MinWordLimit, questionMinLength);
        int cutOffLength = Math.max(ApplicationConstants.MinWordLimit, (int) (questionMinLength * ApplicationConstants.WordCountBufferForLengthScoring));
        float percent = 0;
        if (wordCount > cutOffLength) {
            if (wordCount < idealLength && idealLength > cutOffLength) {
                final float c1 = 100;
                percent = Math.min(100.f, Math.max(0.f, ((float) (idealLength - wordCount)) / (idealLength - cutOffLength) * c1));
            }
        } else {
            percent = 100;
        }
        outOfLengthScore = maxScore * weight / 100;
        lengthScore = HelperFunctions.RoundNumber(percentOfEvaluation * percent * outOfLengthScore / 100, (byte) 2);
    }

    private void SetAggregateScore(int minWordCount) {

        int lexicalDensityIndex = (int) Math.round(20 * vocabulary);
        int readabilityIndex = (int) readability;
        float spellingPercentOfEvaluation = (float) Math.min(1, 1.64 * Math.exp(-0.0417 * normalizedSpellingError / minWordCount * 100.f));//spellingIndex < ApplicationConstants.SpellingMistakeTenPercentPenalizerForOverallScore.length ? ApplicationConstants.SpellingMistakeTenPercentPenalizerForOverallScore[spellingIndex] / 100.f : 0;
        float grammarPercentOfEvaluation = (float) Math.min(1, 1.64 * Math.exp(-0.0417 * grammarError / minWordCount * 100.f));//grammarIndex < ApplicationConstants.GrammarMistakeTenPercentPenalizerForOverallScore.length ? ApplicationConstants.GrammarMistakeTenPercentPenalizerForOverallScore[grammarIndex] / 100.f : 0;
        float lexicalDensityPercentOfEvaluation = lexicalDensityIndex < ApplicationConstants.LexicalDensityFivePercentPenalizerForOverallScore.length ? ApplicationConstants.LexicalDensityFivePercentPenalizerForOverallScore[lexicalDensityIndex] / 100.f : 1;
        float readabilityPercentOfEvaluation = readabilityIndex < ApplicationConstants.ReadabilityUnitPenalizerForOverallScore.length ? ApplicationConstants.ReadabilityUnitPenalizerForOverallScore[readabilityIndex] / 100.f : 1;
        evalPercent *= spellingPercentOfEvaluation;
        evalPercent *= grammarPercentOfEvaluation;
        evalPercent *= lexicalDensityPercentOfEvaluation;
        evalPercent *= readabilityPercentOfEvaluation;
        //Evaluating criteria of evaluation
        if (1 - spellingPercentOfEvaluation > ApplicationConstants.Epsilon) {
            evaluationCriteria = evaluationCriteria | (1 << SpellingKey);
        }
        if (1 - grammarPercentOfEvaluation > ApplicationConstants.Epsilon) {
            evaluationCriteria = evaluationCriteria | (1 << GrammarKey);
        }
        if (1 - lexicalDensityPercentOfEvaluation > ApplicationConstants.Epsilon) {
            evaluationCriteria = evaluationCriteria | (1 << LDKey);
        }
        if (1 - readabilityPercentOfEvaluation > ApplicationConstants.Epsilon) {
            evaluationCriteria = evaluationCriteria | (1 << ReadabilityKey);
        }
        //System.out.println(id + ", " + evalPercent + ", " + spellingIndex + ", " + grammarIndex + ", " + lexicalDensityIndex + ", " + readabilityIndex);

        //System.out.println(id + " - " + normalizedSpellingError + " - " + Math.min(1, 1.64 * Math.exp(-0.0417 * normalizedSpellingError / minWordCount * 100.f)) + " - " + (spellingIndex < ApplicationConstants.SpellingMistakeTenPercentPenalizerForOverallScore.length ? ApplicationConstants.SpellingMistakeTenPercentPenalizerForOverallScore[spellingIndex] / 100.f : 0));
        //System.out.println(id + " - " + normalizedSpellingError);
        if (evalPercent < ApplicationConstants.Epsilon) {
            LoggingFunctions.InsertError("Evaluation Percent is 0 for Submission Id: " + id + System.lineSeparator(), "EnglishLanguageResponseClass", "SetAggregateScore");
        } else {
            /*outOfGrammarScore *= evalPercent;
             outOfKeywordScore *= evalPercent;
             outOfLengthScore *= evalPercent;
             outOfReadabilityScore *= evalPercent;
             outOfSpellingScore *= evalPercent;
             outOfVocabularyScore *= evalPercent;*/
        }
        evalPercent = isWordCountCriterionNotMeeting ? 0 : evalPercent;

        grammarScore *= evalPercent;
        keywordScore *= evalPercent;
        lengthScore *= evalPercent;
        readabilityScore *= evalPercent;
        spellingScore *= evalPercent;
        vocabularyScore *= evalPercent;

        this.aggregateScore = Math.max(Math.min(grammarScore + spellingScore + vocabularyScore + readabilityScore + lengthScore + keywordScore, maxScore), 0);
    }

    private void SetKeywordScore(float weight, float percentOfEvaluation, int outOfKeywordCount) {
        float matchPercent = 100f;
        if (outOfKeywordCount > 0) {
            matchPercent = (float) keywordMatchCount / outOfKeywordCount * 100;
        }
        outOfKeywordScore = maxScore * weight / 100;
        keywordScore = HelperFunctions.RoundNumber(percentOfEvaluation * Math.min(100, matchPercent) * outOfKeywordScore / 100, (byte) 2);
    }

    private void ReEvaluateScoreForToppers(int grammarWeight, float grammarPercentOfEvaluation, int spellingWeight, float spellingPercentOfEvaluation, QuestionClass question) {
        float previousGrammarScore = grammarScore;
        float previousSpellingScore = spellingScore;
        SetGrammarScore(grammarWeight, grammarPercentOfEvaluation, true, question);
        SetSpellingScore(spellingWeight, spellingPercentOfEvaluation, true, question);
        float newAggregateScore = grammarScore + spellingScore + vocabularyScore + readabilityScore + lengthScore + keywordScore;
        float cutOffReevaluationScore = ApplicationConstants.CutOffPercentForReEvaluation * this.maxScore;
        if (newAggregateScore >= cutOffReevaluationScore) {
            aggregateScore = newAggregateScore;
        } else {
            float individualScoreToBeDeducted = (this.aggregateScore - cutOffReevaluationScore) / 2;
            grammarScore = previousGrammarScore - individualScoreToBeDeducted;
            spellingScore = previousSpellingScore - individualScoreToBeDeducted;
            SetAggregateScore(Math.max(ApplicationConstants.MinWordLimit, question.MinLength()));
        }
    }

    @Override
    public void SetupBlockElements(Tokenizer blockTokenizer, HashMap<String, Integer> unusedWordFrequency) {
        spellingChain = new ArrayList<>();
        spellMistakes = new ArrayList<>();
        repetitiveChain = new ArrayList<>();
        grammarErrorBucketing = new LinkedHashMap<>();
        List<String> lstParas = HelperFunctions.SplitTextBySeperator(submissionText, ApplicationConstants.ParagraphSplitRegex);
        for (String para : lstParas) {
            ParagraphClass paragraph = new ParagraphClass(para);
            paragraph.SetupBlockElements(blockTokenizer, wordFrequency);
            boolean duplicateParagraph = false;
            for (ParagraphClass item : paragraphs) {
                if (item.GetCosineSimilarity(paragraph) > ApplicationConstants.MinAcceptableSimilarity) {
                    duplicateParagraph = true;
                    break;
                }
            }
            if (!duplicateParagraph) {
                paragraphs.add(paragraph);
                sentenceCount += paragraph.SentenceCount();
                wordCount += paragraph.WordCount();
                syllableCount += paragraph.SyllableCount();
            } else {
                isDuplicateParaFound = true;
            }
            magnitude += paragraph.Magnitude();
        }
        uniqueWordCount = wordFrequency.size();
        SetMagnitude();
        //n-gram analysis
        ngrams = HelperFunctions.getNGrams(blockTokenizer, submissionText);
        grammarErrorBucketing.put("Typography", 0);
        grammarErrorBucketing.put("Miscellaneous", 0);
        grammarErrorBucketing.put("Possible Typo", 0);
        grammarErrorBucketing.put("Redundant Phrases", 0);
        grammarErrorBucketing.put("Capitalization", 0);
        grammarErrorBucketing.put("Grammar", 0);
        grammarErrorBucketing.put("Commonly Confused Words", 0);
        grammarErrorBucketing.put("Punctuation Errors", 0);
        grammarErrorBucketing.put("Punctuation", 0);
        grammarErrorBucketing.put("Style", 0);
        grammarErrorBucketing.put(DefaultGrammarString, 0);
    }

    private final String DefaultGrammarString = "Others";

    @Override
    protected String GetEvaluationRemarks(QuestionClass question) {
        StringBuilder strEvaluationRemarks = new StringBuilder();
        if (status == SubmissionStatusEnum.Success) {
            strEvaluationRemarks.append("Id").append(ApplicationConstants.SecondLevelSplitter).append(id).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
            append("WC").append(ApplicationConstants.SecondLevelSplitter).append(wordCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
            append("VI").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", vocabulary)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("RI").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", scaledReadability)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("LI").append(ApplicationConstants.SecondLevelSplitter).append(wordCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("GI").append(ApplicationConstants.SecondLevelSplitter).append(grammarError).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("SI").append(ApplicationConstants.SecondLevelSplitter).append(spellingError).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("UGI").append(ApplicationConstants.SecondLevelSplitter).append(uniqueGrammarError).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("USI").append(ApplicationConstants.SecondLevelSplitter).append(uniqueSpellingError).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("KI").append(ApplicationConstants.SecondLevelSplitter).append(keywordMatchCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("VS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", vocabularyScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfVocabularyScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("RS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", readabilityScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfReadabilityScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("LS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", lengthScore)).append(ApplicationConstants.SecondLevelSplitter).append(0).append(ApplicationConstants.FirstLevelSplitter).
                    append("GS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", grammarScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfGrammarScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("SS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", spellingScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfSpellingScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("KS").append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", keywordScore)).append(ApplicationConstants.SecondLevelSplitter).append(String.format("%.2f", outOfKeywordScore)).append(ApplicationConstants.FirstLevelSplitter).
                    append("LN").append(ApplicationConstants.SecondLevelSplitter).append(language.Value()).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("WC").append(ApplicationConstants.SecondLevelSplitter).append(wordCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("SC").append(ApplicationConstants.SecondLevelSplitter).append(sentenceCount).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("PC").append(ApplicationConstants.SecondLevelSplitter).append(paragraphs.size()).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter).
                    append("TT").append(ApplicationConstants.SecondLevelSplitter).append(timeTaken).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
            if (true) {
                int i = 1;
                for (Map.Entry<String, Integer> entry : grammarErrorBucketing.entrySet()) {
                    //System.out.println(entry.getKey() + "/" + entry.getValue());
                    strEvaluationRemarks.append("GI").append(i++).append(ApplicationConstants.SecondLevelSplitter).append(entry.getValue()).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
                }
            }
            if (evaluationCriteria > 1) {
                //strEvaluationRemarks.append("EC").append(ApplicationConstants.SecondLevelSplitter).append(evaluationCriteria).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
                strEvaluationRemarks.append("EP").append(ApplicationConstants.SecondLevelSplitter).append(evalPercent).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
            }
            /*if (repetitiveChain.size() > 0) {
                strEvaluationRemarks.append("RC").append(ApplicationConstants.SecondLevelSplitter).append(1).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
            }
            if (spellingChain.size() > 0) {
                strEvaluationRemarks.append("SCN").append(ApplicationConstants.SecondLevelSplitter).append(1).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
            }
            if (isPlagiarismCase) {
                strEvaluationRemarks.append("PS").append(ApplicationConstants.SecondLevelSplitter).append(cheatedWithSubmissionId).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
                strEvaluationRemarks.append("PI").append(ApplicationConstants.SecondLevelSplitter).append((int) (plagiarismIndex * 100)).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
            }*/
            //.append("DP").append(ApplicationConstants.SecondLevelSplitter).append(isDuplicateParaFound).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
        }
        for (String remark : evaluationRemarks) {
            remark = remark.replace(ApplicationConstants.FirstLevelSplitter, " ");
            remark = remark.replace(ApplicationConstants.SecondLevelSplitter, " ");
            strEvaluationRemarks.append("RM").append(ApplicationConstants.SecondLevelSplitter).append(remark).append(ApplicationConstants.SecondLevelSplitter).append(ApplicationConstants.FirstLevelSplitter);
        }

        //System.out.println(uniqueGrammarError + "," + grammarError + "," + grammarScore + "," + uniqueSpellingError + "," + spellingError + "," + spellingScore + "," + vocabulary + "," + vocabularyScore + "," + readability + "," + readabilityScore);
        HelperFunctions.writeIntoFile(HelperFunctions.ReadEvalStringAndConvertToCSV(strEvaluationRemarks.toString()), ApplicationConstants.FeatureFileName, true);

        return strEvaluationRemarks.toString();
    }

    public EnglishLanguageResponseClass(int id, String submissionText) {
        super(id, submissionText);
        language = LanguageEnum.AmericanEnglish;
    }

    @Override
    protected boolean EvaluateParameters(QuestionClass question, JLanguageTool americanEnglishSpellingTool, JLanguageTool britishEnglishSpellingTool, JLanguageTool americanEnglishGrammarTool, JLanguageTool britishEnglishGrammarTool, HashMap<Character, Byte> codes) {
        repetitiveChain = new ArrayList<>();
        spellingChain = new ArrayList<>();
        boolean rValue = false;
        status = SubmissionStatusEnum.Failed;
        if (wordCount < ApplicationConstants.MinWordLimit) {
            evaluationRemarks.add("Submission is too short to evaluate, minimum words required " + ApplicationConstants.MinWordLimit);
        } else if (uniqueWordCount < ApplicationConstants.MinUniqueLength) {
            evaluationRemarks.add("Submission had less than required minimum unique words which is " + uniqueWordCount + "; Required Unique Word Count: " + ApplicationConstants.MinUniqueLength);
        } else {
            keywordMatchCount = 0;
            for (String word : question.Keywords().keySet()) {
                for (ParagraphClass paragraph : paragraphs) {
                    if (paragraph.wordFrequency.containsKey(word)) {
                        keywordMatchCount++;
                        break;
                    }
                }
            }
            repetitiveChain = DetectRepetitiveChains(submissionText);
            wordCount = HelperFunctions.ChainsAccountedForWordCount(wordCount, repetitiveChain, id, submissionText);
            if (SetSpellingMistakeCount(americanEnglishSpellingTool, britishEnglishSpellingTool)
                    && SetGrammarMistakeCount(language == LanguageEnum.AmericanEnglish ? americanEnglishGrammarTool : britishEnglishGrammarTool)) {
                rValue = true;
                status = SubmissionStatusEnum.Success;
                //todo: uncomment to do phrase analysis                
                //UpdateSubmissionForPhrases();
            }
        }

        //detect if words such as @#$#$@ or 234243 are used
        int allAlphabetWordCount = 0;
        for (Map.Entry<String, Integer> entry : wordFrequency.entrySet()) {
            allAlphabetWordCount += isAllAlphabetWord(entry.getKey()) ? entry.getValue() : 0;
        }
        if (allAlphabetWordCount < MinRequiredAllAlphabetWordPercent * (question.MinLength() > 0 ? question.MinLength() : ApplicationConstants.MinWordLimit) || wordCount < question.MinLength() * ApplicationConstants.WordCountBufferForLengthScoring) {
            isWordCountCriterionNotMeeting = true;
            LoggingFunctions.InsertError("Submission Id: " + id + " had a word count based issue", "EnglishLanguageResponseClass", "UpdateSubmission");

        }
        return rValue;
    }

    private boolean isAllAlphabetWord(String word) {
        for (int i = 0; i < word.length(); i++) {
            char currenChar = word.charAt(i);
            if (!((currenChar >= 65 && currenChar <= 90) || (currenChar >= 97 && currenChar <= 122))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean Score(QuestionClass question, HashMap<DifficultyLevelEnum, HashMap<ScoringParameters, Integer>> hashScoringParameters) {
        int weightVocab = hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Vocabulary);
        float percentRemainingForEvaluation = 1;
        if (wordCount < question.MinLength()) {
            //5% per 50 words with a buffer of 50 words
            percentRemainingForEvaluation = 1 - (question.MinLength() - wordCount) / 50 * (float) 5 / 100;
        }
        if (question.Keywords() != null && question.Keywords().size() > 0) {
            SetKeywordScore(hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Keyword), percentRemainingForEvaluation, question.Keywords().size());
        } else {
            weightVocab += hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Keyword);
        }

        SetGrammarScore(hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Grammar), percentRemainingForEvaluation, false, question);
        SetSpellingScore(hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Spelling), percentRemainingForEvaluation, false, question);
        SetReadabilityScore(hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Readability), percentRemainingForEvaluation, question.DifficultyLevel());
        DeductLengthScore(hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.WordLimit), percentRemainingForEvaluation, question.MinLength());
        SetVocabScore(weightVocab, percentRemainingForEvaluation);
        SetAggregateScore(question.MinLength());
        if (IsScoreNormalizationRequired()) {
            ReEvaluateScoreForToppers(hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Grammar), percentRemainingForEvaluation, hashScoringParameters.get(question.DifficultyLevel()).get(ScoringParameters.Spelling), percentRemainingForEvaluation, question);
        }
        return true;
    }

    @Override
    protected boolean StaticAnalysis(QuestionClass question, HashMap<Integer, ResponseBaseClass> hashTotalSubmissions) {
        //Set Time Taken
        if (timeTaken <= 0) {
            timeTaken = (short) (-1 * timeTaken);
            if (submissionText.length() > 0) {
                //40 WPM is the average typing speed, assuming some time will go in thinking(taking 30 WPM)
                if (dbWordCount == 0) {
                    timeTaken = (short) ((wordCount / 30d) * 60d + timeTaken);
                } else if (wordCount > dbWordCount) {
                    timeTaken = (short) (((wordCount - dbWordCount) / 30d) * 60d + timeTaken);
                }
            }
        }

        //plagiarism detection
        for (ResponseBaseClass submission : hashTotalSubmissions.values()) {
            if (submission instanceof EnglishLanguageResponseClass) {
                EnglishLanguageResponseClass submissionToCheckPlagiarismFrom = (EnglishLanguageResponseClass) submission;
                if (id != submissionToCheckPlagiarismFrom.id
                        && questionId == submissionToCheckPlagiarismFrom.questionId) {
                    plagiarismIndex = GetCosineSimilarity(submissionToCheckPlagiarismFrom);
                    if (plagiarismIndex > ApplicationConstants.MinAcceptableSimilarity) {
                        isPlagiarismCase = true;
                        cheatedWithSubmissionId = submissionToCheckPlagiarismFrom.id;
                        //ideally one cheating should be enough
                        break;
                    }
                }
            }
        }
        return true;
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
            statement.setShort(counter++, timeTaken);
            statement.setFloat(counter++, aggregateScore);
            statement.setInt(counter++, wordCount);
            statement.setString(counter++, GetEvaluationRemarks(question));
            statement.setTime(counter++, new java.sql.Time(System.currentTimeMillis()));
            statement.setInt(counter++, status.Value());
            statement.setInt(counter++, id);
            rValue = statement.executeUpdate() > 0;
            if (!rValue) {
                LoggingFunctions.InsertError("Submission Id: " + id + " could not be updated", "EnglishLanguageResponseClass", "UpdateSubmission");
            }
        } catch (Exception e) {
            LoggingFunctions.InsertError("Error occurred while updating the Submission Id: " + id + "\r\n" + e.getMessage(), "EnglishLanguageResponseClass", "UpdateSubmission");
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing PreparedStatement" + System.lineSeparator() + e.getMessage(), "EnglishLanguageResponseClass", "UpdateSubmission");
                }
            }
            if (isLocalConnection && con != null) {
                try {
                    con.close();
                } catch (Exception e) {
                    LoggingFunctions.InsertError("Error occurred while closing Connection" + System.lineSeparator() + e.getMessage(), "EnglishLanguageResponseClass", "UpdateSubmission");
                }
            }
        }
        return rValue;
    }

}

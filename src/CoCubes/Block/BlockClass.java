
package CoCubes.Block;

import CoCubes.ApplicationConstants.ApplicationConstants;
import java.util.HashMap;
import java.util.HashSet;
import org.languagetool.tokenizers.Tokenizer;

abstract public class BlockClass {

    public HashMap<String, Integer> wordFrequency;
    public float magnitude;
    public static final HashSet<String> IgnoreWordsForSimilarityEvaluation;

    static {
        IgnoreWordsForSimilarityEvaluation = new HashSet<>();
        IgnoreWordsForSimilarityEvaluation.add("the");
        IgnoreWordsForSimilarityEvaluation.add("are");
        IgnoreWordsForSimilarityEvaluation.add("was");
        IgnoreWordsForSimilarityEvaluation.add("were");
        IgnoreWordsForSimilarityEvaluation.add("has");
        IgnoreWordsForSimilarityEvaluation.add("have");
        IgnoreWordsForSimilarityEvaluation.add("had");
        IgnoreWordsForSimilarityEvaluation.add("and");
        IgnoreWordsForSimilarityEvaluation.add("these");
        IgnoreWordsForSimilarityEvaluation.add("there");
        IgnoreWordsForSimilarityEvaluation.add("that");
        IgnoreWordsForSimilarityEvaluation.add("them");
        IgnoreWordsForSimilarityEvaluation.add("then");
        IgnoreWordsForSimilarityEvaluation.add("than");
        IgnoreWordsForSimilarityEvaluation.add("their");
        IgnoreWordsForSimilarityEvaluation.add("not");
        IgnoreWordsForSimilarityEvaluation.add("who");
        IgnoreWordsForSimilarityEvaluation.add("why");
        IgnoreWordsForSimilarityEvaluation.add("whom");
    }

    public BlockClass() {
        wordFrequency = new HashMap<>();
        magnitude = Float.MAX_VALUE;
    }

    abstract public void SetupBlockElements(Tokenizer blockTokenizer, HashMap<String, Integer> wordFrequency);

    public void SetMagnitude() {
        magnitude = 0;
        for (String word : wordFrequency.keySet()) {
            if (word.length() >= ApplicationConstants.MinLengthWordForSimilarityEvaluation && IgnoreWordsForSimilarityEvaluation.contains(word) == false) {
                magnitude += Math.pow(wordFrequency.get(word), 2);
            }
        }
        magnitude = (float)(Math.sqrt(magnitude));
    }

    public float GetCosineSimilarity(BlockClass block2) {
        float similarity = 0;
        if (magnitude > 0 && block2.magnitude > 0) {
            long svMul = 0l;
            BlockClass largePara, smallPara;
            if (wordFrequency.size() > block2.wordFrequency.size()) {
                largePara = this;
                smallPara = block2;
            } else {
                largePara = block2;
                smallPara = this;
            }
            for (String word : smallPara.wordFrequency.keySet()) {
                if (word.length() >= ApplicationConstants.MinLengthWordForSimilarityEvaluation && !IgnoreWordsForSimilarityEvaluation.contains(word) && largePara.wordFrequency.containsKey(word)) {
                    svMul += largePara.wordFrequency.get(word) * smallPara.wordFrequency.get(word);
                }
            }
            similarity = svMul / (magnitude * block2.magnitude);
        }
        return similarity;
    }

    public float Magnitude() {
        return magnitude;
    }

    public int GetWordCount(String word) {
        return wordFrequency.getOrDefault(word, 0);
    }

}

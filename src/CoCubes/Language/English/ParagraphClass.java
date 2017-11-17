
package CoCubes.Language.English;

import CoCubes.Block.BlockClass;
import CoCubes.Helper.HelperFunctions;
import java.util.HashMap;
import java.util.List;
import org.languagetool.tokenizers.Tokenizer;

public class ParagraphClass extends BlockClass {

    protected String text;
    private int sentenceCount;
    protected int wordCount;
    private int syllableCount;

    protected ParagraphClass(String paraText) {
        this.text = paraText;
    }

    protected ParagraphClass() {
    }

    protected int SentenceCount() {
        return sentenceCount;
    }

    protected int WordCount() {
        return wordCount;
    }

    protected int SyllableCount() {
        return syllableCount;
    }

    protected String Text() {
        return text;
    }
    
    @Override
    public void SetupBlockElements(Tokenizer sentenceTokenizer, HashMap<String, Integer> wordFrequency) {
        List<String> sentences = sentenceTokenizer.tokenize(text);
        sentenceCount = sentences.size();
        for (String sentence : sentences) {
            List<String> words = HelperFunctions.SplitIntoLowerCaseWords(sentence);
            wordCount += words.size();
            for (String word : words) {
                if (!this.wordFrequency.containsKey(word)) {
                    this.wordFrequency.put(word, 1);
                } else {
                    this.wordFrequency.put(word, this.wordFrequency.get(word) + 1);
                }
                if (wordFrequency != null) {
                    if (!wordFrequency.containsKey(word)) {
                        wordFrequency.put(word, 1);
                    } else {
                        wordFrequency.put(word, wordFrequency.get(word) + 1);
                    }
                }
                syllableCount += HelperFunctions.CountSyllables(word);
            }
        }
        SetMagnitude();
    }
}
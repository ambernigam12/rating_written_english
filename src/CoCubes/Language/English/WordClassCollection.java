
package CoCubes.Language.English;

import CoCubes.ApplicationConstants.ApplicationConstants;
import java.util.ArrayList;
import java.util.HashMap;

public class WordClassCollection extends ArrayList<WordClass> {

    protected void SplitIntoWordHashes(String text, HashMap<Character, Byte> codes, int offset) {
        for (String word : text.split(ApplicationConstants.WordSplitRegex)) {
            int currentWordHash = 0;
            int currentLowerWordHash = 0;
            for (int i = 0; i < word.length(); i++) {
                char currentChar = word.charAt(i);
                int currentCharCode = ApplicationConstants.IndexEquivalents[i % ApplicationConstants.IndexEquivalents.length];
                int currentLowerCharCode = currentCharCode *= codes.containsKey(currentChar) ? codes.get(currentChar) : (offset + currentChar);
                currentCharCode += currentChar >= 'A' && currentChar <= 'Z' ? ApplicationConstants.DiffBetweenLowerAndUpperCaseAlphabets : 0;
                currentWordHash += currentCharCode;
                currentLowerWordHash += currentLowerCharCode;
            }
            this.add(new WordClass(word, currentWordHash, currentLowerWordHash));
        }
    }
    
}

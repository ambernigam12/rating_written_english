
package CoCubes.Language.English;

import CoCubes.Block.BlockClass;
import java.util.HashMap;
import org.languagetool.tokenizers.Tokenizer;

/**
 *
 * @author Awesome Geeks
 */
public class NGramClass extends BlockClass {

    @Override
    public void SetupBlockElements(Tokenizer blockTokenizer, HashMap<String, Integer> wordFrequency) {
        this.wordFrequency = wordFrequency;
        SetMagnitude();
    }

}

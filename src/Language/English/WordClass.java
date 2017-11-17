
package CoCubes.Language.English;

enum OperationTypeEnum {

    Deletion((byte) 35),
    Insertion((byte) 20),
    Substitution((byte) 35),
    CaseErrors((byte) 10),//can be more than one per word
    CaseInsensitiveMatch((byte) 0),
    ExactMatch((byte) 0);
    private final byte weightage;

    OperationTypeEnum(byte weight) {
        this.weightage = weight;
    }

    protected byte Weight() {
        return weightage;
    }

}

public class WordClass {

    private final String text;
    private final int hash;
    private final int lowerCaseWordHash;
    private OperationTypeEnum operation;

    protected WordClass(String text, int hash, int lowerCaseWordHash) {
        this.text = text;
        this.hash = hash;
        this.lowerCaseWordHash = lowerCaseWordHash;
    }

    protected String Text() {
        return text;
    }

    protected int Hash() {
        return hash;
    }

    protected int LowerCaseWordHash() {
        return lowerCaseWordHash;
    }

    //to be used later
    OperationTypeEnum Operation() {
        return operation;
    }

    void SetOperation(OperationTypeEnum operation) {
        this.operation = operation;
    }

}

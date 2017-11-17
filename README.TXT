========================
BUILD OUTPUT DESCRIPTION
========================

1. 
This utility evaluates the features and saves them in a csv file named "features.csv"
2. These features are then selectively used for predicting the final score through machine learning algorithms. Some features like Grammar error density are evaluated by dividing grammar error count by (word count/100).
3. Database scripts "essays.sql" are inside the folder "Corpus_backup_scripts"
4. Final arff files are also provided which can directly be run through Weka for verifying the results.
5. This project currently supports only Windows platform, though we intend to make it platform independent.
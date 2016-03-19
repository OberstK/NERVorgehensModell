package de.benni.rssparser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.benni.rssparser.Parser;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.*;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

public class Matcher {
	private final static Logger LOGGER = Logger.getLogger(Parser.class.getName());

	public void checkForMatchesOnWords() {
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		List<Word> words = new ArrayList<>();
		List<Company> companies = new ArrayList<>();
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Parser.class.getName());
			e.printStackTrace();
		}
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			Statement stmt = conn.createStatement();

			String sql = "SELECT word, batch, hash FROM newsdb.processed";
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				// Retrieve by column name
				String content = rs.getString("word");
				int batch = rs.getInt("batch");
				String hash = rs.getString("hash");

				Word word = new Word(content);
				word.setBatch(batch);
				word.setHash(hash);

				words.add(word);
			}
			rs.close();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in Database handle", Parser.class.getName());
			e.printStackTrace();
		}

		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			Statement stmt = conn.createStatement();

			String sql = "SELECT id, name FROM newsdb.companies";
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				// Retrieve by column name
				int id = rs.getInt("id");
				String name = rs.getString("name");

				Company c = new Company(id, name);

				companies.add(c);
			}
			rs.close();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in Database handle", Parser.class.getName());
			e.printStackTrace();
		}

		ArrayList<String> stopwords = new ArrayList<>();
		int lines = 0;
		try {
			FileReader fileReader = new FileReader("src/main/resources/stopwords.txt");
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line = null;

			while ((line = bufferedReader.readLine()) != null) {
				if (lines > 4) {
					stopwords.add(line);
				}
				lines++;
			}

			bufferedReader.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "That file could not be read!", Parser.class.getName());
			e.printStackTrace();
		}

		Word previousOne = null;
		int wordsLength = words.size();
		int counter = 0;
		for (Word w : words) {
			boolean match = false;
			boolean stop = false;
			for (String stopword : stopwords) {
				if (w.getContent().equalsIgnoreCase(stopword)) {
					stop = true;
				}
			}
			if (w.getContent().matches("^([A-Za-z]|[0-9])+$") && stop == false) {
				for (Company c : companies) {
					if (w.getContent().equals(c.getName())) {
						// System.out.println("MATCH! " + c.getName() + " found
						// with single word " + w.getContent());
						w.setLabelOwn("B-ORG");
						match = true;
					}
					if (!match && c.getName().contains(" ")) {
						if (previousOne != null && previousOne.getBatch() == w.getBatch()) {
							String combined = previousOne.getContent() + " " + w.getContent();
							// System.out.println("COMBINER ONE: "+combined);
							if (combined.equals(c.getName())) {
								// System.out.println("MATCH! " + c.getName() +
								// " found with combined one word " + combined);
								previousOne.setLabelOwn("B-ORG");
								w.setLabelOwn("I-ORG");
								match = true;
							}
						}
					}
				}
				if (!match) {
					// System.out.println(w.getContent()+" could not be
					// identified as a entity");
					w.setLabelOwn("O");
				}

			}

			// set new previous words
			previousOne = w;
			counter++;
			if ((counter % 1000) == 0) {
				System.out.println(counter + " of " + wordsLength + " done");
			}
		}
		
		System.out.println("Saving matches to database");

		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			conn.setAutoCommit(false);
			for (Word w : words) {
				if (w.getLabelOwn() != null) {
					boolean exist = false;
					final String checkQuery = "SELECT COUNT(*) FROM newsdb.matches WHERE hash= ? AND word = ?";
					try (final PreparedStatement statement = conn.prepareStatement(checkQuery)) {
						statement.setString(1, w.getHash());
						statement.setString(2, w.getContent());
						try (final ResultSet resultSet = statement.executeQuery()) {
							if (resultSet.next() && resultSet.getInt(1) > 0) {
								exist = true;
							}
						}
					}
					if(exist){
						final String updateQuery = "UPDATE newsdb.matches SET label_pred_own = ? WHERE hash= ? AND word = ?";
						try (final PreparedStatement statement = conn.prepareStatement(updateQuery)) {
							statement.setString(1, w.getLabelOwn());
							statement.setString(2, w.getHash());
							statement.setString(3, w.getContent());
							statement.executeUpdate();
						}
					}else{
						final String insertQuery = "INSERT INTO newsdb.matches (word, label_pred_own, hash) values (?, ?, ?)";
						try (final PreparedStatement statement = conn.prepareStatement(insertQuery)) {
							statement.setString(1, w.getContent());
							statement.setString(2, w.getLabelOwn());
							statement.setString(3, w.getHash());
							statement.executeUpdate();
						}
					}
				}
			}
			conn.commit();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Database handler error", Parser.class.getName());
			e.printStackTrace();
		}
		System.out.println("Own Matching done");
	}


	public void checkForMatchesWithStanfordNLP() {
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		List<Word> matches = new ArrayList<>();
		List<String[]> texts = new ArrayList<>();
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Parser.class.getName());
			e.printStackTrace();
		}
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			Statement stmt = conn.createStatement();

			String sql = "SELECT DISTINCT clean_content, hash FROM newsdb.processed";
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				// Retrieve by column name
				String content = rs.getString("clean_content");
				String hash = rs.getString("hash");
				
				String[] text = {content, hash};

				texts.add(text);
			}
			rs.close();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in Database handle", Parser.class.getName());
			e.printStackTrace();
		}
		
		ArrayList<String> stopwords = new ArrayList<>();
		int lines = 0;
		try {
			FileReader fileReader = new FileReader("src/main/resources/stopwords.txt");
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line = null;

			while ((line = bufferedReader.readLine()) != null) {
				if (lines > 4) {
					stopwords.add(line);
				}
				lines++;
			}

			bufferedReader.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "That file could not be read!", Parser.class.getName());
			e.printStackTrace();
		}
		
		String serializedClassifier = "src/main/resources/german.dewac_175m_600.crf.ser.gz";

		AbstractSequenceClassifier<CoreLabel> classifier;
		try {
			classifier = CRFClassifier.getClassifier(serializedClassifier);
			for (String[] str : texts) {
		        for (List<CoreLabel> lcl : classifier.classify(str[0])) {
		            for (CoreLabel cl : lcl) {
		    			boolean stop = false;
		    			for (String stopword : stopwords) {
		    				if (cl.value().equalsIgnoreCase(stopword)) {
		    					stop = true;
		    				}
		    			}
		    			if(!(cl.get(AnswerAnnotation.class).equals("O") || cl.get(AnswerAnnotation.class).equals("B-ORG") || cl.get(AnswerAnnotation.class).equals("I-ORG"))){
		    				cl.set(AnswerAnnotation.class, "O");
		    			}
		            	if (cl.value().matches("^([A-Za-z]|[0-9])+$") && stop == false) {
		            		Word match = new Word(cl.value());
		            		match.setLabelStan(cl.get(AnswerAnnotation.class));
		            		match.setHash(str[1]);
		        			matches.add(match);
		        			//System.out.println("MATCH! "+ match.getContent() +" "+match.getLabelStan());
		        		}
		            }
		          }
			}

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Classifier could not be created!", Parser.class.getName());
			e.printStackTrace();
		}
		
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			conn.setAutoCommit(false);
			for (Word w : matches) {
				if (w.getLabelStan() != null) {
					boolean exist = false;
					final String checkQuery = "SELECT COUNT(*) FROM newsdb.matches WHERE hash=? AND word = ?";
					try (final PreparedStatement statement = conn.prepareStatement(checkQuery)) {
						statement.setString(1, w.getHash());
						statement.setString(2, w.getContent());
						try (final ResultSet resultSet = statement.executeQuery()) {
							if (resultSet.next() && resultSet.getInt(1) > 0) {
								exist = true;
							}
						}
					}
					if(exist){
						final String updateQuery = "UPDATE newsdb.matches SET label_pred_stanford = ? WHERE hash= ? AND word = ?";
						try (final PreparedStatement statement = conn.prepareStatement(updateQuery)) {
							statement.setString(1, w.getLabelStan());
							statement.setString(2, w.getHash());
							statement.setString(3, w.getContent());
							statement.executeUpdate();
						}
					}
				}
			}
			conn.commit();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Database handler error", Parser.class.getName());
			e.printStackTrace();
		}	

	}
	
	
	public void testMatchResults(){
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		List<String[]> matches = new ArrayList<>();
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Parser.class.getName());
			e.printStackTrace();
		}
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			Statement stmt = conn.createStatement();

			String sql = "SELECT word, label_test, label_pred_own, label_pred_stanford FROM newsdb.matches WHERE id <= 3000";
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				// Retrieve by column name
				String word = rs.getString("word");
				String test = rs.getString("label_test");
				String own = rs.getString("label_pred_own");
				String stan = rs.getString("label_pred_stanford");
				
				String[] match = {word, test, own, stan};

				matches.add(match);
			}
			rs.close();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in Database handle", Parser.class.getName());
			e.printStackTrace();
		}
		
		int countAll = matches.size();
		HashMap<String, Float> ownResults = new HashMap<String, Float>();
		ownResults.put("FP", 0.0F);
		ownResults.put("TP", 0.0F);
		ownResults.put("TN", 0.0F);
		ownResults.put("FN", 0.0F);
		HashMap<String, Float> stanResults = new HashMap<String, Float>();
		stanResults.put("FP", 0.0F);
		stanResults.put("TP", 0.0F);
		stanResults.put("TN", 0.0F);
		stanResults.put("FN", 0.0F);
		
		//TEST for ORG/Not org. No absolute match!
		for (String[] strings : matches) {
			String test = strings[1];
			String predOwn = strings[2];
			String predStan = strings[3];
			if((test.contains("ORG") && predOwn.contains("ORG"))){
				float count = ownResults.get("TP");
				ownResults.put("TP", count+1);
			}else if(test.equals("O") && predOwn.equals("O")){
				float count = ownResults.get("TN");
				ownResults.put("TN", count+1);
			}else if(test.contains("ORG") && predOwn.equals("O")){
				float count = ownResults.get("FN");
				ownResults.put("FN", count+1);
			}else if(test.equals("O") && predOwn.contains("ORG")){
				float count = ownResults.get("FP");
				ownResults.put("FP", count+1);
			}
			
			if((test.contains("ORG") && predStan.contains("ORG"))){
				float count = stanResults.get("TP");
				stanResults.put("TP", count+1);
			}else if(test.equals("O") && predStan.equals("O")){
				float count = stanResults.get("TN");
				stanResults.put("TN", count+1);
			}else if(test.contains("ORG") && predStan.equals("O")){
				float count = stanResults.get("FN");
				stanResults.put("FN", count+1);
			}else if(test.equals("O") && predStan.contains("ORG")){
				float count = stanResults.get("FP");
				stanResults.put("FP", count+1);
			}
		}
		
		if((stanResults.get("TP") + stanResults.get("FP") + stanResults.get("TN") + stanResults.get("FN")) != countAll){
			System.out.println("Overall test for stan does not match overall count!");
		}
		
		if((ownResults.get("TP") + ownResults.get("FP") + ownResults.get("TN") + ownResults.get("FN")) != countAll){
			System.out.println("Overall test for own does not match overall count!");
		}
		float ownPrecision = ownResults.get("TP")/(ownResults.get("TP")+ownResults.get("FP"));
		float ownAccuracy = (ownResults.get("TP")+ownResults.get("TN"))/(ownResults.get("TP")+ownResults.get("TN")+ownResults.get("FP")+ownResults.get("FN"));
		float ownRecall = ownResults.get("TP")/(ownResults.get("TP")+ownResults.get("FN")); 
		float ownf = 2 * ((ownPrecision*ownRecall)/(ownPrecision+ownRecall));
		
		float stanPrecision = stanResults.get("TP")/(stanResults.get("TP")+stanResults.get("FP"));
		float stanAccuracy = (stanResults.get("TP")+stanResults.get("TN"))/(stanResults.get("TP")+stanResults.get("TN")+stanResults.get("FP")+stanResults.get("FN"));
		float stanRecall = stanResults.get("TP")/(stanResults.get("TP")+stanResults.get("FN")); 
		float stanf = 2 * ((stanPrecision*stanRecall)/(stanPrecision+stanRecall));
		
		System.out.println("Overall Count of test data: "+countAll);
		System.out.println("Results for own Prediction:");
		System.out.println("Found "+ownResults.get("TP")+" true Positives");
		System.out.println("Found "+ownResults.get("TN")+" true Negatives");
		System.out.println("Found "+ownResults.get("FP")+" false Positives");
		System.out.println("Found "+ownResults.get("FN")+" false Negatives");
		System.out.println("Precision: "+ownPrecision*100+"%");
		System.out.println("Accuracy: "+ownAccuracy*100+"%");
		System.out.println("Recall: "+ownRecall*100+"%");
		System.out.println("F-Score: "+ownf);
		System.out.println("----------------------");
		System.out.println("Results for Stanford Prediction:");
		System.out.println("Found "+stanResults.get("TP")+" true Positives");
		System.out.println("Found "+stanResults.get("TN")+" true Negatives");
		System.out.println("Found "+stanResults.get("FP")+" false Positives");
		System.out.println("Found "+stanResults.get("FN")+" false Negatives");
		System.out.println("Precision: "+stanPrecision*100+"%");
		System.out.println("Accuracy: "+stanAccuracy*100+"%");
		System.out.println("Recall: "+stanRecall*100+"%");
		System.out.println("F-Score: "+stanf);
	}

	public static void main(String[] args) {
		Parser parser = new Parser();
		Cleaner cleaner = new Cleaner();
		Matcher matcher = new Matcher();
		
		/*
		 * Use this to run the rss parser on the urls
		 */
		//parser.fullParse();
		
		/*
		 * Run this after running the SPARQL Import initially 
		 */
        //cleaner.cleanSPARQLData();
		
		/*
		 * Run this when the PreProcess in rapidMiner is done
		 */
		//matcher.checkForMatchesOnWords();
		//matcher.checkForMatchesWithStanfordNLP();
		
		/*
		 * Run This after running the matcher methods to get the test results
		 */
		//matcher.testMatchResults();
	}
}

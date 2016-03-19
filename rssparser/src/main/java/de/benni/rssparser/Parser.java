package de.benni.rssparser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;

import de.benni.rssparser.Entry.Source;
import net.htmlparser.jericho.Renderer;
import net.htmlparser.jericho.Segment;

public class Parser {
	private static String[] NEWSSOURCES;
	private static int newEntriesCounter = 0;
	private final static Logger LOGGER = Logger.getLogger(Parser.class.getName());

	public String[] readLines(String filename) {
		List<String> lines = new ArrayList<>();
		try {
			FileReader fileReader = new FileReader(filename);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				lines.add(line);
			}
			bufferedReader.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "That file could not be read!", Parser.class.getName());
			e.printStackTrace();
		}
		return lines.toArray(new String[lines.size()]);
	}

	public List<Entry> readFromRSSFeed(String url, Source source) {
		List<Entry> entries = new ArrayList<>();
		URL feedUrl = null;
		try {
			feedUrl = new URL(url);
		} catch (MalformedURLException e) {
			LOGGER.log(Level.SEVERE, "That url is not valid!", Parser.class.getName());
			e.printStackTrace();
		}
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = null;
		try {
			feed = input.build(new InputStreamReader(feedUrl.openStream()));
		} catch (FeedException e) {
			LOGGER.log(Level.SEVERE, "There was an error with the Feed you provided", Parser.class.getName());
			e.printStackTrace();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Input could not be processed", Parser.class.getName());
			e.printStackTrace();
		}

		List<SyndEntry> rssEntries = feed.getEntries();
		for (SyndEntry syndEntry : rssEntries) {
			String title = syndEntry.getTitle();
			String link = syndEntry.getLink();
			SyndContent desc = syndEntry.getDescription();
			Date date = syndEntry.getPublishedDate();
			if (title == null || link == null || desc == null || date == null) {
				continue;
			}
			Entry entry = new Entry(title, link, source, desc.getValue(), date);
			entries.add(entry);
		}
		return entries;
	}

	public void saveToDB(List<Entry> entries) throws SQLException {
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Parser.class.getName());
			e.printStackTrace();
		}
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			conn.setAutoCommit(false);
			for (Entry e : entries) {
				boolean result = insertIfNotPresent(conn, e);
				if (result == true) {
					newEntriesCounter++;
				}
			}
			conn.commit();
		}
	}

	public void updateCleanContentInDB(List<Entry> entries) throws SQLException {
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Parser.class.getName());
			e.printStackTrace();
		}
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			conn.setAutoCommit(false);
			for (Entry e : entries) {
				if (e.getId() != 0 && e.getCleanContent() != null) {
					final String updateQuery = "UPDATE newsdb.news SET clean_content = ? WHERE id= ?";
					try (final PreparedStatement statement = conn.prepareStatement(updateQuery)) {
						statement.setString(1, e.getCleanContent());
						statement.setInt(2, e.getId());
						statement.executeUpdate();
					}
				}
			}
			conn.commit();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in Database update", Parser.class.getName());
			e.printStackTrace();
		}
	}

	public List<Entry> getNewNewsFromDB() throws SQLException {
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		List<Entry> entries = new ArrayList<>();
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Parser.class.getName());
			e.printStackTrace();
		}
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			Statement stmt = conn.createStatement();

			String sql = "SELECT id, hash, title, link, content, date, source FROM newsdb.news WHERE clean_content IS NULL";
			ResultSet rs = stmt.executeQuery(sql);

			while (rs.next()) {
				// Retrieve by column name
				int id = rs.getInt("id");
				String hash = rs.getString("hash");
				String title = rs.getString("title");
				String link = rs.getString("link");
				String content = rs.getString("content");
				Date date = rs.getDate("date");
				String source = rs.getString("source");

				Entry e = new Entry(title, link, null, content, date);
				e.setHash(hash);
				e.setId(id);
				e.setSourceOnString(source);
				entries.add(e);
			}
			rs.close();
		}
		return entries;
	}

	public static boolean insertIfNotPresent(final Connection con, Entry e) throws SQLException {
		final String checkQuery = "SELECT COUNT(*) FROM newsdb.news WHERE hash=?";
		try (final PreparedStatement statement = con.prepareStatement(checkQuery)) {
			statement.setString(1, e.getHash().toString());
			try (final ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next() && resultSet.getInt(1) > 0) {
					return false;
				}
			}
		}
		final String insertQuery = "INSERT INTO newsdb.news (hash, title, link, content, date, source) values (?, ?, ?, ?, ?, ?)";
		try (final PreparedStatement statement = con.prepareStatement(insertQuery)) {
			statement.setString(1, e.getHash().toString());
			statement.setString(2, e.getTitle());
			statement.setString(3, e.getLink());
			statement.setString(4, e.getContent());
			statement.setDate(5, new java.sql.Date(e.getDate().getTime()));
			statement.setString(6, e.getSource().toString());
			statement.executeUpdate();
		}
		return true;
	}

	public String getCleanContent(String input) {
		// System.out.println("Original: " + input);
		net.htmlparser.jericho.Source htmlSource = new net.htmlparser.jericho.Source(input);
		Segment htmlSeg = new Segment(htmlSource, 0, htmlSource.length());
		Renderer htmlRend = new Renderer(htmlSeg);
		htmlRend.setIncludeAlternateText(false);
		htmlRend.setIncludeHyperlinkURLs(false);
		String result = htmlRend.toString();
		result = result.replaceAll(",", " ,");
		result = result.replaceAll(":", " :");
		// System.out.println("Result: " + htmlRend.toString());
		return result;
	}

	public void fullParse() {
		List<Entry> entries = new ArrayList<>();
		List<Entry> dbList = new ArrayList<>();
		NEWSSOURCES = this.readLines("src/main/resources/urls.txt");
		int count = 0;
		for (String newsSource : NEWSSOURCES) {
			Source source = Entry.getSourceForUrl(count);
			entries.addAll(this.readFromRSSFeed(newsSource, source));
			count++;
		}

		try {
			this.saveToDB(entries);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "There was an error persisting into the Database", Parser.class.getName());
			e.printStackTrace();
		}
		System.out.println("New Entries added: " + newEntriesCounter);

		try {
			dbList = this.getNewNewsFromDB();
			System.out.println(dbList.size() + " News open for cleaning");
			for (Entry entry : dbList) {
				String cleanContent = this.getCleanContent(entry.getContent());
				entry.setCleanContent(cleanContent);
			}
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "There was an error getting data from the Database", Parser.class.getName());
			e.printStackTrace();
		}

		try {
			this.updateCleanContentInDB(dbList);
		} catch (SQLException e) {
			LOGGER.log(Level.SEVERE, "There was an error persisting into the Database", Parser.class.getName());
			e.printStackTrace();
		}
		System.out.println("Entries cleaned: " + dbList.size());
	}
}

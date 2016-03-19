package de.benni.rssparser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Hello world!
 *
 */
public class Cleaner 
{
	private final static Logger LOGGER = Logger.getLogger(Cleaner.class.getName());
	
	public void cleanSPARQLData(){
		String myDriver = "com.mysql.jdbc.Driver";
		String myUrl = "jdbc:mysql://localhost";
		List<Company> companies = new ArrayList<>();
		List<Company> newCompanies = new ArrayList<>();
		String[] orgs = {"AG", "GmbH", "Inc.", "KG", "Group"};
		try {
			Class.forName(myDriver);
		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Driver not found", Cleaner.class.getName());
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
				
				Company e = new Company(id, name);

				companies.add(e);
			}
			rs.close();
		}catch(Exception e){
			LOGGER.log(Level.SEVERE, "Error in Database handle", Cleaner.class.getName());
			e.printStackTrace();
		}
		
		for (Company companyObj : companies) {
			String name = companyObj.getName();
			//Remove language tag from SPARQL label
			if(name.contains("@en")){
				companyObj.setName(name.substring(0, name.length()-3));
			}
			for (String org : orgs) {
				if(name.contains(org)){
					String newName = companyObj.getName().replace(org, "");
					newName.replaceAll("\\s+$", "");
					Company newCompName = new Company(0, newName);
					
					newCompanies.add(newCompName);
				}
			}
		}
		
		try (Connection conn = DriverManager.getConnection(myUrl, "root", "");) {
			conn.setAutoCommit(false);
			for (Company c : companies) {
				final String updateQuery = "UPDATE newsdb.companies SET name = ? WHERE id= ?";
				try (final PreparedStatement statement = conn.prepareStatement(updateQuery)) {
					statement.setString(1, c.getName());
					statement.setInt(2, c.getId());
					statement.executeUpdate();
				}
			}
			for (Company c : newCompanies) {
				final String insertQuery = "INSERT INTO newsdb.companies (name) values (?)";
				try (final PreparedStatement statement = conn.prepareStatement(insertQuery)) {
					statement.setString(1, c.getName());
					statement.executeUpdate();
				}
			}
			conn.commit();
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error in Database update", Cleaner.class.getName());
			e.printStackTrace();
		}
		
		System.out.println("List of Companies cleaned");
	}
	
}

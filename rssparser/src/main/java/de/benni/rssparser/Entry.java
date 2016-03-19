package de.benni.rssparser;

import java.util.Date;

public class Entry {
	private int id;
	private String hash;
	private String title;
	private String link;
	private Source source;
	private String content;
	private String cleanContent;
	private Date date;

	public enum Source {
		HANDELSBLATT, YAHOO, WIWO, MANAGER, FAZ, FINANZNACHRICHTEN, BOERSENZEITUNG, ADHOC, WALLSTREET
	}

	public Entry(String title, String link, Source source, String content, Date date) {
		this.title = title;
		this.link = link;
		this.source = source;
		this.content = content;
		this.date = date;
		this.hash = title.hashCode() + "_" + date.hashCode() + "_" + link.hashCode();
	}
	
	public int getId(){
		return id;
	}
	
	public void setId(int id){
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	public String getCleanContent() {
		return cleanContent;
	}

	public void setCleanContent(String cleanContent) {
		this.cleanContent = cleanContent;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public static Source getSourceForUrl(int counter) {
		switch (counter) {
			case 0:
				return Source.YAHOO;
			case 1:
				return Source.HANDELSBLATT;
			case 2:
				return Source.WIWO;
			case 3:
				return Source.MANAGER;
			case 4:
				return Source.FAZ;
			case 5:
				return Source.FINANZNACHRICHTEN;
			case 6:
				return Source.BOERSENZEITUNG;
			case 7:
				return Source.ADHOC;
			case 8:
				return Source.WALLSTREET;
			default:
				return null;
		}
	}

	public void setSourceOnString(String source){
		switch (source) {
			case "YAHOO":
				this.source = Source.YAHOO;
			case "HANDELSBLATT":
				this.source = Source.HANDELSBLATT;
			case "WIWO":
				this.source = Source.WIWO;
			case "MANAGER":
				this.source = Source.MANAGER;
			case "FAZ":
				this.source = Source.FAZ;
			case "FINANZNACHRICHTEN":
				this.source = Source.FINANZNACHRICHTEN;
			case "BOERSENZEITUNG":
				this.source = Source.BOERSENZEITUNG;
			case "ADHOC":
				this.source = Source.ADHOC;
			case "WALLSTREET":
				this.source = Source.WALLSTREET;
			default:
				this.source =  null;
		}
	}
}

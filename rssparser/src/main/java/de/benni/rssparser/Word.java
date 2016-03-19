package de.benni.rssparser;

public class Word {
	private String content;
	private int batch;
	private String labelOwn;
	private String labelStan;
	private String hash;
	
	public Word(String content){
		this.setContent(content);
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public int getBatch() {
		return batch;
	}

	public void setBatch(int batch) {
		this.batch = batch;
	}

	public String getLabelOwn() {
		return labelOwn;
	}

	public void setLabelOwn(String label) {
		this.labelOwn = label;
	}

	public String getLabelStan() {
		return labelStan;
	}

	public void setLabelStan(String labelStan) {
		this.labelStan = labelStan;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}
}

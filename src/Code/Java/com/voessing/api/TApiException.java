package com.voessing.api;

public class TApiException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


	private TApiEventGenerator.Event event = null;
	private String addMessage = "";
	
	public TApiException(TApiEventGenerator.Event event) {
		this(event, "");
	}
	
	public TApiException(TApiEventGenerator.Event event, String addMessage) {
		super(event.getString() + ((addMessage!=null && !addMessage.isEmpty()) ? " (" + addMessage + ")": ""));
		this.event = event;
		this.addMessage = addMessage;
	}
	
	TApiEventGenerator.Event getEvent() {
		return this.event;
	}
	
	String getAddMessage() {
		return addMessage;
	}
	
}

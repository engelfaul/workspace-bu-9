package com.ibm.gbs.ams.integration.adapters;

public class IseriesInvocationException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public IseriesInvocationException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}
	
	public IseriesInvocationException(String arg0) {
		super(arg0);
	}
	
}

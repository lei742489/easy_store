package org.jeecgframework.boot.easy_store_boot.app.exception;

public class AppRunTimeException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public AppRunTimeException(String message){
		super(message);
	}
	
	public AppRunTimeException(Throwable cause)
	{
		super(cause);
	}
	
	public AppRunTimeException(String message, Throwable cause)
	{
		super(message,cause);
	}
}

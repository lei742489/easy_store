package org.jeecgframework.boot.easy_store_boot.app.exception;

public class ApiNoAuthException extends RuntimeException{

    private static final long serialVersionUID = 1L;

    public ApiNoAuthException(String message){
        super(message);
    }

    public ApiNoAuthException(Throwable cause)
    {
        super(cause);
    }

    public ApiNoAuthException(String message,Throwable cause)
    {
        super(message,cause);
    }
}

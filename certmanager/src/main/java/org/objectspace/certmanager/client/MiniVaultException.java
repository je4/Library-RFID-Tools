package org.objectspace.certmanager.client;

/**
 * Exception thrown when MiniVault operations fail.
 */
public class MiniVaultException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public MiniVaultException(String message) {
        super(message);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public MiniVaultException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public MiniVaultException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

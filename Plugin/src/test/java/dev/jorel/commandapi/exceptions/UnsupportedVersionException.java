package dev.jorel.commandapi.exceptions;

/**
 * Stand-in for CommandAPI's own exception. CommandAPI is downloaded at runtime, so the plugin only knows this
 * type by name; the test doubles the name to cover the branch which recognises it.
 */
public class UnsupportedVersionException extends RuntimeException {

    public UnsupportedVersionException(String message) {
        super(message);
    }
}

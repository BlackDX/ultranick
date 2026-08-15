package net.ultranick.api.model;

/**
 * Result enum for disguise/nick operations.
 *
 * @author Chatbxn
 */
public enum NickResult {
    /**
     * Operation completed successfully.
     */
    SUCCESS("Nick operation successful."),

    /**
     * The player is already disguised/nicked.
     */
    ALREADY_NICKED("Player is already nicked."),

    /**
     * The player is currently not nicked.
     */
    NOT_NICKED("Player is not nicked."),

    /**
     * The chosen nickname is already occupied or online.
     */
    NAME_TAKEN("This nickname is already in use."),

    /**
     * The chosen nickname contains invalid characters or forbidden words.
     */
    INVALID_NAME("The nickname is invalid."),

    /**
     * The player is currently on command cooldown.
     */
    COOLDOWN("Please wait before changing your nick again."),

    /**
     * The player does not have permission for this action.
     */
    NO_PERMISSION("You do not have permission."),

    /**
     * An internal or database error occurred.
     */
    ERROR("An internal error occurred.");

    private final String defaultMessage;

    NickResult(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public boolean isSuccessful() {
        return this == SUCCESS;
    }
}

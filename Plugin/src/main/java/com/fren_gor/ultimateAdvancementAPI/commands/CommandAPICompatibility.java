package com.fren_gor.ultimateAdvancementAPI.commands;

import org.jetbrains.annotations.NotNull;

/**
 * Recognises the failures which mean the loaded <a href="https://github.com/JorelAli/CommandAPI">CommandAPI</a>
 * simply doesn't know the running server build, as opposed to a real error in the plugin's command code.
 * <p>CommandAPI resolves its NMS implementation while loading and throws when the server version has no
 * matching one; because that implementation is compiled against a specific server build, a version mismatch
 * also surfaces as a {@link LinkageError} for a {@code net.minecraft} class which that build no longer has.
 * Both shapes are expected on an unsupported server and are handled by the built-in
 * {@link BukkitAdvancementCommand} fallback, so they must not print a raw stack trace at the default log level.
 */
public final class CommandAPICompatibility {

    private static final String UNSUPPORTED_VERSION_EXCEPTION = "dev.jorel.commandapi.exceptions.UnsupportedVersionException";
    private static final String COMMANDAPI_PACKAGE = "dev.jorel.commandapi";
    private static final String MINECRAFT_PACKAGE = "net/minecraft/";
    private static final String MINECRAFT_PACKAGE_SOURCE_FORM = "net.minecraft.";

    /**
     * Whether the provided failure means CommandAPI cannot run on this server build.
     *
     * @param throwable The failure thrown while loading CommandAPI.
     * @return Whether the failure is an expected unsupported-server-build failure.
     */
    public static boolean isUnsupportedServerBuild(@NotNull Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (UNSUPPORTED_VERSION_EXCEPTION.equals(current.getClass().getName())) {
                return true;
            }
            if (isMissingMinecraftClass(current) && isFromCommandAPI(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissingMinecraftClass(@NotNull Throwable throwable) {
        if (!(throwable instanceof LinkageError) && !(throwable instanceof ClassNotFoundException)) {
            return false;
        }
        String message = throwable.getMessage();
        return message != null && (message.startsWith(MINECRAFT_PACKAGE) || message.startsWith(MINECRAFT_PACKAGE_SOURCE_FORM));
    }

    private static boolean isFromCommandAPI(@NotNull Throwable throwable) {
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (element.getClassName().startsWith(COMMANDAPI_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    private CommandAPICompatibility() {
        throw new UnsupportedOperationException("Utility class.");
    }
}

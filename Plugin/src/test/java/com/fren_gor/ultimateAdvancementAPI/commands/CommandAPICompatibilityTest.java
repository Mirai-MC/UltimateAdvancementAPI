package com.fren_gor.ultimateAdvancementAPI.commands;

import dev.jorel.commandapi.exceptions.UnsupportedVersionException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandAPICompatibilityTest {

    private static final String UNSUPPORTED_MESSAGE =
            "The CommandAPI doesn't support any version before Paper 1.20.6 build 79. Please update your server!";

    @Test
    public void testUnsupportedVersionExceptionIsExpected() {
        assertTrue(CommandAPICompatibility.isUnsupportedServerBuild(new UnsupportedVersionException(UNSUPPORTED_MESSAGE)));
    }

    @Test
    public void testMissingMinecraftClassFromCommandAPIIsExpected() {
        NoClassDefFoundError missingFuelValues = new NoClassDefFoundError("net/minecraft/world/level/block/entity/FuelValues");
        missingFuelValues.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("dev.jorel.commandapi.nms.NMS_26_Common", "<clinit>", "NMS_26_Common.java", 288),
                new StackTraceElement("dev.jorel.commandapi.nms.PaperNMS_26_2", "bukkitNMS", "PaperNMS_26_2.java", 36)
        });
        assertTrue(CommandAPICompatibility.isUnsupportedServerBuild(
                new IllegalStateException(UNSUPPORTED_MESSAGE, missingFuelValues)));

        ClassNotFoundException sourceForm = new ClassNotFoundException("net.minecraft.world.level.block.entity.FuelValues");
        sourceForm.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("dev.jorel.commandapi.nms.NMS_26_Common", "<clinit>", "NMS_26_Common.java", 288)
        });
        assertTrue(CommandAPICompatibility.isUnsupportedServerBuild(sourceForm));
    }

    @Test
    public void testMissingMinecraftClassOutsideCommandAPIIsNotExpected() {
        NoClassDefFoundError missingFuelValues = new NoClassDefFoundError("net/minecraft/world/level/block/entity/FuelValues");
        missingFuelValues.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.fren_gor.ultimateAdvancementAPI.AdvancementPlugin", "onLoad", "AdvancementPlugin.java", 52)
        });
        assertFalse(CommandAPICompatibility.isUnsupportedServerBuild(missingFuelValues));
    }

    @Test
    public void testCommandAPIFailureOnAnotherClassIsNotExpected() {
        NoClassDefFoundError missingGuava = new NoClassDefFoundError("com/google/common/base/Preconditions");
        missingGuava.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("dev.jorel.commandapi.CommandAPI", "onLoad", "CommandAPI.java", 20)
        });
        assertFalse(CommandAPICompatibility.isUnsupportedServerBuild(missingGuava));
    }

    @Test
    public void testUnrelatedFailuresAreNotExpected() {
        assertFalse(CommandAPICompatibility.isUnsupportedServerBuild(new RuntimeException("boom")));
        assertFalse(CommandAPICompatibility.isUnsupportedServerBuild(new IllegalStateException("outer", new RuntimeException("inner"))));
    }
}

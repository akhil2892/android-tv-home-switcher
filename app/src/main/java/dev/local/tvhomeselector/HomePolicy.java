package dev.local.tvhomeselector;

import java.util.Set;

/** Pure decision logic. A resolver that is not an eligible candidate is never called a launcher. */
public final class HomePolicy {
    private HomePolicy() { }

    public static boolean isCandidate(String resolved, Set<String> accessibleComponents) {
        return resolved != null && accessibleComponents.contains(resolved);
    }

    public static String verification(String desired, String resolved, Set<String> accessible) {
        if (desired == null) return "No selection requested.";
        if (!accessible.contains(desired)) return "The requested launcher is absent, disabled, or inaccessible. No change is verified.";
        if (!isCandidate(resolved, accessible)) return "HOME resolves to a chooser, fallback, or unrecognized activity. No default change is verified.";
        if (desired.equals(resolved)) return "The requested component now resolves HOME. Physical remote behavior and reboot persistence still need testing.";
        return "The requested component does not resolve HOME. Selection may have been cancelled, unsupported, or overridden.";
    }
}

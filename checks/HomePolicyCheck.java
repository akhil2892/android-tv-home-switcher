import dev.local.tvhomeselector.HomePolicy;
import java.util.Set;

public final class HomePolicyCheck {
    public static void main(String[] args) {
        Set<String> candidates = Set.of("stock/Main", "thirdparty/Main");
        require(!HomePolicy.isCandidate("android/ResolverActivity", candidates), "Resolver must not be a launcher");
        require(!HomePolicy.isCandidate(null, candidates), "Missing resolution is unknown");
        require(HomePolicy.verification("thirdparty/Main", "stock/Main", candidates).contains("does not resolve"), "Opening an activity is not selection");
        require(HomePolicy.verification("thirdparty/Main", "thirdparty/Main", candidates).contains("still need testing"), "A match cannot prove remote or reboot behavior");
        require(HomePolicy.verification("removed/Main", "stock/Main", candidates).contains("absent"), "Removed launchers cannot be verified");
        require(HomePolicy.verification("thirdparty/Main", "android/ResolverActivity", candidates).contains("No default change"), "Chooser is not a saved default");
        System.out.println("HomePolicyCheck: 6 meaningful status/verification checks passed.");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

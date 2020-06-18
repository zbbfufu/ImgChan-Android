package nya.miku.wishmaster.http.stormwall;

import java.util.HashMap;
import java.util.Map;

/** Cookie token generator */
public class StormwallTokenGenerator {
    private static final char[] ALPHABET = "0123456789qwertyuiopasdfghjklzxcvbnm:?!".toCharArray();
    private static final Map<Character, Integer> charMap = new HashMap<>();
    static {
        for (int i = 0; i < ALPHABET.length; i++) {
            charMap.put(ALPHABET[i], i);
        }
    }

    private static char shift(int modifier, char input) {
        if (!charMap.containsKey(input)) return input;
        int newCharPos = (charMap.get(input) + modifier + ALPHABET.length) % ALPHABET.length;
        return ALPHABET[newCharPos];
    }

    public static String encrypt(int cK, String сЕ) {
        StringBuilder result = new StringBuilder();
        int modifier = cK;
        for (int i = 0; i < сЕ.length(); i++) {
            result.append(shift(-1 * modifier, сЕ.charAt(i)));
            modifier++;
            if (modifier >= ALPHABET.length) {
                modifier = 0;
            }
        }
        return result.toString();
    }
}

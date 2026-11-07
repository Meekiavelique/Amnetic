package com.meekdev.amnetic.client.content;

// refuses json nested deeper than ContentLimits.JSON_DEPTH before a recursive parser overflows its stack on it
public final class JsonDepth {

    private JsonDepth() {}

    public static void check(byte[] utf8, int from, int length) {
        int depth = 0;
        boolean quoted = false;
        for (int i = from; i < from + length; i++) {
            byte c = utf8[i];
            if (quoted) {
                if (c == '\\') i++;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{' || c == '[') {
                if (++depth > ContentLimits.JSON_DEPTH) {
                    throw new RejectedContentException("json nested deeper than " + ContentLimits.JSON_DEPTH + " levels");
                }
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
    }
}

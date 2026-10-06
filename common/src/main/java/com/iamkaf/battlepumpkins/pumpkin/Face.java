package com.iamkaf.battlepumpkins.pumpkin;

import java.util.List;

/**
 * Which of the 16×16 pixels on a pumpkin's front are carved out. Pixel {@code (x, y)} is bit {@code y * 16 + x},
 * row 0 at the top, packed into four longs so it syncs as four entity data values.
 */
public record Face(long a, long b, long c, long d) {
    public static final int SIZE = 16;
    public static final Face BLANK = new Face(0L, 0L, 0L, 0L);
    /** The face of a vanilla carved pumpkin, which a pumpkin gets when it is caught. */
    public static final Face VANILLA = new Face(0x0E70043000000000L, 0x3C303E781E781E78L, 0x7FFC3FFC29280000L, 0x0000000008486ED8L);

    public static Face of(long[] words) {
        if (words.length != 4) {
            throw new IllegalArgumentException("A face needs 4 words, got " + words.length);
        }
        return new Face(words[0], words[1], words[2], words[3]);
    }

    /** Reads 16 rows of 16 characters, {@code #} for carved and {@code .} for skin. */
    public static Face parse(List<String> rows) {
        if (rows.size() != SIZE) {
            throw new IllegalArgumentException("A face needs " + SIZE + " rows, got " + rows.size());
        }
        long[] words = new long[4];
        for (int y = 0; y < SIZE; y++) {
            String row = rows.get(y);
            if (row.length() != SIZE) {
                throw new IllegalArgumentException("Row " + y + " needs " + SIZE + " characters, got " + row.length());
            }
            for (int x = 0; x < SIZE; x++) {
                char pixel = row.charAt(x);
                if (pixel == '#') {
                    int bit = y * SIZE + x;
                    words[bit >> 6] |= 1L << (bit & 63);
                } else if (pixel != '.') {
                    throw new IllegalArgumentException("Row " + y + " has '" + pixel + "'; use '#' or '.'");
                }
            }
        }
        return of(words);
    }

    public long[] words() {
        return new long[]{a, b, c, d};
    }

    public boolean carved(int x, int y) {
        int bit = y * SIZE + x;
        long word = switch (bit >> 6) {
            case 0 -> a;
            case 1 -> b;
            case 2 -> c;
            default -> d;
        };
        return (word >>> (bit & 63) & 1L) != 0L;
    }

    /** Whether a pixel's four neighbours are all carved or beyond the face's edge. Such pixels glow brightest. */
    public boolean surrounded(int x, int y) {
        return carvedOrOutside(x - 1, y) && carvedOrOutside(x + 1, y) && carvedOrOutside(x, y - 1) && carvedOrOutside(x, y + 1);
    }

    private boolean carvedOrOutside(int x, int y) {
        return x < 0 || y < 0 || x >= SIZE || y >= SIZE || carved(x, y);
    }

    public Face with(int x, int y, boolean carved) {
        long[] words = words();
        int bit = y * SIZE + x;
        if (carved) {
            words[bit >> 6] |= 1L << (bit & 63);
        } else {
            words[bit >> 6] &= ~(1L << (bit & 63));
        }
        return of(words);
    }

    public boolean isBlank() {
        return (a | b | c | d) == 0L;
    }
}

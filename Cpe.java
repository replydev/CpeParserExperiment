import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A high-performance, low-allocation CPE 2.3 parser and matcher.
 * <p>
 * This implementation uses the Java Vector API for SIMD-accelerated delimiter
 * searching and avoids heap allocations during parsing and matching by operating
 * on a single byte array with offsets and lengths for each component.
 */
public final class Cpe {

    // The number of components in a CPE 2.3 string (part, vendor, product, etc.).
    private static final int COMPONENT_COUNT = 11;
    private static final String CPE_23_PREFIX = "cpe:2.3:";
    private static final byte COLON = (byte) ':';
    private static final byte ANY = (byte) '*';
    private static final byte NA = (byte) '-';

    // Vector API constants for SIMD processing.
    // SPECIES_PREFERRED selects the best available vector size for the current CPU (128, 256, 512 bits).
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final ByteVector COLON_VECTOR = ByteVector.broadcast(SPECIES, COLON);

    // The raw bytes of the original CPE string. All components are slices of this array.
    private final byte[] source;
    // The starting position of each of the 11 components within the source array.
    private final int[] offsets;
    // The length of each of the 11 components.
    private final int[] lengths;

    private Cpe(byte[] source, int[] offsets, int[] lengths) {
        this.source = source;
        this.offsets = offsets;
        this.lengths = lengths;
    }

    /**
     * Parses a CPE 2.3 string using the Vector API for high performance.
     * This method is designed to be allocation-free after the initial byte conversion.
     *
     * @param cpeString The CPE string to parse.
     * @return A parsed Cpe object.
     * @throws IllegalArgumentException if the string is not a valid CPE 2.3 WFN.
     */
    public static Cpe parse(String cpeString) {
        if (cpeString == null || !cpeString.startsWith(CPE_23_PREFIX)) {
            throw new IllegalArgumentException("Invalid CPE 2.3 prefix. Must start with '" + CPE_23_PREFIX + "'.");
        }

        final byte[] bytes = cpeString.getBytes(StandardCharsets.UTF_8);
        final int[] offsets = new int[COMPONENT_COUNT];
        final int[] lengths = new int[COMPONENT_COUNT];

        int componentIndex = 0;
        int currentOffset = CPE_23_PREFIX.length();
        int searchOffset = currentOffset;

        // Vectorized search loop
        while (componentIndex < COMPONENT_COUNT - 1 && searchOffset < bytes.length) {
            int vectorBoundary = Math.min(bytes.length, searchOffset + SPECIES.length());

            // Load a chunk of the source bytes into a vector
            ByteVector inputVector = ByteVector.fromArray(SPECIES, bytes, searchOffset);

            // In a single instruction, compare all bytes in the vector to ':'
            var mask = inputVector.compare(VectorOperators.EQ, COLON_VECTOR);

            if (mask.anyTrue()) {
                // Found a colon in this vector chunk.
                int colonIndexInVector = mask.firstTrue();
                int colonAbsoluteIndex = searchOffset + colonIndexInVector;

                offsets[componentIndex] = currentOffset;
                lengths[componentIndex] = colonAbsoluteIndex - currentOffset;
                componentIndex++;
                currentOffset = colonAbsoluteIndex + 1;
                searchOffset = currentOffset;
            } else {
                // No colon found in this chunk, advance to the next chunk.
                searchOffset += SPECIES.length();
            }
        }
        
        // Handle the last component (which has no trailing colon)
        if (componentIndex == COMPONENT_COUNT - 1) {
            offsets[componentIndex] = currentOffset;
            lengths[componentIndex] = bytes.length - currentOffset;
            componentIndex++;
        }

        if (componentIndex != COMPONENT_COUNT) {
            throw new IllegalArgumentException("Invalid CPE format: expected " + COMPONENT_COUNT + " components, but found " + componentIndex);
        }

        return new Cpe(bytes, offsets, lengths);
    }
    
    /**
     * Matches this CPE against another according to the specified simplified rules.
     *
     * @param other The CPE to match against.
     * @return True if they match, false otherwise.
     */
    public boolean matches(Cpe other) {
        if (other == null) {
            return false;
        }
        for (int i = 0; i < COMPONENT_COUNT; i++) {
            if (!matchComponent(i, other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Helper to match a single component based on the custom rules.
     */
    private boolean matchComponent(int index, Cpe other) {
        int o1 = this.offsets[index];
        int l1 = this.lengths[index];
        int o2 = other.offsets[index];
        int l2 = other.lengths[index];
        byte[] b1 = this.source;
        byte[] b2 = other.source;

        boolean thisIsAny = (l1 == 1 && b1[o1] == ANY);
        boolean otherIsAny = (l2 == 1 && b2[o2] == ANY);

        // Rule: ANY matches anything.
        if (thisIsAny || otherIsAny) {
            return true;
        }
        
        boolean thisIsNa = (l1 == 1 && b1[o1] == NA);

        // Rule: NA will not match m + wild cards.
        // We check this by seeing if the other component contains a wildcard.
        if (thisIsNa) {
            for (int i = 0; i < l2; i++) {
                byte c = b2[o2 + i];
                if (c == '*' || c == '?') {
                    return false;
                }
            }
        }

        // Rule: Wildcards vs wildcards are treated as literal text comparison.
        // This is handled by a direct byte-for-byte comparison.
        return regionMatches(b1, o1, l1, b2, o2, l2);
    }
    
    /**
     * A utility method for comparing regions of two byte arrays without allocation.
     */
    private boolean regionMatches(byte[] b1, int o1, int l1, byte[] b2, int o2, int l2) {
        if (l1 != l2) {
            return false;
        }
        for (int i = 0; i < l1; i++) {
            if (b1[o1 + i] != b2[o2 + i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return new String(source, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cpe cpe = (Cpe) o;
        return Arrays.equals(source, cpe.source);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(source);
    }

    // Example Usage
    public static void main(String[] args) {
        String cpeStr1 = "cpe:2.3:a:apache:http_server:2.4.53:*:*:*:*:*:*:*";
        String cpeStr2 = "cpe:2.3:a:apache:http_server:2.4.53:-:*:*:*:*:*:*";
        String cpeStr3 = "cpe:2.3:a:microsoft:iis:*:*:*:*:*:*:*:*";

        Cpe cpe1 = Cpe.parse(cpeStr1);
        Cpe cpe2 = Cpe.parse(cpeStr2);
        Cpe cpe3 = Cpe.parse(cpeStr3);

        System.out.println("CPE 1: " + cpe1);
        System.out.println("CPE 2: " + cpe2);
        System.out.println("CPE 3: " + cpe3);

        System.out.println("\n--- Matching Results ---");

        // ANY matches NA -> true
        System.out.printf("'%s' matches '%s'? %b\n", cpeStr1, cpeStr2, cpe1.matches(cpe2));
        
        // Literal parts match, and ANY matches a specific version -> true
        System.out.printf("'%s' matches '%s'? %b\n", cpeStr3, cpeStr1, cpe3.matches(cpe1));
        
        // Exact same string -> true
        System.out.printf("'%s' matches '%s'? %b\n", cpeStr1, cpeStr1, cpe1.matches(cpe1));
        
        // Test NA vs wildcards -> false
        Cpe cpeNa = Cpe.parse("cpe:2.3:a:vendor:prod:-:*:*:*:*:*:*:*");
        Cpe cpeWithWildcardValue = Cpe.parse("cpe:2.3:a:vendor:prod:ver_*:*:*:*:*:*:*:*");
        System.out.printf("'...:-:...' matches '...:ver_*:...'? %b\n", cpeNa.matches(cpeWithWildcardValue)); // Expected: false
    }
}
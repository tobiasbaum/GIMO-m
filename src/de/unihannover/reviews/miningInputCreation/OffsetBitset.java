package de.unihannover.reviews.miningInputCreation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class OffsetBitset {

    private int size;
    private int offset;
    private int[] afterOffset;
    private int[] beforeOffset;

    public OffsetBitset() {
        this.afterOffset = new int[1];
        this.beforeOffset = new int[1];
    }

    public void add(int value) {
        if (this.size == 0) {
            this.offset = value & 0xFFFFFFE0;
        }
        int diff;
        int wordIndex;
        int[] arr;
        if (value >= this.offset) {
            diff = value - this.offset;
            wordIndex = diff / 32;
            if (wordIndex >= this.afterOffset.length) {
                this.afterOffset = this.enlarge(this.afterOffset, wordIndex);
            }
            arr = this.afterOffset;
        } else {
            diff = this.offset - value - 1;
            wordIndex = diff / 32;
            if (wordIndex >= this.beforeOffset.length) {
                this.beforeOffset = this.enlarge(this.beforeOffset, wordIndex);
            }
            arr = this.beforeOffset;
        }
        final int mask = 1 << (diff % 32);
        final int oldValue = arr[wordIndex];
        final int newValue = oldValue | mask;
        if (oldValue != newValue) {
            this.size++;
            arr[wordIndex] = newValue;
        }
    }

    private int[] enlarge(int[] oldArray, int minNeededIndex) {
        return Arrays.copyOf(oldArray, Integer.highestOneBit(minNeededIndex + 1) * 2);
    }

    public void removeAll(OffsetBitset other) {
        final int indexDiff = (other.offset - this.offset) / 32;

        if (indexDiff >= 0) {
            this.subtractRange(this.beforeOffset, other.beforeOffset, 0, indexDiff, Math.min(this.beforeOffset.length, other.beforeOffset.length - indexDiff));
            this.subtractRangeReversed(this.afterOffset, other.beforeOffset,
                            Math.max(0, indexDiff - other.beforeOffset.length),
                            Math.max(0, indexDiff - this.afterOffset.length),
                            indexDiff);
            this.subtractRange(this.afterOffset, other.afterOffset, indexDiff, 0, Math.min(this.afterOffset.length - indexDiff, other.afterOffset.length));
        } else {
            this.subtractRange(this.beforeOffset, other.beforeOffset, -indexDiff, 0, Math.min(this.beforeOffset.length + indexDiff, other.beforeOffset.length));
            this.subtractRangeReversed(this.beforeOffset, other.afterOffset,
                            Math.max(0, -indexDiff - other.afterOffset.length),
                            Math.max(0, -indexDiff - this.beforeOffset.length),
                            -indexDiff);
            this.subtractRange(this.afterOffset, other.afterOffset, 0, -indexDiff, Math.min(this.afterOffset.length, other.afterOffset.length + indexDiff));
        }
    }

    private void subtractRangeReversed(int[] ourArray, int[] otherArray, int startOur, int startOther, int maxLength) {
        final int length = maxLength - startOur - startOther;
        for (int i = 0; i < length; i++) {
            final int offOur = startOur + i;
            final int oldValue = ourArray[offOur];
            final int newValue = oldValue & ~Integer.reverse(otherArray[startOther + length - i - 1]);
            ourArray[offOur] = newValue;
            this.size = this.size + Integer.bitCount(newValue) - Integer.bitCount(oldValue);
        }
    }

    private void subtractRange(int[] ourArray, int[] otherArray, int startOur, int startOther, int length) {
        for (int i = 0; i < length; i++) {
            final int offOur = startOur + i;
            final int oldValue = ourArray[offOur];
            final int newValue = oldValue & ~otherArray[startOther + i];
            ourArray[offOur] = newValue;
            this.size = this.size + Integer.bitCount(newValue) - Integer.bitCount(oldValue);
        }
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public int size() {
        return this.size;
    }

    public Set<Integer> toSet() {
        final Set<Integer> ret = new LinkedHashSet<>();
        for (int i = 0; i < this.beforeOffset.length; i++) {
            final int curWord = this.beforeOffset[i];
            final int base = this.offset - i * 32 - 1;
            addIfSet(ret, curWord, base, 0x00000001, 0);
            addIfSet(ret, curWord, base, 0x00000002, -1);
            addIfSet(ret, curWord, base, 0x00000004, -2);
            addIfSet(ret, curWord, base, 0x00000008, -3);
            addIfSet(ret, curWord, base, 0x00000010, -4);
            addIfSet(ret, curWord, base, 0x00000020, -5);
            addIfSet(ret, curWord, base, 0x00000040, -6);
            addIfSet(ret, curWord, base, 0x00000080, -7);
            addIfSet(ret, curWord, base, 0x00000100, -8);
            addIfSet(ret, curWord, base, 0x00000200, -9);
            addIfSet(ret, curWord, base, 0x00000400, -10);
            addIfSet(ret, curWord, base, 0x00000800, -11);
            addIfSet(ret, curWord, base, 0x00001000, -12);
            addIfSet(ret, curWord, base, 0x00002000, -13);
            addIfSet(ret, curWord, base, 0x00004000, -14);
            addIfSet(ret, curWord, base, 0x00008000, -15);
            addIfSet(ret, curWord, base, 0x00010000, -16);
            addIfSet(ret, curWord, base, 0x00020000, -17);
            addIfSet(ret, curWord, base, 0x00040000, -18);
            addIfSet(ret, curWord, base, 0x00080000, -19);
            addIfSet(ret, curWord, base, 0x00100000, -20);
            addIfSet(ret, curWord, base, 0x00200000, -21);
            addIfSet(ret, curWord, base, 0x00400000, -22);
            addIfSet(ret, curWord, base, 0x00800000, -23);
            addIfSet(ret, curWord, base, 0x01000000, -24);
            addIfSet(ret, curWord, base, 0x02000000, -25);
            addIfSet(ret, curWord, base, 0x04000000, -26);
            addIfSet(ret, curWord, base, 0x08000000, -27);
            addIfSet(ret, curWord, base, 0x10000000, -28);
            addIfSet(ret, curWord, base, 0x20000000, -29);
            addIfSet(ret, curWord, base, 0x40000000, -30);
            addIfSet(ret, curWord, base, 0x80000000, -31);
        }
        for (int i = 0; i < this.afterOffset.length; i++) {
            final int curWord = this.afterOffset[i];
            final int base = this.offset + i * 32;
            addIfSet(ret, curWord, base, 0x00000001, 0);
            addIfSet(ret, curWord, base, 0x00000002, 1);
            addIfSet(ret, curWord, base, 0x00000004, 2);
            addIfSet(ret, curWord, base, 0x00000008, 3);
            addIfSet(ret, curWord, base, 0x00000010, 4);
            addIfSet(ret, curWord, base, 0x00000020, 5);
            addIfSet(ret, curWord, base, 0x00000040, 6);
            addIfSet(ret, curWord, base, 0x00000080, 7);
            addIfSet(ret, curWord, base, 0x00000100, 8);
            addIfSet(ret, curWord, base, 0x00000200, 9);
            addIfSet(ret, curWord, base, 0x00000400, 10);
            addIfSet(ret, curWord, base, 0x00000800, 11);
            addIfSet(ret, curWord, base, 0x00001000, 12);
            addIfSet(ret, curWord, base, 0x00002000, 13);
            addIfSet(ret, curWord, base, 0x00004000, 14);
            addIfSet(ret, curWord, base, 0x00008000, 15);
            addIfSet(ret, curWord, base, 0x00010000, 16);
            addIfSet(ret, curWord, base, 0x00020000, 17);
            addIfSet(ret, curWord, base, 0x00040000, 18);
            addIfSet(ret, curWord, base, 0x00080000, 19);
            addIfSet(ret, curWord, base, 0x00100000, 20);
            addIfSet(ret, curWord, base, 0x00200000, 21);
            addIfSet(ret, curWord, base, 0x00400000, 22);
            addIfSet(ret, curWord, base, 0x00800000, 23);
            addIfSet(ret, curWord, base, 0x01000000, 24);
            addIfSet(ret, curWord, base, 0x02000000, 25);
            addIfSet(ret, curWord, base, 0x04000000, 26);
            addIfSet(ret, curWord, base, 0x08000000, 27);
            addIfSet(ret, curWord, base, 0x10000000, 28);
            addIfSet(ret, curWord, base, 0x20000000, 29);
            addIfSet(ret, curWord, base, 0x40000000, 30);
            addIfSet(ret, curWord, base, 0x80000000, 31);
        }
        return ret;
    }

    private static void addIfSet(Set<Integer> ret, int curWord, int base, int mask, int toAdd) {
        if ((curWord & mask) != 0) {
            ret.add(base + toAdd);
        }
    }

    public boolean contains(int i) {
        //TODO implement more efficiently
        return this.toSet().contains(i);
    }

    public void addAll(OffsetBitset other) {
        final int indexDiff = (other.offset - this.offset) / 32;

        if (this.beforeOffset.length < other.beforeOffset.length - indexDiff) {
        	this.beforeOffset = this.enlarge(this.beforeOffset, other.beforeOffset.length - indexDiff - 1);
        }
        if (this.afterOffset.length < other.afterOffset.length + indexDiff) {
        	this.afterOffset = this.enlarge(this.afterOffset, other.afterOffset.length + indexDiff - 1);
        }

        if (indexDiff >= 0) {
            this.addRange(this.beforeOffset, other.beforeOffset, 0, indexDiff, other.beforeOffset.length - indexDiff);
            this.addRangeReversed(this.afterOffset, other.beforeOffset,
                            Math.max(0, indexDiff - other.beforeOffset.length),
                            Math.max(0, indexDiff - this.afterOffset.length),
                            indexDiff);
            this.addRange(this.afterOffset, other.afterOffset, indexDiff, 0, other.afterOffset.length);
        } else {
            this.addRange(this.beforeOffset, other.beforeOffset, -indexDiff, 0, other.beforeOffset.length);
            this.addRangeReversed(this.beforeOffset, other.afterOffset,
                            Math.max(0, -indexDiff - other.afterOffset.length),
                            Math.max(0, -indexDiff - this.beforeOffset.length),
                            -indexDiff);
            this.addRange(this.afterOffset, other.afterOffset, 0, -indexDiff, other.afterOffset.length + indexDiff);
        }
    }

    private void addRangeReversed(int[] ourArray, int[] otherArray, int startOur, int startOther, int maxLength) {
        final int length = maxLength - startOur - startOther;
        for (int i = 0; i < length; i++) {
            final int offOur = startOur + i;
            final int oldValue = ourArray[offOur];
            final int newValue = oldValue | Integer.reverse(otherArray[startOther + length - i - 1]);
            ourArray[offOur] = newValue;
            this.size = this.size + Integer.bitCount(newValue) - Integer.bitCount(oldValue);
        }
    }

    private void addRange(int[] ourArray, int[] otherArray, int startOur, int startOther, int length) {
        for (int i = 0; i < length; i++) {
            final int offOur = startOur + i;
            final int oldValue = ourArray[offOur];
            final int newValue = oldValue | otherArray[startOther + i];
            ourArray[offOur] = newValue;
            this.size = this.size + Integer.bitCount(newValue) - Integer.bitCount(oldValue);
        }
    }

    public void addAll(Set<Integer> coveredRemarks) {
        for (final Integer i : coveredRemarks) {
            this.add(i);
        }
    }

}

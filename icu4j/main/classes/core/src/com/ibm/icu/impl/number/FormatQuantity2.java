// © 2017 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
package com.ibm.icu.impl.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Represents numbers and digit display properties using Binary Coded Decimal (BCD).
 *
 * @implements {@link FormatQuantity}
 */
public class FormatQuantity2 extends FormatQuantityBCD {

  /**
   * The BCD of the 16 digits of the number represented by this object. Every 4 bits of the long map
   * to one digit. For example, the number "12345" in BCD is "0x12345".
   *
   * <p>Whenever bcd changes internally, {@link #computePrecisionAndCompact()} must be called,
   * except in special cases like setting the digit to zero.
   */
  private long bcd;

  @Override
  public int maxRepresentableDigits() {
    return 16;
  }

  public FormatQuantity2(long input) {
    readLongToBcd(input);
  }

  public FormatQuantity2(int input) {
    readIntToBcd(input);
  }

  public FormatQuantity2(double input) {
    readDoubleToBcd(input);
  }

  public FormatQuantity2(BigInteger input) {
    readBigIntegerToBcd(input);
  }

  public FormatQuantity2(BigDecimal input) {
    readBigDecimalToBcd(input);
  }

  public FormatQuantity2(FormatQuantity2 other) {
    copyFrom(other);
  }

  @Override
  protected void _copyBcdFrom(FormatQuantity _other) {
    FormatQuantity2 other = (FormatQuantity2) _other;
    bcd = other.bcd;
  }

  @Override
  public byte getDigit(int magnitude) {
    return getDigitPos(magnitude - scale);
  }

  /**
   * Returns a single digit from the BCD list. No internal state is changed by calling this method.
   *
   * @param position The position of the digit to pop, counted in BCD units from the least
   *     significant digit. If outside the range [0,16), zero is returned.
   * @return The digit at the specified location.
   */
  private byte getDigitPos(int position) {
    if (position < 0 || position >= 16) return 0;
    return (byte) ((bcd >>> (position * 4)) & 0xf);
  }

  //////////////////////////////////
  ///// BCD ARITHMETIC METHODS /////
  //////////////////////////////////

  @Override
  protected void setToZero() {
    bcd = 0L;
    scale = 0;
    precision = 0;
  }

  /**
   * Sets the internal BCD state to represent the value in the given long.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readLongToBcd(long n) {
    if (n == 0) {
      setToZero();
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    long result = 0L;
    int i = 16;
    for (; n != 0L; n /= 10L, i--) {
      result = (result >>> 4) + ((n % 10) << 60);
    }
    int adjustment = (i > 0) ? i : 0;
    bcd = result >>> (adjustment * 4);
    scale = (i < 0) ? -i : 0;
    computePrecisionAndCompact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given int.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readIntToBcd(int n) {
    if (n == 0) {
      setToZero();
      return;
    } else if (n < 0) {
      flags |= NEGATIVE_FLAG;
      n = -n;
    }

    long result = 0L;
    int i = 16;
    for (; n != 0; n /= 10, i--) {
      result = (result >>> 4) + (((long) n % 10) << 60);
    }
    // ints can't overflow the 16 digits in the BCD, so scale is always zero
    bcd = result >>> (i * 4);
    scale = 0;
    computePrecisionAndCompact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigInteger.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readBigIntegerToBcd(BigInteger n) {
    if (n.signum() == 0) {
      setToZero();
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    if (n.bitLength() < 64) {
      readLongToBcd(n.longValueExact());
      return;
    }

    long result = 0L;
    int i = 16;
    for (; n.signum() != 0; i--) {
      BigInteger[] temp = n.divideAndRemainder(BigInteger.TEN);
      result = (result >>> 4) + (temp[1].longValue() << 60);
      n = temp[0];
    }
    int adjustment = (i > 0) ? i : 0;
    bcd = result >>> (adjustment * 4);
    scale = (i < 0) ? -i : 0;
    computePrecisionAndCompact();
  }

  /**
   * Sets the internal BCD state to represent the value in the given BigDecimal.
   *
   * <p>With all of the readToBcd methods, it is the caller's responsibility to clear out the flags
   * if they want the flags cleared.
   *
   * @param n The value to consume.
   */
  @Override
  protected void readBigDecimalToBcd(BigDecimal n) {
    if (n.signum() == 0) {
      setToZero();
      return;
    } else if (n.signum() == -1) {
      flags |= NEGATIVE_FLAG;
      n = n.negate();
    }

    int fracLength = n.scale();
    n = n.scaleByPowerOfTen(fracLength);
    readBigIntegerToBcd(n.toBigInteger());
    scale -= fracLength;
  }

  /**
   * Returns a long approximating the internal BCD. A long can only represent the integral part of
   * the number.
   *
   * @return A double representation of the internal BCD.
   */
  @Override
  protected long bcdToLong() {
    long result = 0L;
    for (int magnitude = scale + precision - 1; magnitude >= 0; magnitude--) {
      result = result * 10 + getDigitPos(magnitude - scale);
    }
    return result;
  }

  /**
   * This returns a long representing the fraction digits of the number, as required by PluralRules.
   * For example, if we represent the number "1.20" (including optional and required digits), then
   * this function returns "20" if includeTrailingZeros is true or "2" if false.
   */
  @Override
  protected long bcdToFractionLong(boolean includeTrailingZeros) {
    long result = 0L;
    int magnitude = -1;
    for (;
        (magnitude >= scale || (includeTrailingZeros && magnitude >= lReqPos))
            && magnitude >= lOptPos;
        magnitude--) {
      result = result * 10 + getDigitPos(magnitude - scale);
    }
    return result;
  }

  /**
   * Returns a double approximating the internal BCD. The double may not retain all of the
   * information encoded in the BCD if the BCD represents a number out of range of a double.
   *
   * @return A double representation of the internal BCD.
   */
  @Override
  protected double bcdToDouble() {
    long tempLong = 0L;
    for (int shift = (precision - 1); shift >= 0; shift--) {
      tempLong = tempLong * 10 + getDigitPos(shift);
    }
    double result = tempLong;
    if (scale >= 0) {
      int i = scale;
      for (; i >= 9; i -= 9) result *= 1000000000;
      for (; i >= 3; i -= 3) result *= 1000;
      for (; i >= 1; i -= 1) result *= 10;
    } else {
      int i = scale;
      for (; i <= -9; i += 9) result /= 1000000000;
      for (; i <= -3; i += 3) result /= 1000;
      for (; i <= -1; i += 1) result /= 10;
    }
    if (isNegative()) result = -result;
    return result;
  }

  /**
   * Returns a BigDecimal encoding the internal BCD value.
   *
   * @return A BigDecimal representation of the internal BCD.
   */
  @Override
  protected BigDecimal bcdToBigDecimal() {
    long tempLong = 0L;
    for (int shift = (precision - 1); shift >= 0; shift--) {
      tempLong = tempLong * 10 + getDigitPos(shift);
    }
    BigDecimal result = BigDecimal.valueOf(tempLong);
    result = result.scaleByPowerOfTen(scale);
    if (isNegative()) result = result.negate();
    return result;
  }

  @Override
  protected void bcdRoundToMagnitude(int magnitude, RoundingMode roundingMode) {
    // The position in the BCD at which rounding will be performed; digits to the right of position
    // will be rounded away.
    int position = magnitude - scale;

    if (position <= 0) {
      // All digits are to the left of the rounding magnitude.
    } else if (bcd == 0L) {
      // No rounding for zero.
    } else {
      // Perform rounding logic.
      // "leading" = most significant digit to the right of rounding
      // "trailing" = least significant digit to the left of rounding
      byte leadingDigit = getDigitPos(position - 1);
      byte trailingDigit = getDigitPos(position);

      // Compute which section (lower, half, or upper) of the number we are in
      int section = RoundingUtils.SECTION_MIDPOINT;
      if (leadingDigit < 5) {
        section = RoundingUtils.SECTION_LOWER;
      } else if (leadingDigit > 5) {
        section = RoundingUtils.SECTION_UPPER;
      } else {
        for (int p = position - 2; p >= 0; p--) {
          if (getDigitPos(p) != 0) {
            section = RoundingUtils.SECTION_UPPER;
            break;
          }
        }
      }

      boolean roundDown =
          RoundingUtils.getRoundingDirection(
              (trailingDigit % 2) == 0, isNegative(), section, roundingMode.ordinal(), this);

      // Perform truncation
      if (position < 16) {
        bcd >>>= (position * 4); // shift off the rounded digits
      } else {
        bcd = 0L; // all digits are being rounded off
      }
      scale = magnitude;

      // Bubble the result to the higher digits
      if (!roundDown) {
        if (trailingDigit == 9) {
          int bubblePos = 0;
          for (; getDigitPos(bubblePos) == 9; bubblePos++) {}
          // Note: the most digits BCD can have at this point is 15, so bubblePos <= 15
          bcd >>>= (bubblePos * 4); // shift off the trailing 9s
          scale += bubblePos;
        }
        assert getDigitPos(0) != 9;
        bcd += 1; // the addition operation will apply to the trailing digit at position 0
      }
      computePrecisionAndCompact();
    }
  }

  /**
   * Removes trailing zeros from the BCD (adjusting the scale as required) and then computes the
   * precision. The precision is the number of digits in the number up through the greatest nonzero
   * digit.
   *
   * <p>This method must always be called when bcd changes in order for assumptions to be correct in
   * methods like {@link #fractionCount()}.
   */
  private void computePrecisionAndCompact() {
    // Special handling for 0
    if (bcd == 0L) {
      scale = 0;
      precision = 0;
      return;
    }

    // Compact the number (remove trailing zeros)
    int delta = Long.numberOfTrailingZeros(bcd) / 4;
    bcd >>>= delta * 4;
    scale += delta;

    // Compute precision
    precision = 16 - (Long.numberOfLeadingZeros(bcd) / 4);
  }

  @Override
  public String toString() {
    return String.format(
        "<FormatQuantity2 %s:%d:%d:%s %016XE%d>",
        (lOptPos > 1000 ? "max" : String.valueOf(lOptPos)),
        lReqPos,
        rReqPos,
        (rOptPos < -1000 ? "min" : String.valueOf(rOptPos)),
        bcd,
        scale);
  }
}

package com.skyblockgirlfriend.client;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class BasicMathResponder {
	private static final int MAX_EXPRESSION_LENGTH = 256;
	private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;
	private static final List<String> INVALID_MATH_RESPONSES = List.of(
			"That's not real math, silly!",
			"Baby, what kind of math is that?!",
			"Hehe, I don't think numbers work like that!",
			"Sweetie, you're making up math again!",
			"Ummm... wanna try that again?",
			"That's not how math works, dummy~",
			"Babe, even my calculator is confused!",
			"Are you inventing a new branch of mathematics?",
			"Pfft, what am I supposed to do with that?!",
			"Silly goose, that's not a real equation!",
			"Awww, you're trying your best!",
			"Honey, those numbers aren't numbering!",
			"I think your math teacher just fainted.",
			"Wait... what am I even looking at?",
			"That's some Hypixel-level mathematics right there!",
			"Hehe, did you just make that up?",
			"Babe, I can't solve something that doesn't exist!",
			"You're adorable, but that equation isn't!",
			"My brain just did a backflip reading that.",
			"Even the Bazaar can't calculate that!",
			"Sweetheart, that's not an equation, that's a cry for help!",
			"I love you, but I don't love whatever that was.",
			"Did you accidentally summon a math demon?",
			"Hehe, those numbers are fighting each other!",
			"Babe, I think you broke mathematics.",
			"That equation needs a little more love!",
			"Nooo, silly! That's not how you do it!",
			"I'm your girlfriend, not a magician!",
			"Did you type that with your eyes closed?",
			"Awww, let's pretend I didn't see that~",
			"Even Jerry would be confused by that one!",
			"Honey, that's not math, that's Minecraft enchantment table language!",
			"You can't just throw numbers together and hope for the best!",
			"My calculator just filed a complaint.",
			"Hehe, I think you invented a new kind of wrong!",
			"Baby, what did those poor numbers ever do to you?",
			"That's a very creative way to confuse me!",
			"I'm gonna need you to explain that one, cutie.",
			"Wait, are we doing math or casting spells?",
			"Babe, the numbers aren't cooperating!",
			"That equation is giving me emotional damage!",
			"Awww, you almost had me believing that was math!",
			"Sweetie, I don't think even Einstein could help with that.",
			"Did you learn math from the Auction House?",
			"Hehe, you're so silly sometimes!",
			"That looks more like a Wi-Fi password than an equation!",
			"Babe, I'm cute, not capable of solving nonsense!",
			"My last two brain cells are arguing over that one.",
			"That's not math, that's a secret Minecraft crafting recipe!",
			"Try again, cutie! I believe in you~"
	);

	private BasicMathResponder() {
	}

	public static String respond(String input) {
		try {
			BigDecimal result = new Parser(input).parse();
			DecimalFormat format = new DecimalFormat("#,##0.############",
					DecimalFormatSymbols.getInstance(Locale.US));
			format.setRoundingMode(RoundingMode.HALF_UP);
			format.setMaximumFractionDigits(12);
			return format.format(result);
		} catch (DivisionByZeroException exception) {
			return invalidMathResponse();
		} catch (IllegalArgumentException exception) {
			return invalidMathResponse();
		}
	}

	private static String invalidMathResponse() {
		int responseIndex = ThreadLocalRandom.current().nextInt(INVALID_MATH_RESPONSES.size());
		return INVALID_MATH_RESPONSES.get(responseIndex);
	}

	private static final class Parser {
		private final String input;
		private int position;

		private Parser(String input) {
			if (input == null || input.isBlank() || input.length() > MAX_EXPRESSION_LENGTH) {
				throw new IllegalArgumentException("Invalid expression length.");
			}
			this.input = input.replace(",", "");
		}

		private BigDecimal parse() {
			BigDecimal result = expression();
			skipWhitespace();
			if (position != input.length()) {
				throw new IllegalArgumentException("Unexpected character.");
			}
			return result;
		}

		private BigDecimal expression() {
			BigDecimal result = term();
			while (true) {
				skipWhitespace();
				if (match('+')) {
					result = result.add(term());
				} else if (match('-')) {
					result = result.subtract(term());
				} else {
					return result;
				}
			}
		}

		private BigDecimal term() {
			BigDecimal result = factor();
			while (true) {
				skipWhitespace();
				if (match('*') || match('x') || match('X') || match('\u00D7')) {
					result = result.multiply(factor());
				} else if (match('/') || match('\u00F7')) {
					BigDecimal divisor = factor();
					if (divisor.signum() == 0) {
						throw new DivisionByZeroException();
					}
					result = result.divide(divisor, DIVISION_CONTEXT);
				} else {
					return result;
				}
			}
		}

		private BigDecimal factor() {
			skipWhitespace();
			if (match('+')) {
				return factor();
			}
			if (match('-')) {
				return factor().negate();
			}
			if (match('(')) {
				BigDecimal result = expression();
				skipWhitespace();
				if (!match(')')) {
					throw new IllegalArgumentException("Missing closing parenthesis.");
				}
				return result;
			}
			return number();
		}

		private BigDecimal number() {
			skipWhitespace();
			int start = position;
			boolean hasDigit = false;
			boolean hasDecimalPoint = false;
			while (position < input.length()) {
				char character = input.charAt(position);
				if (Character.isDigit(character)) {
					hasDigit = true;
					position++;
				} else if (character == '.' && !hasDecimalPoint) {
					hasDecimalPoint = true;
					position++;
				} else {
					break;
				}
			}
			if (!hasDigit) {
				throw new IllegalArgumentException("Expected a number.");
			}

			BigDecimal result = new BigDecimal(input.substring(start, position));
			if (position < input.length()) {
				char suffix = Character.toLowerCase(input.charAt(position));
				if (suffix == 'k' || suffix == 'm') {
					position++;
					result = result.multiply(suffix == 'k'
							? BigDecimal.valueOf(1_000)
							: BigDecimal.valueOf(1_000_000));
				}
			}
			return result;
		}

		private boolean match(char expected) {
			if (position < input.length() && input.charAt(position) == expected) {
				position++;
				return true;
			}
			return false;
		}

		private void skipWhitespace() {
			while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
				position++;
			}
		}
	}

	private static final class DivisionByZeroException extends IllegalArgumentException {
	}
}

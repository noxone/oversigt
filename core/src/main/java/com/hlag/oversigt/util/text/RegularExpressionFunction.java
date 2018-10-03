package com.hlag.oversigt.util.text;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

class RegularExpressionFunction implements Function<String, String> {
	private final String value;

	RegularExpressionFunction(final String value) {
		this.value = value;
	}

	@Override
	public String apply(String regex) {
		final Matcher matcher = Pattern.compile(regex).matcher(value); // TODO: Cache pattern statically

		// Return empty string if not found
		if (!matcher.find()) {
			return "";
		}

		// Return either the group "return" or the matched string
		String result;
		try {
			result = matcher.group("return");
		} catch (IllegalArgumentException e) {
			result = matcher.group();
		}

		// Making sure to differentiate between no result and empty result.
		return Strings.isNullOrEmpty(result) ? " " : result;
	}
}
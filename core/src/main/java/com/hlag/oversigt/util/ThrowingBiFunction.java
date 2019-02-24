package com.hlag.oversigt.util;

import java.io.IOException;
import java.util.function.BiFunction;

@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {
	@Override
	default R apply(T t, U u) {
		try {
			return applyThrowing(t, u);
		} catch (Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new SneakyException(e);
		}
	}

	R applyThrowing(T t, U u) throws IOException;
}
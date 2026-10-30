package com.codex.blacklight.forge.codec;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Small, project-owned codec contract backed by Forge's FriendlyByteBuf transport. */
public interface StreamCodec<B, T> {
    T decode(B buffer);
    void encode(B buffer, T value);

    static <B, T> StreamCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new StreamCodec<>() {
            public T decode(B buffer) { return decoder.apply(buffer); }
            public void encode(B buffer, T value) { encoder.accept(buffer, value); }
        };
    }

    /** Narrow the buffer input while preserving the same bounded encoder/decoder. */
    default <C extends B> StreamCodec<C, T> cast() {
        return StreamCodec.of(this::encode, this::decode);
    }

    static <B, T> StreamCodec<B, T> unit(T value) {
        return of((buffer, ignored) -> {}, buffer -> value);
    }

    default <R> StreamCodec<B, R> map(Function<T, R> decode, Function<R, T> encode) {
        return of((buffer, value) -> this.encode(buffer, encode.apply(value)),
                buffer -> decode.apply(this.decode(buffer)));
    }

    static <B, T, A> StreamCodec<B, T> composite(StreamCodec<B, A> a, Function<T, A> getA,
                                                Function<A, T> constructor) {
        return a.map(constructor, getA);
    }

    static <B, T, A, C> StreamCodec<B, T> composite(StreamCodec<B, A> a, Function<T, A> getA,
            StreamCodec<B, C> c, Function<T, C> getC, BiFunction<A, C, T> constructor) {
        return of((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); },
                b -> constructor.apply(a.decode(b), c.decode(b)));
    }

    static <B, T, A, C, D> StreamCodec<B, T> composite(StreamCodec<B, A> a, Function<T, A> getA,
            StreamCodec<B, C> c, Function<T, C> getC, StreamCodec<B, D> d, Function<T, D> getD,
            Function3<A, C, D, T> constructor) {
        return of((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); d.encode(b, getD.apply(t)); },
                b -> constructor.apply(a.decode(b), c.decode(b), d.decode(b)));
    }

    static <B, T, A, C, D, E> StreamCodec<B, T> composite(StreamCodec<B, A> a, Function<T, A> getA,
            StreamCodec<B, C> c, Function<T, C> getC, StreamCodec<B, D> d, Function<T, D> getD,
            StreamCodec<B, E> e, Function<T, E> getE, Function4<A, C, D, E, T> constructor) {
        return of((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); d.encode(b, getD.apply(t)); e.encode(b, getE.apply(t)); },
                b -> constructor.apply(a.decode(b), c.decode(b), d.decode(b), e.decode(b)));
    }

    static <B, T, A, C, D, E, F> StreamCodec<B, T> composite(StreamCodec<B, A> a, Function<T, A> getA,
            StreamCodec<B, C> c, Function<T, C> getC, StreamCodec<B, D> d, Function<T, D> getD,
            StreamCodec<B, E> e, Function<T, E> getE, StreamCodec<B, F> f, Function<T, F> getF,
            Function5<A, C, D, E, F, T> constructor) {
        return of((b, t) -> { a.encode(b, getA.apply(t)); c.encode(b, getC.apply(t)); d.encode(b, getD.apply(t)); e.encode(b, getE.apply(t)); f.encode(b, getF.apply(t)); },
                b -> constructor.apply(a.decode(b), c.decode(b), d.decode(b), e.decode(b), f.decode(b)));
    }

    @FunctionalInterface interface Function3<A, B, C, R> { R apply(A a, B b, C c); }
    @FunctionalInterface interface Function4<A, B, C, D, R> { R apply(A a, B b, C c, D d); }
    @FunctionalInterface interface Function5<A, B, C, D, E, R> { R apply(A a, B b, C c, D d, E e); }
}

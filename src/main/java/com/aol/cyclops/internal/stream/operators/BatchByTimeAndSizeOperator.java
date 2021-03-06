package com.aol.cyclops.internal.stream.operators;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.aol.cyclops.control.StreamUtils;
import com.aol.cyclops.data.collections.extensions.standard.ListXImpl;

public class BatchByTimeAndSizeOperator<T, C extends Collection<? super T>> {
    private final Stream<T> stream;
    private final Supplier<C> factory;

    public BatchByTimeAndSizeOperator(final Stream<T> stream) {
        this.stream = stream;
        factory = () -> (C) new ListXImpl<>();
    }

    public BatchByTimeAndSizeOperator(final Stream<T> stream2, final Supplier<C> factory2) {
        this.stream = stream2;
        this.factory = factory2;
    }

    public Stream<C> batchBySizeAndTime(final int size, final long time, final TimeUnit t) {
        final Iterator<T> it = stream.iterator();
        final long toRun = t.toNanos(time);
        return StreamUtils.stream(new Iterator<C>() {
            long start = System.nanoTime();

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public C next() {

                final C list = factory.get();
                while (System.nanoTime() - start < toRun && it.hasNext() && list.size() < size) {
                    list.add(it.next());
                }
                start = System.nanoTime();
                return list;
            }

        })
                          .filter(l -> l.size() > 0);
    }
}

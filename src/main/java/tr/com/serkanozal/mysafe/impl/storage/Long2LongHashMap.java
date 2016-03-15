/*
 * Original work Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 * Modified work Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.impl.storage;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A Probing hashmap specialised for long key and value pairs.
 */
class Long2LongHashMap {
    
    /** The default load factor for constructors not explicitly supplying it */
    public static final double DEFAULT_LOAD_FACTOR = 0.6;
    private final Set<Long> keySet;
    private final LongIterator valueIterator;
    private final Collection<Long> values;
    private final Set<Entry<Long, Long>> entrySet;

    private final double loadFactor;
    private final long missingValue;

    private long[] entries;
    private int capacity;
    private int mask;
    private int resizeThreshold;
    private int size;

    public Long2LongHashMap(int initialCapacity, double loadFactor, long missingValue) {
        this(loadFactor, missingValue);
        capacity(nextPowerOfTwo(initialCapacity));
    }

    public Long2LongHashMap(long missingValue) {
        this(16, DEFAULT_LOAD_FACTOR, missingValue);
    }

    public Long2LongHashMap(Long2LongHashMap that) {
        this(that.loadFactor, that.missingValue);
        this.entries = Arrays.copyOf(that.entries, that.entries.length);
        this.capacity = that.capacity;
        this.mask = that.mask;
        this.resizeThreshold = that.resizeThreshold;
        this.size = that.size;
    }

    private Long2LongHashMap(double loadFactor, long missingValue) {
        this.entrySet = entrySetSingleton();
        this.keySet = keySetSingleton();
        this.values = valuesSingleton();
        this.valueIterator = new LongIterator(1);
        this.loadFactor = loadFactor;
        this.missingValue = missingValue;
    }
    
    private int nextPowerOfTwo(final int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }
    
    private long fastLongMix(long k) {
        // phi = 2^64 / goldenRatio
        final long phi = 0x9E3779B97F4A7C15L;
        long h = k * phi;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }
    
    private int evenLongHash(final long value, final int mask) {
        final int h = (int) fastLongMix(value);
        return h & mask & ~1;
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return size;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    public long get(final long key) {
        final long[] entries = this.entries;
        int index = evenLongHash(key, mask);
        long candidateKey;
        while ((candidateKey = entries[index]) != missingValue) {
            if (candidateKey == key) {
                return entries[index + 1];
            }
            index = next(index);
        }
        return missingValue;
    }

    public long put(final long key, final long value) {
        assert key != missingValue : "Invalid key " + key;
        assert value != missingValue : "Invalid value " + value;
        long oldValue = missingValue;
        int index = evenLongHash(key, mask);
        long candidateKey;
        while ((candidateKey = entries[index]) != missingValue) {
            if (candidateKey == key) {
                oldValue = entries[index + 1];
                break;
            }
            index = next(index);
        }
        if (oldValue == missingValue) {
            ++size;
            entries[index] = key;
        }
        entries[index + 1] = value;
        checkResize();
        return oldValue;
    }

    private void checkResize() {
        if (size > resizeThreshold) {
            final int newCapacity = capacity << 1;
            if (newCapacity < 0) {
                throw new IllegalStateException("Max capacity reached at size=" + size);
            }
            rehash(newCapacity);
        }
    }

    private void rehash(final int newCapacity) {
        final long[] oldEntries = entries;
        capacity(newCapacity);
        for (int i = 0; i < oldEntries.length; i += 2) {
            final long key = oldEntries[i];
            if (key != missingValue) {
                put(key, oldEntries[i + 1]);
            }
        }
    }

    /**
     * Primitive specialised forEach implementation.
     * <p/>
     * NB: Renamed from forEach to avoid overloading on parameter types of lambda
     * expression, which doesn't interplay well with type inference in lambda expressions.
     *
     * @param consumer a callback called for each key/value pair in the map.
     */
    public void longForEach(final LongLongConsumer consumer) {
        final long[] entries = this.entries;
        for (int i = 0; i < entries.length; i += 2) {
            final long key = entries[i];
            if (key != missingValue) {
                consumer.accept(entries[i], entries[i + 1]);
            }
        }
    }

    /**
     * Provides a cursor over the map's entries. Similar to {@code entrySet().iterator()},
     * but with a simpler and more clear API.
     */
    public LongLongCursor cursor() {
        return new LongLongCursor();
    }

    /**
     * Implements the cursor.
     */
    public final class LongLongCursor {
        private int i = -2;

        public boolean advance() {
            final long[] es = entries;
            do {
                i += 2;
            } while (i < es.length && es[i] == missingValue);
            return i < es.length;
        }

        public long key() {
            return entries[i];
        }

        public long value() {
            return entries[i + 1];
        }
    }

    /**
     * Long primitive specialised containsKey.
     *
     * @param key the key to check.
     * @return true if the map contains key as a key, false otherwise.
     */
    public boolean containsKey(final long key) {
        return get(key) != missingValue;
    }

    public boolean containsValue(final long value) {
        final long[] entries = this.entries;
        for (int i = 1; i < entries.length; i += 2) {
            final long entryValue = entries[i];
            if (entryValue == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        Arrays.fill(entries, missingValue);
        size = 0;
    }

    // ---------------- Boxed Versions Below ----------------

    /**
     * {@inheritDoc}
     */
    public Long get(final Object key) {
        return get((long) (Long) key);
    }

    /**
     * {@inheritDoc}
     */
    public Long put(final Long key, final Long value) {
        return put(key.longValue(), value.longValue());
    }

    /**
     * {@inheritDoc}
     */
    public void forEach(final BiConsumer<? super Long, ? super Long> action) {
        longForEach(new UnboxingBiConsumer(action));
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(final Object key) {
        return containsKey((long) (Long) key);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(final Object value) {
        return containsValue((long) (Long) value);
    }

    /**
     * {@inheritDoc}
     */
    public void putAll(final Map<? extends Long, ? extends Long> map) {
        for (final Entry<? extends Long, ? extends Long> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<Long> keySet() {
        return keySet;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<Long> values() {
        return values;
    }

    /**
     * {@inheritDoc}
     * This set's iterator also implements <code>Map.Entry</code>
     * so the <code>next()</code> method can just return the iterator
     * instance itself with no heap allocation. This characteristic
     * makes the set unusable wherever the returned entries are
     * retained (such as <code>coll.addAll(entrySet)</code>.
     */
    public Set<Entry<Long, Long>> entrySet() {
        return entrySet;
    }

    /**
     * {@inheritDoc}
     */
    public Long remove(final Object key) {
        return remove((long) (Long) key);
    }

    public long remove(final long key) {
        final long[] entries = this.entries;
        int index = evenLongHash(key, mask);
        long candidateKey;
        while ((candidateKey = entries[index]) != missingValue) {
            if (candidateKey == key) {
                final int valueIndex = index + 1;
                final long oldValue = entries[valueIndex];
                entries[index] = missingValue;
                entries[valueIndex] = missingValue;
                size--;
                compactChain(index);
                return oldValue;
            }
            index = next(index);
        }
        return missingValue;
    }

    @Override public String toString() {
        final StringBuilder b = new StringBuilder(size() * 8);
        b.append('{');
        longForEach(new LongLongConsumer() {
            String separator = "";
            @Override public void accept(long key, long value) {
                b.append(separator).append(key).append("->").append(value);
                separator = " ";
            }
        });
        return b.append('}').toString();
    }

    private void compactChain(int deleteIndex) {
        final long[] entries = this.entries;
        int index = deleteIndex;
        while (true) {
            index = next(index);
            if (entries[index] == missingValue) {
                return;
            }
            final int hash = evenLongHash(entries[index], mask);
            if ((index < hash && (hash <= deleteIndex || deleteIndex <= index))
                    || (hash <= deleteIndex && deleteIndex <= index)) {
                entries[deleteIndex] = entries[index];
                entries[deleteIndex + 1] = entries[index + 1];
                entries[index] = missingValue;
                entries[index + 1] = missingValue;
                deleteIndex = index;
            }
        }
    }
    
    /**
     * This is a (long,long) primitive specialisation of a BiConsumer
     */
    public interface LongLongConsumer {
        /**
         * Accept a key and value that comes as a tuple of longs.
         *
         * @param key   for the tuple.
         * @param value for the tuple.
         */
        void accept(long key, long value);
    }
    
    public interface BiConsumer<T, U> {

        /**
         * Performs this operation on the given arguments.
         *
         * @param t the first input argument
         * @param u the second input argument
         */
        void accept(T t, U u);
    }

    private static class IteratorSupplier implements Supplier<Iterator<Long>> {
        private final LongIterator keyIterator;

        public IteratorSupplier(LongIterator keyIterator) {
            this.keyIterator = keyIterator;
        }

        @Override
        public Iterator<Long> get() {
            return keyIterator.reset();
        }
    }

    private static class EntryIteratorSupplier implements Supplier<Iterator<Entry<Long, Long>>> {
        private final EntryIterator entryIterator;

        public EntryIteratorSupplier(EntryIterator entryIterator) {
            this.entryIterator = entryIterator;
        }

        @Override
        public Iterator<Entry<Long, Long>> get() {
            return entryIterator.reset();
        }
    }

    private static class UnboxingBiConsumer implements LongLongConsumer {
        private final BiConsumer<? super Long, ? super Long> action;

        public UnboxingBiConsumer(BiConsumer<? super Long, ? super Long> action) {
            this.action = action;
        }

        @Override
        public void accept(long t, long u) {
            action.accept(t, u);
        }
    }

    // ---------------- Utility Classes ----------------

    private abstract class AbstractIterator {
        private int capacity;
        private int mask;
        private int positionCounter;
        private int stopCounter;

        private void reset() {
            final long[] entries = Long2LongHashMap.this.entries;
            capacity = entries.length;
            mask = capacity - 1;
            int i = capacity;
            if (entries[capacity - 2] != missingValue) {
                i = 0;
                for (int size = capacity; i < size; i += 2) {
                    if (entries[i] == missingValue) {
                        break;
                    }
                }
            }
            stopCounter = i;
            positionCounter = i + capacity;
        }

        protected int keyPosition() {
            return positionCounter & mask;
        }

        public boolean hasNext() {
            final long[] entries = Long2LongHashMap.this.entries;
            boolean hasNext = false;
            for (int i = positionCounter - 2; i >= stopCounter; i -= 2) {
                final int index = i & mask;
                if (entries[index] != missingValue) {
                    hasNext = true;
                    break;
                }
            }
            return hasNext;
        }

        protected void findNext() {
            final long[] entries = Long2LongHashMap.this.entries;
            for (int i = positionCounter - 2; i >= stopCounter; i -= 2) {
                final int index = i & mask;
                if (entries[index] != missingValue) {
                    positionCounter = i;
                    return;
                }
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }

    private final class LongIterator extends AbstractIterator implements Iterator<Long> {
        private final int offset;

        private LongIterator(final int offset) {
            this.offset = offset;
        }

        public Long next() {
            return nextValue();
        }

        public long nextValue() {
            findNext();
            return entries[keyPosition() + offset];
        }

        public LongIterator reset() {
            super.reset();
            return this;
        }

        @Override
        public void forEachRemaining(Consumer<? super Long> action) {
            throw new UnsupportedOperationException();
        }
    }

    private final class EntryIterator
            extends AbstractIterator implements Iterator<Entry<Long, Long>>, Entry<Long, Long>
    {
        private long key;
        private long value;

        private EntryIterator() { }

        public Long getKey() {
            return key;
        }

        public Long getValue() {
            return value;
        }

        public Long setValue(final Long value) {
            throw new UnsupportedOperationException();
        }

        public Entry<Long, Long> next() {
            findNext();
            final int keyPosition = keyPosition();
            key = entries[keyPosition];
            value = entries[keyPosition + 1];
            return this;
        }

        public EntryIterator reset() {
            super.reset();
            key = missingValue;
            value = missingValue;
            return this;
        }

        @Override
        public void forEachRemaining(Consumer<? super Entry<Long, Long>> action) {
            throw new UnsupportedOperationException();
        }
    }

    private int next(final int index) {
        return (index + 2) & mask;
    }

    private void capacity(final int newCapacity) {
        capacity = newCapacity;
        resizeThreshold = (int) (newCapacity * loadFactor);
        mask = (newCapacity * 2) - 1;
        entries = new long[newCapacity * 2];
        size = 0;
        Arrays.fill(entries, missingValue);
    }
    
    @SuppressWarnings("rawtypes")
    final class MapDelegatingSet<V> extends AbstractSet<V> {
        
        private final Long2LongHashMap delegate;
        private final Supplier<Iterator<V>> iterator;
        private final Predicate contains;

        public MapDelegatingSet(final Long2LongHashMap delegate, final Supplier<Iterator<V>> iterator, final Predicate contains) {
            this.delegate = delegate;
            this.iterator = iterator;
            this.contains = contains;
        }

        /**
         * {@inheritDoc}
         */
        public int size() {
            return delegate.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        public boolean contains(final Object o) {
            return contains.test(o);
        }

        /**
         * {@inheritDoc}
         */
        public Iterator<V> iterator() {
            return iterator.get();
        }

        /**
         * {@inheritDoc}
         */
        public void clear() {
            delegate.clear();
        }

        @Override
        public Spliterator<V> spliterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeIf(Predicate<? super V> filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<V> stream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<V> parallelStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEach(Consumer<? super V> action) {
            throw new UnsupportedOperationException();
        }
    }

    @SuppressWarnings("rawtypes")
    private MapDelegatingSet<Entry<Long, Long>> entrySetSingleton() {
        return new MapDelegatingSet<Entry<Long, Long>>(this, new EntryIteratorSupplier(new EntryIterator()),
                new Predicate() {
                    @SuppressWarnings("unchecked")
                    @Override public boolean test(Object e) {
                        return containsKey(((Entry<Long, Long>) e).getKey());
                    }

                    @Override
                    public Predicate and(Predicate other) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Predicate negate() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Predicate or(Predicate other) {
                        throw new UnsupportedOperationException();
                    }
                });
    }

    @SuppressWarnings("rawtypes")
    private MapDelegatingSet<Long> keySetSingleton() {
        return new MapDelegatingSet<Long>(this, new IteratorSupplier(new LongIterator(0)), new Predicate() {
            @Override public boolean test(Object value) {
                return containsValue(value);
            }

            @Override
            public Predicate and(Predicate other) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Predicate negate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Predicate or(Predicate other) {
                throw new UnsupportedOperationException();
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private MapDelegatingSet<Long> valuesSingleton() {
        return new MapDelegatingSet<Long>(this, new Supplier<Iterator<Long>>() {
            @Override public Iterator<Long> get() {
                return valueIterator.reset();
            }
        }, new Predicate() {
            @Override public boolean test(Object key) {
                return containsKey(key);
            }

            @Override
            public Predicate and(Predicate other) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Predicate negate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Predicate or(Predicate other) {
                throw new UnsupportedOperationException();
            }
        });
    }
}

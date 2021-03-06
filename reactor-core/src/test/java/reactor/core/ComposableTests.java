package reactor.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static reactor.Fn.$;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matcher;
import org.junit.Test;

import reactor.fn.Consumer;
import reactor.fn.Deferred;
import reactor.fn.Event;
import reactor.fn.Function;
import reactor.fn.Selector;

/**
 * @author Jon Brisbin
 */
public class ComposableTests {

	static final String2Integer STRING_2_INTEGER = new String2Integer();

	@Test
	public void testComposeFromSingleValue() throws InterruptedException {
		Composable<String> c = Composable.from("Hello World!");

		Deferred<String> d = c.map(new Function<String, String>() {
			@Override
			public String apply(String s) {
				return "Goodbye then!";
			}
		});

		await(d, is("Goodbye then!"));
	}

	@Test
	public void testComposeFromMultipleValues() throws InterruptedException {
		Composable<Integer> c = Composable
				.from(Arrays.asList("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER)
				.map(new Function<Integer, Integer>() {
					int sum = 0;

					@Override
					public Integer apply(Integer i) {
						sum += i;
						return sum;
					}
				});

		await(c, is(15));
	}

	@Test
	public void testComposeFromMultipleFilteredValues() throws InterruptedException {
		Composable<Integer> c = Composable
				.from(Arrays.asList("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER)
				.filter(new Function<Integer, Boolean>() {

					@Override
					public Boolean apply(Integer t) {
						return t % 2 == 0;
					}

				});

		await(c, is(4));
	}

	@Test
	public void testComposedErrorHandlingWithMultipleValues() throws InterruptedException {
		Composable<Integer> c = Composable
				.from(Arrays.asList("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER)
				.map(new Function<Integer, Integer>() {
					int sum = 0;

					@Override
					public Integer apply(Integer i) {
						if (i >= 5) {
							throw new IllegalArgumentException();
						}
						sum += i;
						return sum;
					}
				});

		await(c, is(10));
	}

	@Test
	public void valueIsImmediatelyAvailable() throws InterruptedException {
		Composable<String> c = Composable.from(Arrays.asList("1", "2", "3", "4", "5"));

		await(c, is("5"));
	}

	@Test
	public void testReduce() throws InterruptedException {
		Composable<Integer> c = Composable
				.from(Arrays.asList("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER)
				.reduce(new Function<Composable.Reduce<Integer, Integer>, Integer>() {
					@Override
					public Integer apply(Composable.Reduce<Integer, Integer> r) {
						return ((null != r.getLastValue() ? r.getLastValue() : 1) * r.getNextValue());
					}
				});

		await(c, is(120));
	}

	@Test
	public void testFirstAndLast() throws InterruptedException {
		Composable<Integer> c = Composable
				.from(Arrays.asList("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER);

		Deferred<Integer> first = c.first();
		Deferred<Integer> last = c.last();

		await(first, is(1));
		await(last, is(5));
	}

	@Test
	public void testRelaysEventsToReactor() throws InterruptedException {
		Reactor r = R.create();
		Object key = new Object();
		Selector sel = $(key);

		final CountDownLatch latch = new CountDownLatch(5);
		r.on(sel, new Consumer<Event<Integer>>() {
			@Override
			public void accept(Event<Integer> integerEvent) {
				latch.countDown();
			}
		});

		Composable<Integer> c = Composable
				.from(Arrays.asList("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER)
				.consume(key, r);

		c.get(); // Trigger the deferred value to be set

		latch.await(1, TimeUnit.SECONDS);
		assertThat(latch.getCount(), is(0L));
		assertThat(c.get(), is(5));
	}

	@Test
	public void composableWithInitiallyUnknownNumberOfValues() throws InterruptedException {
		final Composable<Integer> c = Composable
				.from(new TestIterable<String>("1", "2", "3", "4", "5"))
				.map(STRING_2_INTEGER)
				.map(new Function<Integer, Integer>() {
					int sum = 0;

					@Override
					public Integer apply(Integer i) {
						sum += i;
						return sum;
					}
				});

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {

				}
				c.setExpectedAcceptCount(5);
			}
		}).start();

		await(c, is(15));
	}

	<T> void await(Deferred<T> d, Matcher<T> expected) throws InterruptedException {
		long startTime = System.currentTimeMillis();
		T result = d.await(1, TimeUnit.SECONDS);
		long duration = System.currentTimeMillis() - startTime;

		assertThat(result, expected);
		assertThat(duration, is(lessThan(1000L)));
	}

	static class String2Integer implements Function<String, Integer> {
		@Override
		public Integer apply(String s) {
			return Integer.parseInt(s);
		}
	}

	static class TestIterable<T> implements Iterable<T> {

		private final Collection<T> items;

		@SafeVarargs
		public TestIterable(T... items) {
			this.items = Arrays.asList(items);
		}

		@Override
		public Iterator<T> iterator() {
			return this.items.iterator();
		}

	}

}

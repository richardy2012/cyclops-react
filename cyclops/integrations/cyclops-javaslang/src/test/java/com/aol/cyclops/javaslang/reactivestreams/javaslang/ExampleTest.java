package com.aol.cyclops.javaslang.reactivestreams.javaslang;

import javaslang.collection.LazyStream;

import org.junit.Test;

import com.aol.cyclops.javaslang.reactivestreams.JavaslangReactiveStreamsPublisher;
import com.aol.cyclops.javaslang.reactivestreams.JavaslangReactiveStreamsSubscriber;
import com.aol.cyclops.javaslang.reactivestreams.ReactiveStream;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.types.stream.reactive.CyclopsSubscriber;

public class ExampleTest {

	@Test
	public void subscribe(){
		CyclopsSubscriber<Integer> subscriber =ReactiveSeq.subscriber();
		
		LazyStream<Integer> stream = LazyStream.of(1,2,3);
		
		JavaslangReactiveStreamsPublisher.ofSync(stream)
										.subscribe(subscriber);
		
		subscriber.sequenceM()
				.forEach(System.out::println);
	}
	@Test
	public void publish(){
		
		ReactiveSeq<Integer> publisher =ReactiveSeq.of(1,2,3);
		
		JavaslangReactiveStreamsSubscriber<Integer> subscriber = new JavaslangReactiveStreamsSubscriber<>();
		publisher.subscribe(subscriber);
		
		ReactiveStream<Integer> stream = subscriber.getStream();
		
		
		
		stream.forEach(System.out::println);
	}
}
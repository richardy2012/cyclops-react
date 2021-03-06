package com.aol.cyclops.react.async.pipes;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import org.junit.Before;
import org.junit.Test;

import com.aol.cyclops.control.Eval;
import com.aol.cyclops.control.FutureW;
import com.aol.cyclops.control.LazyReact;
import com.aol.cyclops.control.Maybe;
import com.aol.cyclops.control.Pipes;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.data.async.Queue;
import com.aol.cyclops.data.async.QueueFactories;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.futurestream.LazyFutureStream;
import com.aol.cyclops.types.stream.reactive.SeqSubscriber;

import lombok.val;
import reactor.core.publisher.Flux;
public class PipesTest {
    Pipes<String,String> pipes;
   
	@Before
	public void setup() {
		pipes = Pipes.of(new HashMap<>());
	}
	
	@Test
	public void evalIssue(){
	    
        Pipes<String, Integer> bus = Pipes.of();
        bus.register("reactor", QueueFactories.<Integer>boundedNonBlockingQueue(1000)
                                              .build());
        
        bus.publishTo("reactor",ReactiveSeq.of(10,20,30));
        
        val ev = bus.nextOrNull("reactor");
        List results = new ArrayList();
       
        results.add(ev.get());
        results.add(ev.get());
        results.add(ev.get());
        
        assertThat(results,equalTo(ListX.of(10,20,30)));
        
      
        //finished!
           
        
     
        
	}
	@Test
    public void nextValueFinished(){
        
	    Maybe.just("hello").peek(System.out::println);
        Pipes<String, Integer> bus = Pipes.of();
        bus.register("reactor", QueueFactories.<Integer>boundedNonBlockingQueue(1000)
                                              .build());
        
        bus.publishTo("reactor",ReactiveSeq.of(10,20,30));
        
        val ev = bus.nextValue("reactor");
        List results = new ArrayList();
        
        results.add(ev.get());
        results.add(ev.get());
        
       
        results.add(ev.get());
        
        
        assertThat(results,equalTo(ListX.of(Maybe.of(10),Maybe.of(20),Maybe.of(30))));
           
        
     
        
    }
	@Test
    public void oneOrErrorFinishes(){
        
        Pipes<String, Integer> bus = Pipes.of();
        bus.register("reactor", QueueFactories.<Integer>boundedNonBlockingQueue(1000)
                                              .build());
        
        bus.publishTo("reactor",ReactiveSeq.of(10,20,30));
        
        List results = new ArrayList();
        
        results.add(bus.oneOrError("reactor").get());
        results.add(bus.oneOrError("reactor").get());
        results.add(bus.oneOrError("reactor").get());
        
        assertThat(results,equalTo(ListX.of(10,20,30)));       
     
         
    }
	@Test
    public void futureStreamTest(){
        
        Pipes<String, Integer> bus = Pipes.of();
        bus.register("reactor", QueueFactories.<Integer>boundedNonBlockingQueue(1000)
                                              .build());
        
        bus.publishTo("reactor",ReactiveSeq.of(10,20,30));
        
        bus.close("reactor");
        
        
       
      System.out.println(Thread.currentThread().getId());
       List<String> res =  bus.futureStream("reactor", new LazyReact())
            .get()
           .map(i->"fan-out to handle blocking I/O:" + Thread.currentThread().getId() + ":"+i)
           .toList();
          System.out.println(res);
       
       assertThat(res.size(),equalTo(3));
           
        
     
        
    }
	@Test
    public void futureStreamCustomTest(){
        
        Pipes<String, Integer> bus = Pipes.of();
        bus.register("reactor", QueueFactories.<Integer>boundedNonBlockingQueue(1000)
                                              .build());
        
        bus.publishTo("reactor",ReactiveSeq.of(10,20,30));
        
        bus.close("reactor");
        
        
       
      System.out.println(Thread.currentThread().getId());
       List<String> res =  bus.futureStream("reactor", new LazyReact(10,10))
            .get()
           .map(i->"fan-out to handle blocking I/O:" + Thread.currentThread().getId() + ":"+i)
           .toList();
          System.out.println(res);
       
       assertThat(res.size(),equalTo(3));
           
        
     
        
    }
	@Test
    public void publishToTest(){
	    
        Pipes<String, Integer> bus = Pipes.of();
        bus.register("reactor", QueueFactories.<Integer>boundedNonBlockingQueue(1000)
                                              .build());
        
        bus.publishTo("reactor",Flux.just(10,20,30));
        
        bus.close("reactor");
        
        
       
      System.out.println(Thread.currentThread().getId());
       List<String> res =  bus.futureStream("reactor", new LazyReact())
            .get()
           .map(i->"fan-out to handle blocking I/O:" + Thread.currentThread().getId() + ":"+i)
           .toList();
          System.out.println(res);
       
       assertThat(res.size(),equalTo(3));
           
        
     
        
    }
	
	@Test
	public void testGetAbsent() {
		
		assertFalse(pipes.get("hello").isPresent());
	}
	@Test
	public void testGetPresent() {
		pipes.register("hello", new Queue());
		assertTrue(pipes.get("hello").isPresent());
	}

	@Test
	public void reactiveSeq() throws InterruptedException{
	    Queue q = new Queue<>();
	    pipes.register("hello", q);
	    pipes.push("hello", "world");
	    q.close();
	    assertThat(pipes.reactiveSeq("hello")
	         .get().toList(),equalTo(Arrays.asList("world")));
	   
	   
	    
	}
	@Test
    public void xValues() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
        pipes.push("hello", "world3");
        pipes.push("hello", "world4");
        q.close();
        assertThat(pipes.xValues("hello",2),equalTo(ListX.of("world","world2")));
        assertThat(pipes.xValues("hello",2),equalTo(ListX.of("world3","world4")));
            
        
    }
	@Test
    public void nextValue() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
        q.close();
        Eval<Maybe<String>> nextValue = pipes.nextValue("hello");
        int values = 0;
        while(nextValue.get().isPresent()){
            System.out.println(values++);
            
        }
            
        assertThat(values,equalTo(2));
        
          
    }
	@Test
    public void nextNull() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
        q.close();
        Eval<String> nextValue = pipes.nextOrNull("hello");
        int values = 0;
        while(nextValue.get()!=null){
            System.out.println(values++);
            
        }
            
        assertThat(values,equalTo(2));
        
          
    }
	
	Executor ex = Executors.newFixedThreadPool(1);
	
	@Test
	public void nextAsync(){
	    Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
        q.close();
        assertThat(pipes.oneOrErrorAsync("hello", ex).get(),
                equalTo(FutureW.ofResult("world").get()));  
        assertThat(pipes.oneOrErrorAsync("hello", ex).get(),
                equalTo(FutureW.ofResult("world2").get()));  
	}
	@Test
    public void oneOrError() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
        q.close();
        assertThat(pipes.oneOrError("hello")
             .get(),equalTo("world"));
        assertThat(pipes.oneOrError("hello")
                .get(),equalTo("world2"));
          
    }
	@Test
    public void oneValueOrError() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
      
        q.close();
        assertThat(pipes.oneValueOrError("hello")
             .get().get(),equalTo("world"));
        assertThat(pipes.oneValueOrError("hello")
                .get().get(),equalTo("world2"));
          
    }
	@Test
    public void oneValueOrErrorTry() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
        pipes.push("hello", "world2");
      
        q.close();
        assertThat(pipes.oneValueOrError("hello",Throwable.class)
             .get().get(),equalTo("world"));
        assertThat(pipes.oneValueOrError("hello")
                .get().get(),equalTo("world2"));
          
    }
	@Test
    public void oneValueOrErrorTryException() throws InterruptedException{
        Queue q = new Queue<>();
        pipes.register("hello", q);
        pipes.push("hello", "world");
      
        q.close();
        assertThat(pipes.oneValueOrError("hello",Throwable.class)
             .get().get(),equalTo("world"));
        assertThat(pipes.oneValueOrError("hello",Throwable.class)
                .get().failureGet(),instanceOf(NoSuchElementException.class));
          
    }
	
	@Test
	public void subscribeTo(){
	    SeqSubscriber subscriber = SeqSubscriber.subscriber();
		Queue queue = new Queue();
		pipes.register("hello", queue);
		pipes.subscribeTo("hello",subscriber,ForkJoinPool.commonPool());
		queue.offer("world");
		queue.close();
		assertThat(subscriber.stream().findAny().get(),equalTo("world"));
	}
	@Test
	public void publishTo() throws InterruptedException{
	    Pipes<String,Integer> pipes = Pipes.of();
		Queue queue = new Queue();
		pipes.register("hello", queue);
		pipes.publishToAsync("hello",LazyFutureStream.of(1,2,3));
		Thread.sleep(100);
		queue.offer(4);
		queue.close();
		assertThat(queue.stream().toList(),equalTo(Arrays.asList(1,2,3,4)));
	}
	@Test
	public void seqSubscriberTest(){
	    SeqSubscriber<Integer> sub = SeqSubscriber.subscriber();
        ReactiveSeq.of(1,2,3).subscribe(sub);
        assertThat(sub.toListX(),equalTo(ListX.of(1,2,3)));
	}
	@Test
    public void publishToSync() throws InterruptedException{
	    Pipes<String,Integer> pipes = Pipes.of();
        Queue<Integer> queue = new Queue<>();
        pipes.<Integer>register("hello", queue);
        pipes.publishTo("hello",ReactiveSeq.of(1,2,3));
        queue.offer(4);
        queue.close();
        assertThat(queue.stream().toList(),equalTo(Arrays.asList(1,2,3,4)));
    }
}


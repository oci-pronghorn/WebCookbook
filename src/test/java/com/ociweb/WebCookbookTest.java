package com.ociweb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.stage.test.ConsoleJSONDumpStage;

public class WebCookbookTest {

	@Test
	public void makeCallsTest() {

	    ClientCoordinator.registerDomain("127.0.0.1");	
	    
		WebCookbook.main(new String[] {"-h", "127.0.0.1", "-p", "8899"});
		
		GraphManager gm = new GraphManager();
				
		int tracks = 1;
		TLSCertificates tlsCertificates = null;//TLSCertificates.defaultCerts; 		//TODO: turn this on later..
		int connectionsInBits = 3;
		int maxPartialResponses = 4;					
		int clientRequestCount=7; 
		int clientRequestSize=2000;

		Pipe<ClientHTTPRequestSchema>[] clientRequests = Pipe.buildPipes(
				tracks, 
				ClientHTTPRequestSchema.instance.newPipeConfig(20, 1<<9));

		Pipe<NetResponseSchema>[] clientResponses = Pipe.buildPipes(
					tracks, 
					NetResponseSchema.instance.newPipeConfig(20, 1<<20));//large enough for files
		
		NetGraphBuilder.buildHTTPClientGraph(gm, 
											clientResponses,
											clientRequests,
											maxPartialResponses, connectionsInBits,
											clientRequestCount,
											clientRequestSize,
											tlsCertificates);

		//since we have no producing stage we have to init the buffer ourselves
		clientRequests[0].initBuffers();
		
		//these are the test requests
		ClientHTTPRequestSchema.publishHTTPGet(clientRequests[0], 
				0, //pipe destination for the response
				0, //sessionId, which instance of this domain is it
				8899, 
				"127.0.0.1", 
				"/person/add?id=333&name=nathan", 
				null);
		
		ClientHTTPRequestSchema.publishHTTPGet(clientRequests[0], 
				0, //pipe destination for the response
				0, //sessionId, which instance of this domain is it
				8899, 
				"127.0.0.1", 
				"/person/add?id=444&name=scott", 
				null);
		
		//this is the last call which will have the second session id 		
		ClientHTTPRequestSchema.publishHTTPGet(clientRequests[0], 
				0, //pipe destination for the response
				0, //sessionId, which instance of this domain is it
				8899, 
				"127.0.0.1", 
				"/person/list", 
				null);
		
		ClientHTTPRequestSchema.publishHTTPGet(clientRequests[0], 
				0, //pipe destination for the response
				1, //sessionId, use different connection in parallel to the other requests
				8899, 
				"127.0.0.1", 
				"/resource/reqPerSec.png", 
				null);
		
		ClientHTTPRequestSchema.publishHTTPGet(clientRequests[0], 
				0, //pipe destination for the response
				2, //sessionId, use different connection in parallel to the other requests
				8899, 
				"127.0.0.1", 
				"/proxy/person/list", 
				null);
		
		ClientHTTPRequestSchema.publishHTTPGet(clientRequests[0], 
				0, //pipe destination for the response
				2, //sessionId, use different connection in parallel to the other requests
				8899, 
				"127.0.0.1", 
				"/proxy/resource/reqPerSec.png", 
				null);
				
		int expectedResponseCount = 6;
		
		Pipe.publishEOF(clientRequests[0]);
		
		StringBuilder results = new StringBuilder();
		ConsoleJSONDumpStage.newInstance(gm, clientResponses[0], results);		
		
		NonThreadScheduler scheduler = new NonThreadScheduler(gm);
		
		
		scheduler.startup();
		int i = 200;
		while (--i>=0) {
			try {
				Thread.sleep(10);
				if (respCount(results)==expectedResponseCount) {
					break;//quit early
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			scheduler.run();
		}		
		scheduler.shutdown();
		
		int c = respCount(results);
		assertEquals("Expeced response count",expectedResponseCount,c);
		
		//show test response data.
		//System.err.println(results);
	}

	private int respCount(StringBuilder results) {
		int c = 0;
		int j = 0;
		while ((j=results.indexOf("Response",j+1))>=0) {
			c++;
		}
		return c;
	}


	
	
}

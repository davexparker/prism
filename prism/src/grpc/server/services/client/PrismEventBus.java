package grpc.server.services.client;
import java.util.concurrent.TimeUnit;
import grpc.server.services.PrismGrpc;
import grpc.server.services.PrismGrpcLogger;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class PrismEventBus {
    private static PrismEventBus instance;
    private final BlockingQueue<PrismEvent> eventQueue;
    private final Map<PrismEvent,
            PrismGrpc.ClientModelGeneratorResponseWrapper> responseMap;

    private final PrismGrpcLogger logger = PrismGrpcLogger.getLogger();

    private PrismEvent prismEventMutex = null;

    private PrismEventBus() {
        eventQueue = new LinkedBlockingQueue<>();
        responseMap = new ConcurrentHashMap<>();
    }

    public static synchronized PrismEventBus getInstance() {
        if (instance == null) {
            instance = new PrismEventBus();
        }
        return instance;
    }

    public PrismEvent getPrismEventMutex() {
        return prismEventMutex;
    }

    public void setPrismEventMutex(PrismEvent prismEventMutex) {
        this.prismEventMutex = prismEventMutex;
    }

    public void postEvent(PrismEvent event) {
        eventQueue.add(event);
        prismEventMutex = event;
    }

    public PrismEvent getNextEvent() throws InterruptedException {
        // waits until an event is available
        return eventQueue.take();
//        PrismEvent event = eventQueue.poll(3, TimeUnit.SECONDS);
//
//        if (event != null) {
//            return event;
//        } else {
//            // create a CloseClientModelGeneratorRequest
//            logger.info("No more requests coming...shutting down");
//            PrismEvent closeClientModelGeneratorRequest = new PrismEvent(
//                    PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
//                            .setCloseClientModelGeneratorRequest(
//                                    PrismGrpc.CloseClientModelGeneratorRequest.newBuilder().build())
//                            .build(),PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.EXPORTTRANSTOFILEREQUEST);
//
//            prismEventMutex = closeClientModelGeneratorRequest;
//            return closeClientModelGeneratorRequest;
//        }

    }

    public void postResponse(PrismGrpc.ClientModelGeneratorResponseWrapper response) {
        if (prismEventMutex.getExpectedResponse() != response.getResponseCase()) {
            logger.warning("PrismEventBus: expected response "
                    + prismEventMutex.getExpectedResponse()
                    + " but got response "
                    + response.getResponseCase());
        } else if (prismEventMutex == null) {
            logger.warning("PrismEventBus: prismEventMutex is null");
        } else {
            // resetting prismEventMutex
            responseMap.put(prismEventMutex, response);
            prismEventMutex = null;
        }
    }

    public PrismGrpc.ClientModelGeneratorResponseWrapper getResponse(PrismEvent event) {
        while (!responseMap.containsKey(event)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.warning("Thread interrupted while waiting for response to event " + event);
            }
        }
        prismEventMutex = null;
        return responseMap.remove(event);
    }
}

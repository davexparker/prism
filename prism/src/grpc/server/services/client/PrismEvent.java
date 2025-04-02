package grpc.server.services.client;

import grpc.server.services.PrismGrpc;

public class PrismEvent {
    private PrismGrpc.ClientModelGeneratorRequestWrapper request;

    private PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse;

    public PrismEvent( PrismGrpc.ClientModelGeneratorRequestWrapper request, PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse) {
        this.request = request;
        this.expectedResponse = expectedResponse;
    }

    public PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase getExpectedResponse() {
        return expectedResponse;
    }


    public PrismGrpc.ClientModelGeneratorRequestWrapper getRequest() {
        return request;
    }
}

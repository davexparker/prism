package grpc.server.services.client;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import grpc.server.services.PrismGrpc;
import grpc.server.services.PrismGrpcLogger;
import io.grpc.stub.StreamObserver;
import parser.State;
import parser.ast.DeclarationInt;
import parser.ast.DeclarationType;
import parser.ast.Expression;
import parser.type.Type;
import parser.type.TypeUtils;
import prism.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


public class ClientServiceWrapper implements ModelGenerator<Double>, RewardGenerator<Double> {

    private final PrismGrpcLogger logger = PrismGrpcLogger.getLogger();
    private final String modelGeneratorObjectId;

    private PrismEventBus eventBus = PrismEventBus.getInstance();

    private Map<String, Object> prismObjectMap = null;

    public ClientServiceWrapper(String modelGeneratorObjectId, StreamObserver<PrismGrpc.ClientModelGeneratorRequestWrapper> responseObserver, Map<String, Object> prismObjectMap) {
        this.modelGeneratorObjectId = modelGeneratorObjectId;
        this.prismObjectMap = prismObjectMap;
    }


    @Override
    public ModelType getModelType() {
    logger.info("[ClientServiceWrapper] - sending getModelType request");
    // creating model type request
    PrismGrpc.ModelTypeRequest modelTypeRequest = PrismGrpc.ModelTypeRequest.newBuilder().build();
    PrismGrpc.ClientModelGeneratorRequestWrapper modelTypeRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
            .setModelTypeRequest(modelTypeRequest)
            .build();

    // setting expected response
    PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.MODELTYPERESPONSE;

    // adding it to the event bus
    PrismEvent event = new PrismEvent(modelTypeRequestWrapper, expectedResponse);
    eventBus.postEvent(event);

    // waiting for the response
    PrismGrpc.ClientModelGeneratorResponseWrapper response = eventBus.getResponse(event);

    logger.info("[ClientServiceWrapper] - received getModelType response with value: " + response.getModelTypeResponse().getValue().getValue());
    return ModelType.valueOf(response.getModelTypeResponse().getValue().getValue());
    }

    @Override
    public List<String> getVarNames() {
        logger.info("[ClientServiceWrapper] - sending getVarNames request");
        // send var names request (empty)
        PrismGrpc.VarNamesRequest varNamesRequest = PrismGrpc.VarNamesRequest.newBuilder().build();
        PrismGrpc.ClientModelGeneratorRequestWrapper varNamesRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setVarNamesRequest(varNamesRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.VARNAMESRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(varNamesRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.StringArrayResponse response = eventBus.getResponse(event).getVarNamesResponse();

        List<String> stringList = new ArrayList<>();
        for (StringValue value : response.getValuesList()) {
            stringList.add(value.getValue());
        }
        logger.info("[ClientServiceWrapper] - received getVarNames response with value: " + stringList);
        return stringList;

    }

    @Override
    public List<Type> getVarTypes() {
        logger.info("[ClientServiceWrapper] - sending getVarTypes request");

        // send var types request (empty)
        PrismGrpc.VarTypesRequest varTypesRequest = PrismGrpc.VarTypesRequest.newBuilder().build();
        PrismGrpc.ClientModelGeneratorRequestWrapper varTypesRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setVarTypesRequest(varTypesRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.VARTYPESRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(varTypesRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.StringArrayResponse response = eventBus.getResponse(event).getVarTypesResponse();

        // casting the response to a list of types
        List<Type> typeList = new ArrayList<>();
        for (StringValue value : response.getValuesList()) {
            try {
                typeList.add(TypeUtils.typeFromName(value.getValue()));
            } catch (PrismLangException e) {
                logger.warning("[ClientServiceWrapper] - received getVarTypes response with invalid type: " + value.getValue());
            }
        }

        logger.info("[ClientServiceWrapper] - received getVarTypes response with value: " + typeList);
        return typeList;
    }

    @Override
    public DeclarationType getVarDeclarationType(int i)
    {
        logger.info("[ClientServiceWrapper] - sending getVarDeclarationType request with index: " + i);

        // send var declaration type request
        PrismGrpc.VarDeclarationTypeRequest varDeclarationTypeRequest = PrismGrpc.VarDeclarationTypeRequest
            .newBuilder()
            .setIndex(i)
            .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper varDeclarationTypeRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
            .setVarDeclarationTypeRequest(varDeclarationTypeRequest)
            .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.VARDECLARATIONTYPERESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(varDeclarationTypeRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        List<Int32Value> response = eventBus.getResponse(event).getVarDeclarationTypeResponse().getValuesList();
        // casting the response to DeclarationType
        DeclarationType declarationType = new DeclarationInt(
            Expression.Int(response.get(0).getValue()),
            Expression.Int(response.get(1).getValue()));

        logger.info("[ClientServiceWrapper] - received getVarDeclarationType response with value: " + declarationType);

        return declarationType;
    }

    @Override
    public List<String> getLabelNames()
    {
        logger.info("[ClientServiceWrapper] - sending getLabelNames request");

        // send label names request
        PrismGrpc.LabelNamesRequest labelNamesRequest = PrismGrpc.LabelNamesRequest.newBuilder().build();
        PrismGrpc.ClientModelGeneratorRequestWrapper labelNamesRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
            .setLabelNamesRequest(labelNamesRequest)
            .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.LABELNAMESRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(labelNamesRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.StringArrayResponse response = eventBus.getResponse(event).getLabelNamesResponse();

        List<String> stringList = new ArrayList<>();

        for (StringValue value : response.getValuesList()) {
            stringList.add(value.getValue());
        }
        logger.info("[ClientServiceWrapper] - received getLabelNames response with value: " + stringList);
        return stringList;
    }

    // Methods for ModelGenerator interface (rather than superclass ModelInfo)

    @Override
    public State getInitialState() throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getInitialState request");
        // set initial state request (empty)
        PrismGrpc.InitialStateRequest initialStateRequest = PrismGrpc.InitialStateRequest.newBuilder().build();
        PrismGrpc.ClientModelGeneratorRequestWrapper initialStateRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setInitialStateRequest(initialStateRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.INITIALSTATERESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(initialStateRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        String stateObjectId = eventBus.getResponse(event).getInitialStateResponse().getStateObjectId();

        // get state object from prism object map
        State state = (State) prismObjectMap.get(stateObjectId);

        logger.info("[ClientServiceWrapper] - received getInitialState response with value: " + state);
        return state;
    }

    @Override
    public void exploreState(State exploreState) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending exploreState request with state: " + exploreState);

        String exploreStateKey = null;
        // find exploreState in prism object map
        for (Map.Entry<String, Object> entry : prismObjectMap.entrySet()) {
            if (entry.getValue().equals(exploreState)) {
                exploreStateKey = entry.getKey();
                break;
            }
        }

        if (exploreStateKey == null) {
            logger.warning("[ClientServiceWrapper] - exploreState request with state: " + exploreState + " not found in prism object map");
            return;
        }

        // create request
        PrismGrpc.State stateRequest = PrismGrpc.State.newBuilder()
                .setStateObjectId(exploreStateKey)
                .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper exploreStateRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setExploreStateRequest(stateRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.EXPLORESTATERESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(exploreStateRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.ClientModelGeneratorResponseWrapper response = eventBus.getResponse(event);
        logger.info("[ClientServiceWrapper] - received exploreState response");
    }

    @Override
    public int getNumChoices() throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getNumChoices request");

        // send num choices request
        PrismGrpc.NumChoicesRequest numChoicesRequest = PrismGrpc.NumChoicesRequest.newBuilder().build();
        PrismGrpc.ClientModelGeneratorRequestWrapper numChoicesRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
            .setNumChoicesRequest(numChoicesRequest)
            .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.NUMCHOICESRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(numChoicesRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        int numChoices = eventBus.getResponse(event).getNumChoicesResponse().getValue().getValue();

        logger.info("[ClientServiceWrapper] - received getNumChoices response with value: " + numChoices);

        return numChoices;
    }

    @Override
    public int getNumTransitions(int i) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getNumTransitions request with index: " + i);

        // send num transitions request
        PrismGrpc.NumTransitionsRequest numTransitionsRequest = PrismGrpc.NumTransitionsRequest.newBuilder()
                .setIndex(1)
                .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper numTransitionsRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setNumTransitionsRequest(numTransitionsRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.NUMTRANSITIONSRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(numTransitionsRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        int numTransitions = eventBus.getResponse(event).getNumTransitionsResponse().getValue().getValue();

        logger.info("[ClientServiceWrapper] - received getNumTransitions response with value: " + numTransitions);

        return numTransitions;
    }

    @Override
    public Object getTransitionAction(int i, int offset) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getTransitionAction request with index: " + i + " and offset: " + offset);

        // send transition action request
        PrismGrpc.TransitionActionRequest transitionActionRequest = PrismGrpc.TransitionActionRequest.newBuilder()
                .setIndex(i)
                .setOffset(offset)
                .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper transitionActionRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setTransitionActionRequest(transitionActionRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.TRANSITIONACTIONRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(transitionActionRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.IntResponse response = eventBus.getResponse(event).getTransitionActionResponse();

        logger.info("[ClientServiceWrapper] - received getTransitionAction response");

        if (response.hasValue()) {
            int value = response.getValue().getValue();
            return value;
        } else {
            return null;
        }
    }

    @Override
    public Double getTransitionProbability(int i, int offset) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getTransitionProbability request with index: " + i + " and offset: " + offset);
        // create request
        PrismGrpc.TransitionProbabilityRequest transitionProbabilityRequest = PrismGrpc.TransitionProbabilityRequest.newBuilder()
                .setIndex(i)
                .setOffset(offset)
                .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper transitionProbabilityRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setTransitionProbabilityRequest(transitionProbabilityRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.TRANSITIONPROBABILITYRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(transitionProbabilityRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.DoubleResponse response = eventBus.getResponse(event).getTransitionProbabilityResponse();

        logger.info("[ClientServiceWrapper] - received getTransitionProbability response");

        if (response.hasValue()) {
            double value = response.getValue().getValue();
            return value;
        } else {
            return null;
        }
    }

    @Override
    public State computeTransitionTarget(int i, int offset) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending computeTransitionTarget request with index: " + i + " and offset: " + offset);
        // create request
        PrismGrpc.TransitionTargetRequest transitionTargetRequest = PrismGrpc.TransitionTargetRequest.newBuilder()
                                .setIndex(i)
                                .setOffset(offset)
                                .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper transitionTargetRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setTransitionTargetRequest(transitionTargetRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.TRANSITIONTARGETRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(transitionTargetRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        String stateObjectId = eventBus.getResponse(event).getTransitionTargetResponse().getStateObjectId();

        // get state object from prism object map
        State state = (State) prismObjectMap.get(stateObjectId);
        logger.info("[ClientServiceWrapper] - received computeTransitionTarget response");

        return state;
    }

    @Override
    public boolean isLabelTrue(int i) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending isLabelTrue request with index: " + i);

        // create request
        PrismGrpc.LabelTrueRequest labelTrueRequest = PrismGrpc.LabelTrueRequest.newBuilder()
                .setIndex(i)
                .build();
        PrismGrpc.ClientModelGeneratorRequestWrapper labelTrueRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setLabelTrueRequest(labelTrueRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.LABELTRUERESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(labelTrueRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.BoolResponse response = eventBus.getResponse(event).getLabelTrueResponse();

        if (!response.hasValue()) {
            throw new PrismException("Response does not contain a value");
        }

        logger.info("[ClientServiceWrapper] - received isLabelTrue response");
        return response.getValue().getValue();
    }

    @Override
    public List<String> getRewardStructNames()
    {
        logger.info("[ClientServiceWrapper] - sending getRewardStructNames request");
        // create request
        PrismGrpc.RewardStructNamesRequest rewardStructNamesRequest = PrismGrpc.RewardStructNamesRequest.newBuilder().build();
        PrismGrpc.ClientModelGeneratorRequestWrapper rewardStructNamesRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setRewardStructNamesRequest(rewardStructNamesRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.REWARDSTRUCTNAMESRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(rewardStructNamesRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.StringArrayResponse response = eventBus.getResponse(event).getRewardStructNamesResponse();

        logger.info("[ClientServiceWrapper] - received getRewardStructNames response");

        List<String> stringList = new ArrayList<>();

        for (StringValue value : response.getValuesList()) {
            stringList.add(value.getValue());
        }

        return stringList;

    }

    @Override
    public Double getStateReward(int r, State state) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getStateReward request with reward structure: " + r + " and state: " + state);

        String exploreStateKey = null;
        // find exploreState in prism object map
        for (Map.Entry<String, Object> entry : prismObjectMap.entrySet()) {
            if (entry.getValue().equals(state)) {
                exploreStateKey = entry.getKey();
                break;
            }
        }

        if (exploreStateKey == null) {
            logger.warning("[ClientServiceWrapper] - exploreState request with state: " + state + " not found in prism object map");
            throw new PrismException("exploreState request with state: " + state + " not found in prism object map");
        }

        // create request
        PrismGrpc.State stateRequest = PrismGrpc.State.newBuilder()
                .setStateObjectId(exploreStateKey)
                .build();

        PrismGrpc.StateRewardRequest stateRewardRequest = PrismGrpc.StateRewardRequest.newBuilder().setReward(r).setState(stateRequest).build();

        PrismGrpc.ClientModelGeneratorRequestWrapper stateRewardRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setStateRewardRequest(stateRewardRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.STATEREWARDRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(stateRewardRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.DoubleResponse response = eventBus.getResponse(event).getStateRewardResponse();

        logger.info("[ClientServiceWrapper] - received getStateReward response");

        if (response.hasValue()) {
            double value = response.getValue().getValue();
            return value;
        } else {
            return null;
        }
    }

    @Override
    public Double getStateActionReward(int r, State state, Object action) throws PrismException
    {
        logger.info("[ClientServiceWrapper] - sending getStateActionReward request with reward structure: " + r + " and state: " + state + " and action: " + action);

        String exploreStateKey = null;
        // find exploreState in prism object map
        for (Map.Entry<String, Object> entry : prismObjectMap.entrySet()) {
            if (entry.getValue().equals(state)) {
                exploreStateKey = entry.getKey();
                break;
            }
        }

        if (exploreStateKey == null) {
            logger.warning("[ClientServiceWrapper] - exploreState request with state: " + state + " not found in prism object map");
            throw new PrismException("exploreState request with state: " + state + " not found in prism object map");
        }

        // create request
        PrismGrpc.State stateRequest = PrismGrpc.State.newBuilder()
                .setStateObjectId(exploreStateKey)
                .build();

        PrismGrpc.StateActionRewardRequest.Builder builder = PrismGrpc.StateActionRewardRequest.newBuilder()
                .setReward(r)
                .setState(stateRequest);

        if (action != null) {
            builder.setAction(Int32Value.of((Integer) action));
        }

        PrismGrpc.StateActionRewardRequest stateActionRewardRequest = builder.build();

        PrismGrpc.ClientModelGeneratorRequestWrapper stateActionRewardRequestWrapper = PrismGrpc.ClientModelGeneratorRequestWrapper.newBuilder()
                .setStateActionRewardRequest(stateActionRewardRequest)
                .build();

        // setting expected response
        PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase expectedResponse = PrismGrpc.ClientModelGeneratorResponseWrapper.ResponseCase.STATEACTIONREWARDRESPONSE;

        // adding it to the event bus
        PrismEvent event = new PrismEvent(stateActionRewardRequestWrapper, expectedResponse);
        eventBus.postEvent(event);

        // waiting for the response
        PrismGrpc.DoubleResponse response = eventBus.getResponse(event).getStateActionRewardResponse();

        logger.info("[ClientServiceWrapper] - received getStateActionReward response");

        if (response.hasValue()) {;
            return response.getValue().getValue();
        } else {
            return null;
        }
    }
}

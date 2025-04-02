import os
from abc import ABC

from google.protobuf import wrappers_pb2
from grpc._channel import _InactiveRpcError
from prismpy.services.grpc import prismGrpc_pb2
from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel
from prismpy.services.prismpy_exceptions import PrismPyException
from prismpy.stub_classes.result import Result


class ServiceProviderBase(PrismPyBaseModel, ABC):
    def __init__(self, ServiceProviderClass, prism_object_id):
        self.__result = None
        self.prism_object_id = prism_object_id
        super().__init__(standalone=False)
        self.ServiceProviderClass = ServiceProviderClass

    def handle_requests(self, requests):
        # Iterate over the stream of ClientModelGeneratorResponseWrapper objects
        # waits till the server sends a response
        for request_wrapper in requests:
            request_type = request_wrapper.WhichOneof('request')

            self.logger.info(f"[STREAM] - Received request of type {request_type}")

            if request_type == "modelTypeRequest":
                # perform action
                model_type = self.ServiceProviderClass.get_model_type()

                # create response
                model_type_response = prismGrpc_pb2.StringResponse(
                    value=wrappers_pb2.StringValue(value=model_type) if model_type is not None else None
                )

                # Wrap it in a ClientModelGeneratorResponseWrapper as a modelTypeResponse
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    modelTypeResponse=model_type_response
                )

                # Send the response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "varNamesRequest":
                # perform action
                var_names = self.ServiceProviderClass.get_var_names()

                # create response
                var_names_response = prismGrpc_pb2.StringArrayResponse(
                    values=[wrappers_pb2.StringValue(value=name) for name in var_names] if var_names else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    varNamesResponse=var_names_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "varTypesRequest":
                # perform action
                var_types = self.ServiceProviderClass.get_var_types()

                # create response
                var_types_response = prismGrpc_pb2.StringArrayResponse(
                    values=[wrappers_pb2.StringValue(value=single_type) for single_type in
                            var_types] if var_types else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    varTypesResponse=var_types_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)

            elif request_type == "varDeclarationTypeRequest":
                # get arguments from request
                var_name = request_wrapper.varDeclarationTypeRequest.index

                # perform action
                var_declaration_type = self.ServiceProviderClass.get_var_declaration_type(var_name)

                # create response
                var_declaration_type_response = prismGrpc_pb2.ArrayResponse(
                    values=[wrappers_pb2.Int32Value(value=i) for i in
                            var_declaration_type] if var_declaration_type else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    varDeclarationTypeResponse=var_declaration_type_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "labelNamesRequest":
                # perform action
                label_names = self.ServiceProviderClass.get_label_names()

                # create response
                label_names_response = prismGrpc_pb2.StringArrayResponse(
                    values=[wrappers_pb2.StringValue(value=name) for name in label_names] if label_names else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    labelNamesResponse=label_names_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "initialStateRequest":
                # perform action
                initial_state = self.ServiceProviderClass.get_initial_state()

                # create response
                initial_state_response = prismGrpc_pb2.State(
                    state_object_id=initial_state.object_id
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    initialStateResponse=initial_state_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "exploreStateRequest":
                # get object id from request
                state_object_id = request_wrapper.exploreStateRequest.state_object_id

                status = "success"
                try:
                    # perform action
                    state = self.prism_object_map.get(state_object_id)
                    self.ServiceProviderClass.explore_state(state)

                except Exception as e:
                    status = "error"

                # create status response
                status_response = prismGrpc_pb2.StatusResponse(
                    status=status
                )

                # wrap the status response
                wrapped_response = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    exploreStateResponse=status_response
                )

                # send response
                self.send_response(wrapped_response)
            elif request_type == "numChoicesRequest":
                # no arguments

                # perform action
                num_choices = self.ServiceProviderClass.get_num_choices()

                # create response
                num_choices_response = prismGrpc_pb2.IntResponse(
                    value=wrappers_pb2.Int32Value(value=num_choices) if num_choices is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    numChoicesResponse=num_choices_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "numTransitionsRequest":
                # get arguments from request
                index = request_wrapper.numTransitionsRequest.index

                # perform action
                num_transitions = self.ServiceProviderClass.get_num_transitions(index)

                # create response
                num_transitions_response = prismGrpc_pb2.IntResponse(
                    value=wrappers_pb2.Int32Value(value=num_transitions) if num_transitions is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    numTransitionsResponse=num_transitions_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "transitionActionRequest":
                # get arguments from request
                index = request_wrapper.transitionActionRequest.index
                offset = request_wrapper.transitionActionRequest.offset

                # perform action
                transition_action = self.ServiceProviderClass.get_transition_action(index, offset)

                # create response
                transition_action_response = prismGrpc_pb2.IntResponse(
                    value=wrappers_pb2.Int32Value(value=transition_action) if transition_action is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    transitionActionResponse=transition_action_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "transitionProbabilityRequest":
                # get arguments from request
                index = request_wrapper.transitionProbabilityRequest.index
                offset = request_wrapper.transitionProbabilityRequest.offset

                # perform action
                transition_probability = self.ServiceProviderClass.get_transition_probability(index, offset)

                # create response
                transition_probability_response = prismGrpc_pb2.DoubleResponse(
                    value=wrappers_pb2.DoubleValue(
                        value=transition_probability) if transition_probability is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    transitionProbabilityResponse=transition_probability_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "transitionTargetRequest":
                # get arguments from request
                index = request_wrapper.transitionTargetRequest.index
                offset = request_wrapper.transitionTargetRequest.offset

                # perform action
                transition_target = self.ServiceProviderClass.compute_transition_target(index, offset)

                # create response
                transition_target_response = prismGrpc_pb2.State(
                    state_object_id=transition_target.object_id
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    transitionTargetResponse=transition_target_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "labelTrueRequest":
                # get arguments from request
                index = request_wrapper.labelTrueRequest.index

                # perform action
                label_true = self.ServiceProviderClass.is_label_true(index)

                # create response
                label_true_response = prismGrpc_pb2.BoolResponse(
                    value=wrappers_pb2.BoolValue(value=label_true) if label_true is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    labelTrueResponse=label_true_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "rewardStructNamesRequest":
                # no arguments

                # perform action
                reward_struct_names = self.ServiceProviderClass.get_reward_struct_names()

                # create response
                reward_struct_names_response = prismGrpc_pb2.StringArrayResponse(
                    values=[wrappers_pb2.StringValue(value=name) for name in
                            reward_struct_names] if reward_struct_names else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    rewardStructNamesResponse=reward_struct_names_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "stateRewardRequest":
                # get arguments from request
                reward = request_wrapper.stateActionRewardRequest.reward
                state_object_id = request_wrapper.stateActionRewardRequest.state.state_object_id

                # get state
                state = self.prism_object_map.get(state_object_id)

                # perform action
                state_reward = self.ServiceProviderClass.get_state_reward(reward, state)

                # create response
                state_reward_response = prismGrpc_pb2.DoubleResponse(
                    value=wrappers_pb2.DoubleValue(value=state_reward) if state_reward is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    stateRewardResponse=state_reward_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "stateActionRewardRequest":
                # get arguments from request
                reward_struct = request_wrapper.stateActionRewardRequest.reward
                state_object_id = request_wrapper.stateActionRewardRequest.state.state_object_id
                action = None if not request_wrapper.stateActionRewardRequest.HasField("action") \
                    else request_wrapper.stateActionRewardRequest.action.value

                # get state
                state = self.prism_object_map.get(state_object_id)

                # perform action
                state_action_reward = self.ServiceProviderClass.get_state_action_reward(reward_struct, state, action)

                # create response
                state_action_reward_response = prismGrpc_pb2.DoubleResponse(
                    value=wrappers_pb2.DoubleValue(
                        value=state_action_reward) if state_action_reward is not None else None
                )

                # wrap response
                client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
                    stateActionRewardResponse=state_action_reward_response
                )

                # send response
                self.send_response(client_model_generator_response_wrapper)
            elif request_type == "closeClientModelGeneratorRequest":
                self.logger.info(f"[STREAM] - Closing stream")
                return
            elif request_type == "exportTransToFileResponse":
                self.logger.info(
                    f"[STREAM] - received exportTransToFileResponse {request_wrapper.exportTransToFileResponse.status}")
                return
            elif request_type == "modelCheckPropStringResponse":
                self.__result.result = request_wrapper.modelCheckPropStringResponse.result
                return
            else:
                self.logger.error(f"[STREAM] - Unknown request type {request_type}")
                return request_wrapper

    def send_response(self, response):
        # check whether 'response' is actually of type ClientModelGeneratorResponseWrapper
        if not isinstance(response, prismGrpc_pb2.ClientModelGeneratorResponseWrapper):
            self.logger.error("[STREAM] - The response is not of type ClientModelGeneratorResponseWrapper")
            return

        # send response
        handle_requests_response = self.stub.ClientModelGenerator(iter([response]))
        self.logger.info(f"[STREAM] - sending " + str(response).replace("\n", ""))
        self.handle_requests(handle_requests_response)

    def load_model_generator(self):
        # Instantiate the LoadModelGeneratorRequest object
        load_model_generator_request = prismGrpc_pb2.LoadModelGeneratorRequest(
            prism_object_id=self.prism_object_id,
            model_generator_object_id=self.object_id
        )

        client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
            loadModelGeneratorRequest=load_model_generator_request
        )

        # sending the request
        requests = self.stub.ClientModelGenerator(iter([client_model_generator_response_wrapper]))
        self.logger.info(f"[STREAM] - sending " + str(client_model_generator_response_wrapper).replace("\n", ""))

        # handle the response
        self.handle_requests(requests)

    # model check while in service provision mode
    def model_check(self, prop):
        self.logger.info("Model checking property in client service provision mode.")

        self.__result = Result()
        # create request
        model_check_prop_string_request = prismGrpc_pb2.ModelCheckPropStringRequest(
            prism_object_id=self.prism_object_id,
            properties_string=prop,
            result_object_id=self.__result.object_id)

        client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
            modelCheckPropStringRequest=model_check_prop_string_request
        )

        # send request
        requests = self.stub.ClientModelGenerator(iter([client_model_generator_response_wrapper]))
        self.logger.info(f"[STREAM] - sending " + str(client_model_generator_response_wrapper).replace("\n", ""))

        # handle the response
        self.handle_requests(requests)

        return self.__result

    def export_trans_to_file(self, ordered, export_type, filename):
        # create request
        export_trans_to_file_request = prismGrpc_pb2.ExportTransToFileRequest(
            prism_object_id=self.prism_object_id,
            ordered=ordered,
            export_type=export_type,
            file_name=filename
        )
        client_model_generator_response_wrapper = prismGrpc_pb2.ClientModelGeneratorResponseWrapper(
            exportTransToFileRequest=export_trans_to_file_request
        )

        # send request
        requests = self.stub.ClientModelGenerator(iter([client_model_generator_response_wrapper]))
        self.logger.info(f"[STREAM] - sending " + str(client_model_generator_response_wrapper).replace("\n", ""))

        # handle the response
        self.handle_requests(requests)

    # function to upload a generic file to the prism server
    def upload_file(self, filename):
        self.logger.info(f"Uploading file {filename} to prism server.")

        # Check if file exists
        if not os.path.isfile(filename):
            self.logger.error(f"File {filename} not found. Aborting upload.")
            raise PrismPyException(f"File {filename} not found. Aborting upload.")

        try:
            # Upload file
            response = self.stub.UploadFile(self.__get_file_chunks(filename))
            self.logger.info(f"File successfully uploaded to {response.filename}")
            return response

        except _InactiveRpcError:
            # If there was an error, it is likely that the service is unavailable
            self.logger.error(f"gRPC service seems to be unavailable. Please make sure the service is running!!!.")
        except Exception as e:
            self.logger.error(f"Unknown error.\n{str(e)}")

    # private function to cut a file into chunks to be uploaded to the gRPC service
    # this function should only be called via upload_file()
    def __get_file_chunks(self, filename):
        self.logger.info(f"Cutting file {filename} into chunks.")

        try:
            # First, yield a request containing the filename
            yield prismGrpc_pb2.UploadRequest(filename=filename)
            self.logger.info(f"Uploaded filename {filename}")

            with open(filename, 'rb') as f:
                while True:
                    # read a chunk of the file
                    piece = f.read(self.__CHUNK_SIZE)
                    if len(piece) == 0:
                        # if the chunk is empty, we have reached the end of the file
                        return
                    # yield the chunk to the gRPC service
                    yield prismGrpc_pb2.UploadRequest(chunk_data=piece)
                    self.logger.info(f"Uploaded chunk of size {len(piece)}")
        except Exception as e:
            self.logger.error(f"Unknown error.\n{str(e)}")

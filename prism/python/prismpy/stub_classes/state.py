from prismpy.services.grpc import prismGrpc_pb2
from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class State(PrismPyBaseModel):

    # var_values = None

    def __init__(self, *args):

        if len(args) == 1:
            if isinstance(args[0], int):
                # self.var_values = [None] * args[0]

                # init with state_number
                super().__init__(standalone=True, state_number=args[0])

            elif isinstance(args[0], State):
                # self.var_values = list(args[0].var_values)

                # handling the init manually below
                super().__init__(standalone=False)

                # For arg_state_object_ids
                arg_ids = prismGrpc_pb2.InitStateRequest.ArgStateObjectIDs(
                    state_object_id=[args[0].object_id])

                # create InitStateRequest
                init_request = prismGrpc_pb2.InitStateRequest(
                    state_object_id=self.object_id,
                    arg_state_object_ids=arg_ids)

                # send request
                self.stub.InitState(init_request)
        elif len(args) == 2:
            if all(isinstance(arg, State) for arg in args):
                # self.var_values = list(args[0].var_values) + list(args[1].var_values)

                # handling the init manually below
                super().__init__(standalone=False)

                arg_ids = prismGrpc_pb2.InitStateRequest.ArgStateObjectIDs(
                    state_object_id=[args[0].object_id, args[1].object_id])

                init_request = prismGrpc_pb2.InitStateRequest(
                    state_object_id=self.object_id,
                    arg_state_object_ids=arg_ids)

                # send request
                self.stub.InitState(init_request)
        else:
            self.logger.error("Invalid number of arguments for State constructor.")

    def set_value(self, i, val):
        # currently only supporting int values
        # self.var_values[i] = val

        # create a SetValueRequest
        request = prismGrpc_pb2.SetStateValueRequest(state_object_id=self.object_id,
                                                     index=i,
                                                     value=val)

        # send request
        self.stub.SetStateValue(request)
        return self

    @property
    def var_values(self):
        # create a StateVarValuesRequest
        request = prismGrpc_pb2.StateVarValuesRequest(state_object_id=self.object_id)

        # send request
        return self.stub.StateVarValues(request).values

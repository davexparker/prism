from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel
from prismpy.stub_classes.values import Values
from prismpy.services.grpc import prismGrpc_pb2


class UndefinedConstants(PrismPyBaseModel):
    undefined_constants = None

    def __init__(self, modules_file, properties_file, property_object):
        super().__init__(standalone=True, module_object_id=modules_file.object_id,
                         properties_file_object_id=properties_file.object_id,
                         property_object_id=property_object.object_id)

    def define_using_const_switch(self, constant):
        self.logger.info("Define using const switch {}.".format(constant))

        # create request
        request = prismGrpc_pb2.DefineUsingConstSwitchRequest(undefined_constants_object_id=self.object_id,
                                                              constant=constant)

        # Make the RPC call to DefineUsingConstSwitch
        response = self.stub.DefineUsingConstSwitch(request)
        self.logger.info("Received message {}.".format(response.status))

    def get_number_property_iterations(self):
        self.logger.info("Get number of property iterations.")

        # create request
        request = prismGrpc_pb2.GetNumberPropertyIterationsRequest(undefined_constants_object_id=self.object_id)

        # Make the RPC call to GetNumberPropertyIterations
        response = self.stub.GetNumberPropertyIterations(request)

        self.logger.info("Received message {}.".format(response.status))

        return response.number_iterations

    def get_pf_constant_values(self):
        self.logger.info("Get properties file constant values.")

        # object to hold result
        values = Values()

        # create request
        request = prismGrpc_pb2.GetPFConstantValuesRequest(undefined_constants_object_id=self.object_id,
                                                           values_object_id=values.object_id)

        # Make the RPC call to GetPFConstantValues
        response = self.stub.GetPFConstantValues(request)
        values.current_values = response.values
        self.logger.info("Received messageee {}.".format(response.status))

        return values

    def iterate_property(self):
        self.logger.info("Iterate property.")

        # create request
        request = prismGrpc_pb2.IteratePropertyRequest(undefined_constants_object_id=self.object_id)

        # Make the RPC call to IteratePolicy
        response = self.stub.IterateProperty(request)
        self.logger.info("Received message {}.".format(response.status))

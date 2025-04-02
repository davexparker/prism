from prismpy.stub_classes.property_object import PropertyObject
from prismpy.services.grpc import prismGrpc_pb2
from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class PropertiesFile(PrismPyBaseModel):
    # storing this for future use; so far never used
    properties_file_path = None
    properties_string = None

    def __init__(self, properties_file_path=None, properties_string=None):
        super().__init__(standalone=False)

        self.properties_file_path = properties_file_path
        self.properties_string = properties_string

    def get_property_object(self, property_index):
        self.logger.info("Get property object {}.".format(property_index))
        # creating property object to populate and return
        property_object = PropertyObject()

        # create GetPropertyObjectRequest
        request = prismGrpc_pb2.GetPropertyObjectRequest(properties_file_object_id=self.object_id,
                                                         property_object_id=property_object.object_id,
                                                         property_index=property_index)

        # Make the RPC call to GetPropertyObject
        response = self.stub.GetPropertyObject(request)
        self.logger.info("Received message {}.".format(response.status))

        # populate property object
        property_object.property_string = response.property

        return property_object

    # def __forward(self, name, *args, **kwargs):
    #     self.logger.info("Forwarding unsupported call {} to prism server.".format(name))
    #     # create forward request
    #     request = prismGrpc_pb2.PropertiesFileForwardMethodCallRequest(properties_file_object_id=str(id(self)),
    #                                                                    method=name,
    #                                                                    args=args,
    #                                                                    kwargs=kwargs)
    #     # Make the RPC call to Forward
    #     response = self.stub.PropertiesFileForwardMethodCall(request)
    #     self.logger.info("Received message {}.".format(response.status))
    #
    #     return response.result
    #
    # def __getattr__(self, name):
    #     return lambda *args, **kwargs: self.__forward(name, *args, **kwargs)

    def get_undefined_constants_used_in_property(self, property_object):
        self.logger.info("Get undefined constants used in property {}.".format(property_object))

        # create GetUndefinedConstantsUsedInPropertyRequest
        request = prismGrpc_pb2.GetUndefinedConstantsUsedInPropertyRequest(
            properties_file_object_id=self.object_id,
            property_object_id=property_object.object_id)

        # Make the RPC call to GetUndefinedConstantsUsedInProperty
        response = self.stub.GetUndefinedConstantsUsedInProperty(request)
        self.logger.info("Received message {}.".format(response.status))

        return response.constants

    def set_some_undefined_constants(self, values):
        self.logger.info("Set some undefined constants")

        # create SetSomeUndefinedConstantsRequest
        request = prismGrpc_pb2.SetSomeUndefinedConstantsRequest(
            properties_file_object_id=self.object_id,
            values_object_id=values.object_id)

        # Make the RPC call to SetSomeUndefinedConstants
        response = self.stub.SetSomeUndefinedConstants(request)
        self.logger.info("Received message {}.".format(response.status))

        return response.status

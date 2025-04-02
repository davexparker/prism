from prismpy.services.grpc import prismGrpc_pb2
from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class Values(PrismPyBaseModel):
    current_values = None

    def __init__(self):
        super().__init__(standalone=True)

    def add_value(self, const_name, value):
        self.logger.info("Add value {}.".format(value))
        request = prismGrpc_pb2.AddValueRequest(values_object_id=self.object_id,
                                                const_name=const_name,
                                                value=value)

        response = self.stub.AddValue(request)
        self.logger.info("Received message {}.".format(response.status))
        self.current_values = response.result
        return response.status

    def __str__(self):
        return str(self.current_values)

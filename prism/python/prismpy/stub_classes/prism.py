import grpc
from prismpy.services.producing.service_provider_base import ServiceProviderBase
from prismpy.stub_classes.modules_file import ModulesFile
from prismpy.services.prismpy_exceptions import PrismPyException
from prismpy.stub_classes.properties_file import PropertiesFile
from prismpy.stub_classes.result import Result
from prismpy.services.grpc import prismGrpc_pb2
from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class Prism(PrismPyBaseModel):

    # list of export types
    EXPORT_PLAIN = 1
    EXPORT_MATLAB = 2
    EXPORT_DOT = 3
    EXPORT_MRMC = 4
    EXPORT_ROWS = 5
    EXPORT_DOT_STATES = 6

    main_log_object_id = None

    def __init__(self, main_log):
        self.service_provider = None
        self.main_log_object_id = main_log.object_id
        super().__init__(standalone=True, main_log_object_id=main_log.object_id)

        self.__client_service_provision = False

    def initialise(self):
        if self.main_log_object_id is None:
            self.logger.error("No log file specified. Please specify a log file.")
            raise PrismPyException("No log file specified. Please specify a log file.")
        else:
            self.logger.info("Initialising Prism Engine.")
            # Create a request with PrismDevNullLog
            request = prismGrpc_pb2.InitialiseRequest(prism_object_id=self.object_id)

            try:
                # Call the Initialise method
                response = self.stub.Initialise(request)
                self.logger.info("Received message {}".format(response.status))
            except grpc.RpcError as e:
                self.logger.error(
                    "Could not establish connection to the gRPC server. Please make sure the Prism server is running.")
                self.logger.error("gRPC error info: {}".format(e.details()))
                exit(1)
        return self

    def parse_model_file(self, model_file_path):
        # upload the stub_classes file to prism server
        upload_response = self.upload_file(model_file_path)

        # create ModuleFile object to populate and return
        modules_file = ModulesFile(model_file_path)

        # instruct prism to parse the uploaded file
        self.logger.info("Parsing file {}.".format(upload_response.filename))

        # Create a ParseModelRequest
        request = prismGrpc_pb2.ParseModelFileRequest(prism_object_id=self.object_id,
                                                      module_object_id=modules_file.object_id,
                                                      model_file_name=upload_response.filename)

        # Make the RPC call to ParseModelFile
        response = self.stub.ParseModelFile(request)

        self.logger.info("Received message {}.".format(response.status))

        return modules_file

    def load_prism_model(self, module_file):
        self.logger.info("Loading prism stub_classes with module file" + module_file.model_file_name)

        # Create a LoadPRISMModelRequest
        request = prismGrpc_pb2.LoadPRISMModelRequest(prism_object_id=self.object_id,
                                                      module_object_id=module_file.object_id)

        # Make the RPC call to LoadPRISMModel
        response = self.stub.LoadPRISMModel(request)

        self.logger.info("Received message {}.".format(response.status))

        return self

    def parse_properties_file(self, module_file, properties_file_path):
        self.logger.info(
            "Parse property file " + properties_file_path)

        # upload the stub_classes file to prism server
        upload_response = self.upload_file(properties_file_path)

        # create PropertiesFile object to populate and return
        properties_file = PropertiesFile(properties_file_path=properties_file_path)

        # create ParsePropertiesFileRequest
        request = prismGrpc_pb2.ParsePropertiesFileRequest(prism_object_id=self.object_id,
                                                           module_object_id=module_file.object_id,
                                                           properties_file_object_id=properties_file.object_id,
                                                           properties_file_name=upload_response.filename)

        # Make the RPC call to ParsePropertiesFile
        response = self.stub.ParsePropertiesFile(request)

        self.logger.info("Received message {}.".format(response.status))

        return properties_file

    # since overloading is not possible in python, we utilise this small hack
    def model_check(self, *args):
        # checking if we're in a client service provision mode
        if self.__client_service_provision:
            # currently only supports property string
            return self.service_provider.model_check(args[0])
        # not in client service provision mode
        elif len(args) == 1:
            return self.__model_check_properties_string(args[0])
        elif len(args) == 2:
            return self.__model_check_prop_file_prop_obj(args[0], args[1])
        else:
            self.logger.error("Invalid number of arguments passed to model_check method.")
            raise PrismPyException("Invalid number of arguments passed to model_check method.")

    def __model_check_prop_file_prop_obj(self, properties_file, property_object):
        self.logger.info("Model checking property")

        # Create ResultFile object to populate and return
        result = Result()

        # Create a ModelCheckRequest
        request = prismGrpc_pb2.ModelCheckPropFilePropObjRequest(
            prism_object_id=self.object_id,
            properties_file_object_id=properties_file.object_id,
            property_object_id=property_object.object_id,
            result_object_id=result.object_id)

        # Make the RPC call to ModelCheck
        response = self.stub.ModelCheckPropFilePropObj(request)

        # Populate the result object
        result.result = response.result

        self.logger.info("Received message {}.".format(response.status))

        return result

    def __model_check_properties_string(self, properties_string):
        self.logger.info("Model checking property")

        # Create ResultFile object to populate and return
        result = Result()

        # Create a ModelCheckRequest
        request = prismGrpc_pb2.ModelCheckPropStringRequest(
            prism_object_id=self.object_id,
            properties_string=properties_string,
            result_object_id=result.object_id)

        # Make the RPC call to ModelCheck
        response = self.stub.ModelCheckPropString(request)

        # Populate the result object
        result.result = response.result

        self.logger.info("Received message {}.".format(response.status))

        return result

    def parse_properties_string(self, modules_file, properties_string):
        self.logger.info("Parse property string " + properties_string)

        # create PropertiesFile object to populate and return
        properties_file = PropertiesFile(properties_string=properties_string)

        # create ParsePropertiesFileRequest
        request = prismGrpc_pb2.ParsePropertiesStringRequest(prism_object_id=self.object_id,
                                                             module_object_id=modules_file.object_id,
                                                             properties_string=properties_string,
                                                             properties_file_object_id=properties_file.object_id)

        # Make the RPC call to ParsePropertiesFile
        response = self.stub.ParsePropertiesString(request)

        self.logger.info("Received message {}.".format(response.status))

        return properties_file

    def close_down(self):
        self.logger.info("Closing down Prism Engine.")

        # Create a CloseDownRequest
        request = prismGrpc_pb2.CloseDownRequest(prism_object_id=self.object_id)

        # Make the RPC call to CloseDown
        response = self.stub.CloseDown(request)

        self.logger.info("Received message {}.".format(response.status))

        # Garbage collect all objects
        super().clean_up()
        return self

    def load_model_generator(self, model_gen):
        self.logger.info("Loading model generator")

        self.__client_service_provision = True

        self.service_provider = ServiceProviderBase(model_gen, self.object_id)

        self.service_provider.load_model_generator()

    def export_trans_to_file(self, ordered, export_type, filename):
        self.logger.info("Exporting transitions to file")

        self.service_provider.export_trans_to_file(ordered, export_type, filename)
        self.download_file(filename)




import os
import uuid
import re
from abc import ABC

import grpc
from grpc._channel import _InactiveRpcError

from prismpy.services.prismpy_exceptions import PrismPyException
from prismpy.services import prismpy_logger
from prismpy.services.grpc import prismGrpc_pb2_grpc, prismGrpc_pb2


class PrismPyBaseModel(ABC):
    prism_object_map = {}

    # specify the size of chunks to read from the file
    __CHUNK_SIZE = 1024 * 1024  # 1MB

    # singleton logger
    logger = prismpy_logger.PrismPyLogger().get_logger()

    # object_id of the object in the prism server
    __object_id = None
    __object_id_accessed = False

    # gRPC channel and stub
    channel = None
    stub = None

    def __init__(self, standalone, **kwargs):
        # id of the object in the prism server
        self.__object_id = str(uuid.uuid4())
        self.__create_channel()

        # if an object is created as a standalone object, it will be created on the server side too
        if standalone:
            self.__init_grpc_object(**kwargs)

        # add this object to the object map
        PrismPyBaseModel.prism_object_map[self.object_id] = self

    # private function to create a representation of this object on the server side
    def __init_grpc_object(self, **kwargs):
        class_name = self.__class__.__name__

        # convert class_name to snake_case
        s1 = re.sub('(.)([A-Z][a-z]+)', r'\1_\2', class_name)
        snake_case_class_name = re.sub('([a-z0-9])([A-Z])', r'\1_\2', s1).lower()

        # self.logger.info(f"Init gRPC object for class {class_name}.")

        init_method_name = "Init" + class_name
        attr_name = f"{snake_case_class_name}_object_id"

        try:
            # Try to fetch the method from the stub
            init_method = getattr(self.stub, init_method_name)
        except AttributeError:
            self.logger.error(f"Method {init_method_name} not found in gRPC stub.")
            raise

        # Initialize request attributes with the object_id
        request_attrs = {attr_name: self.object_id}

        # Update request attributes with additional provided attributes
        request_attrs.update(kwargs)

        request = getattr(prismGrpc_pb2, f"{init_method_name}Request")(**request_attrs)
        try:
            response = init_method(request)
            # self.logger.info(f"Received message {response.status}.")
        except _InactiveRpcError:
            self.logger.error(f"gRPC service seems to be unavailable. Please make sure the service is running.")
            exit(1)

    # property to access the object_id of the object in the prism server
    # defined as a property to enable garbage collection
    @property
    def object_id(self):
        self.__object_id_accessed = True
        return self.__object_id

    # private function to create a channel to the gRPC service
    def __create_channel(self):
        # Open a gRPC channel
        self.channel = grpc.insecure_channel('localhost:50051')

        # Create a stub (client)
        self.stub = prismGrpc_pb2_grpc.PrismProtoServiceStub(self.channel)

    # private function to close the channel to the gRPC service
    def __close_channel(self):
        self.channel.close()

        self.stub = None
        self.channel = None

    # private garbage collector which deletes the object on the server, if it was not already deleted
    # def __del__(self):
    #     # check if object was uploaded to the server
    #     if self.__object_id_accessed:
    #
    #         # opening channel if not already open
    #         if self.stub is None:
    #             self.__create_channel()
    #
    #         # create a delete object request
    #         request = prismGrpc_pb2.DeleteObjectRequest(object_id=self.__object_id)
    #
    #         # send request
    #         try:
    #             self.stub.DeleteObject(request)
    #         except _InactiveRpcError:
    #             self.logger.error(f"[Garbage Collector] - gRPC service seems to be unavailable. Please make sure the "
    #                               f"service is running.")
    #         self.__close_channel()

    # function to download a generic file from the prism server
    def download_file(self, filename):
        self.logger.info(f"Downloading file {filename} from prism server.")
        request = prismGrpc_pb2.DownloadRequest(fileName=filename)

        with open(filename, 'wb') as f:
            for chunk in self.stub.DownloadFile(request):
                f.write(chunk.content)

        self.logger.info(f"File successfully downloaded to {filename}")


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

    def clean_up(self):
        self.logger.info("[Garbage Collection] Cleaning up...")

        for prism_object in self.prism_object_map.values():
            if prism_object.__object_id_accessed:
                self.logger.info("[Garbage Collection] Deleting object: " + prism_object.__object_id)

                request = prismGrpc_pb2.DeleteObjectRequest(object_id=prism_object.__object_id)
                # send request
            try:
                self.stub.DeleteObject(request)
            except Exception as e:
                self.logger.error("Error deleting object: " + str(e))

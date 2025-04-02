import os
import sys

"""
REASONS FOR THIS FILE:
    1. Easier imports for the end user 
        Instead of having to import 'from prismpy.stub_classes.xzz import XYZ', 
        user can simply import 'from prismpy import xzz'
    2. Sys path appending for grpc files
        Since one of the generated grpc files uses relative imports, we need to append the path to the grpc directory.
        Alternatively, we could change the import statements in the generated grpc files to absolute imports but 
        then every time we change the proto file and generate new grpc files, we would need re-apply these changes.
        This seems like a more robust solution since we don't have to worry about this in the future.
"""
# Get the directory of the currently executing script
current_directory = os.path.dirname(os.path.abspath(__file__))

# Construct the path to the grpc directory relative to the script's location
grpc_path = os.path.join(current_directory, "services", "grpc")
sys.path.append(grpc_path)

# forwarding stub classes to make imports easier for the user
from .stub_classes.model_generator import ModelGenerator
from .stub_classes.model_type import ModelType
from .stub_classes.modules_file import ModulesFile
from .stub_classes.prism import Prism
from .stub_classes.prism_log import PrismLog
from .stub_classes.properties_file import PropertiesFile
from .stub_classes.property_object import PropertyObject
from .stub_classes.result import Result
from .stub_classes.reward_generator import RewardGenerator
from .stub_classes.state import State
from .stub_classes.undefined_constants import UndefinedConstants
from .stub_classes.values import Values




#!/bin/bash

# Define a function to check last command status
function check_command {
    if [ $? -ne 0 ]; then
        echo "Error: last command failed. Exiting script."
        exit 1
    fi
}

# Start of the script
echo "Starting java compilaton..."

# Remove previous proto files
echo "Removing the services/PrismGrpc.java..."
rm -rf services/PrismGrpc.java
check_command

echo "Removing the services/PrismProtoServiceGrpc.java..."
rm -rf services/PrismProtoServiceGrpc.java
check_command

# Run mvn clean install
echo "Running mvn clean install..."
mvn clean install
check_command

echo "Moving PrismProtoServiceGrpc.java file to server/services/..."
mv target/generated-sources/protobuf/grpc-java/grpc/server/services/PrismProtoServiceGrpc.java server/services/
check_command

echo "Moving PrismGrpc.java file to to server/services..."
mv target/generated-sources/protobuf/java/grpc/server/services/PrismGrpc.java server/services/
check_command

echo "Java-proto compilation finished successfully. Continuing with python-proto compilation..."


# Navigate to the prism-python directory
echo "Navigating to the prism-python directory..."
cd client/
check_command


# Assumption here is that there already exists a venv with all requirements installed
# Activating virtual env
echo "Activating virtual environment..."
source venv/bin/activate
check_command
sleep 2

# Execute the proto command

echo "Executing the proto command..."
python -m grpc_tools.protoc -I../proto/ --python_out=prismpy/services/grpc --grpc_python_out=prismpy/services/grpc prismGrpc.proto
check_command

# Deactivate the venv
echo "Deactivating virtual environment..."
deactivate
check_command

# Successfull exit
echo "Proto compilation finished successfully."

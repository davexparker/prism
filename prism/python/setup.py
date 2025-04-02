from setuptools import setup, find_packages

setup(
    name="prismpy",
    version="0.1",
    packages=find_packages(),
    install_requires=[
        'grpcio==1.54.0',
        'grpcio-tools==1.54.0',
        'protobuf==4.23.0',
        'halo==0.0.31',
    ],
    python_requires='>=3.8',
    author="Christoph Weinhuber",
    author_email="christoph.weinhuber@tum.de",
    description="Package to connect to the Prism model checker via gRPC",
    keywords="prism grpc prismpy model checking",
    url="https://github.com/yourusername/prismpy",
    classifiers=[
        "License :: OSI Approved :: MIT License",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
    ],
)


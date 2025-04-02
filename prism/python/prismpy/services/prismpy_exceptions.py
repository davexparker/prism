class PrismPyException(Exception):
    """Base class for exceptions in PrismPy."""

    def __init__(self, message):
        self.message = message
        super().__init__(message)

    def __str__(self):
        return f'{self.__class__.__name__}: {self.message}'


class PrismPyConnectionException(PrismPyException):
    """Raised when there are connection issues with the Prism server."""

    def __init__(self, message="Connection error occurred"):
        super().__init__(message)


class PrismPyModelException(PrismPyException):
    """Raised when there are issues with the stub_classes checker."""

    def __init__(self, message="Model error occurred"):
        super().__init__(message)


class PrismPyInputException(PrismPyException):
    """Raised when there are issues with the input."""

    def __init__(self, message="Input error occurred"):
        super().__init__(message)


class PrismPyServerException(PrismPyException):
    """Raised when there are server-side issues."""

    def __init__(self, message="Server error occurred"):
        super().__init__(message)


class PrismClientServiceNotImplementedException(PrismPyException):
    """Raised when a service is not implemented."""

    def __init__(self, message="Service not implemented"):
        super().__init__(message)

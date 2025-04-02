from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class Result(PrismPyBaseModel):
    result = None

    def __init__(self):
        super().__init__(standalone=True)

    def get_result(self):
        return self.result

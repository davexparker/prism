from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class PrismLog(PrismPyBaseModel):
    type = None

    def __init__(self, type):
        self.type = type
        super().__init__(standalone=True, type=type)



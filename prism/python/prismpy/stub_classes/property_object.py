from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class PropertyObject(PrismPyBaseModel):
    property_string = None

    def __init__(self):
        super().__init__(standalone=False)
        # id of the module object in the prism server

    def __str__(self):
        return self.property_string

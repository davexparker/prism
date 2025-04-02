from prismpy.services.consuming.prismpy_base_model import PrismPyBaseModel


class ModulesFile(PrismPyBaseModel):

    model_file_name = None

    def __init__(self, model_file_name):
        super().__init__(standalone=False)
        # name of original property file
        self.model_file_name = model_file_name


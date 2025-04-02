from abc import ABC, abstractmethod
from prismpy.services.prismpy_exceptions import PrismClientServiceNotImplementedException


class ModelGenerator(ABC):
    @abstractmethod
    def get_model_type(self):
        raise PrismClientServiceNotImplementedException("get_model_type not implemented.")

    @abstractmethod
    def get_var_names(self):
        raise PrismClientServiceNotImplementedException("get_var_names not implemented.")

    @abstractmethod
    def get_var_types(self):
        raise PrismClientServiceNotImplementedException("get_var_types not implemented.")

    @abstractmethod
    def get_var_declaration_type(self, i):
        raise PrismClientServiceNotImplementedException("get_var_declaration_type not implemented.")

    @abstractmethod
    def get_label_names(self):
        raise PrismClientServiceNotImplementedException("get_label_names not implemented.")

    @abstractmethod
    def get_initial_state(self):
        raise PrismClientServiceNotImplementedException("get_initial_state not implemented.")

    @abstractmethod
    def explore_state(self, explore_state):
        raise PrismClientServiceNotImplementedException("explore_state not implemented.")

    @abstractmethod
    def get_num_choices(self):
        raise PrismClientServiceNotImplementedException("get_num_choices not implemented.")

    @abstractmethod
    def get_num_transitions(self, i):
        raise PrismClientServiceNotImplementedException("get_num_transitions not implemented.")

    @abstractmethod
    def get_transition_action(self, i, offset):
        raise PrismClientServiceNotImplementedException("get_transition_action not implemented.")

    @abstractmethod
    def get_transition_probability(self, i, offset):
        raise PrismClientServiceNotImplementedException("get_transition_probability not implemented.")

    @abstractmethod
    def compute_transition_target(self, i, offset):
        raise PrismClientServiceNotImplementedException("compute_transition_target not implemented.")

    @abstractmethod
    def is_label_true(self, i):
        raise PrismClientServiceNotImplementedException("is_label_true not implemented.")

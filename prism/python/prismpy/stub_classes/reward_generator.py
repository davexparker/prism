from abc import ABC, abstractmethod
from prismpy.services.prismpy_exceptions import PrismClientServiceNotImplementedException


class RewardGenerator(ABC):
    @abstractmethod
    def get_reward_struct_names(self):
        raise PrismClientServiceNotImplementedException("get_reward_struct_names not implemented.")

    @abstractmethod
    def get_state_reward(self, r, state):
        raise PrismClientServiceNotImplementedException("get_state_reward not implemented.")

    @abstractmethod
    def get_state_action_reward(self, r, state, action):
        raise PrismClientServiceNotImplementedException("get_state_action_reward not implemented.")

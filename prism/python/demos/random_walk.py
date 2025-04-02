from prismpy import ModelGenerator
from prismpy import RewardGenerator
from prismpy import State


class RandomWalk(ModelGenerator, RewardGenerator):
    def __init__(self, n, p):
        self.n = n
        self.p = p
        self._explore_state = None
        self.x = None

    def get_model_type(self):
        # return ModelType.model_types["DTMC"]
        return "DTMC"

    def get_var_names(self):
        return ["x"]

    def get_var_types(self):
        return ["int"]  # Python does not have a direct equivalent for Java's TypeInt

    def get_var_declaration_type(self, i):
        # TODO: Ask about the purpose of this method
        # TODO: Right now we're returning an Array Int32
        # Might cause problems later on if n can be of a different type
        return -self.n, self.n

    def get_label_names(self):
        return ["end", "left", "right"]

    def get_initial_state(self):
        return State(1).set_value(0,0)

    def explore_state(self, explore_state):
        self._explore_state = explore_state
        self.x = explore_state.var_values[0]

    def get_num_choices(self):
        return 1

    def get_num_transitions(self, i):
        return 1 if self.x == -self.n or self.x == self.n else 2

    def get_transition_action(self, i, offset):
        # TODO: Usually returns an object...
        # TODO: Ask for different examples
        return None

    def get_transition_probability(self, i, offset):
        return 1.0 if self.x == -self.n or self.x == self.n else (1 - self.p if offset == 0 else self.p)

    def compute_transition_target(self, i, offset):
        target = State(self._explore_state)

        if self.x == -self.n or self.x == self.n:
            return target
        else:
            return target.set_value(0, self.x - 1 if offset == 0 else self.x + 1)

    def is_label_true(self, i):
        if i == 0:
            return self.x == -self.n or self.x == self.n
        elif i == 1:
            return self.x == -self.n
        elif i == 2:
            return self.x == self.n
        else:
            return False

    def get_reward_struct_names(self):
        return ["r"]

    def get_state_reward(self, r, state):
        return 1.0

    def get_state_action_reward(self, r, state, action):
        return 0.0
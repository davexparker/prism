class ModelType:
    PROBABILITY = "Probability"
    RATE = "Rate"

    model_types = {
        "CTMC": {
            "full_name": "continuous-time Markov chain",
            "choices_sum_to_one": False,
            "continuous_time": True,
            "nondeterministic": False,
            "probability_or_rate": RATE,
        },
        "CTMDP": {
            "full_name": "continuous-time Markov decision process",
            "choices_sum_to_one": False,
            "continuous_time": True,
            "probability_or_rate": RATE,
            "remove_nondeterminism": "CTMC",
        },
        "DTMC": {
            "full_name": "discrete-time Markov chain",
            "nondeterministic": False,
        },
        "LTS": {
            "full_name": "labelled transition system",
            "is_probabilistic": False,
            "probability_or_rate": "",
            "remove_nondeterminism": "DTMC",
        },
        "MDP": {
            "full_name": "Markov decision process",
            "remove_nondeterminism": "DTMC",
        },
        "POMDP": {
            "full_name": "partially observable Markov decision process",
            "partially_observable": True,
            "remove_nondeterminism": "DTMC",
        },
        "POPTA": {
            "full_name": "partially observable probabilistic timed automaton",
            "continuous_time": True,
            "real_time": True,
            "partially_observable": True,
            "remove_nondeterminism": "DTMC",
        },
        "PTA": {
            "full_name": "probabilistic timed automaton",
            "continuous_time": True,
            "real_time": True,
        },
        "STPG": {
            "full_name": "stochastic two-player game",
            "multiple_players": True,
            "remove_nondeterminism": "DTMC",
        },
        "SMG": {
            "full_name": "stochastic multi-player game",
            "multiple_players": True,
            "remove_nondeterminism": "DTMC",
        },
        "IDTMC": {
            "full_name": "interval discrete-time Markov chain",
            "nondeterministic": False,
            "uncertain": True,
        },
        "IMDP": {
            "full_name": "interval Markov decision process",
            "remove_nondeterminism": "DTMC",
            "uncertain": True,
        }
    }

    @classmethod
    def full_name(cls, model_type):
        return cls.model_types[model_type]["full_name"]

    @classmethod
    def keyword(cls, model_type):
        return model_type.lower()

    @classmethod
    def choices_sum_to_one(cls, model_type):
        return cls.model_types[model_type].get("choices_sum_to_one", True)

    @classmethod
    def continuous_time(cls, model_type):
        return cls.model_types[model_type].get("continuous_time", False)

    @classmethod
    def real_time(cls, model_type):
        return cls.model_types[model_type].get("real_time", False)

    @classmethod
    def nondeterministic(cls, model_type):
        return cls.model_types[model_type].get("nondeterministic", True)

    @classmethod
    def multiple_players(cls, model_type):
        return cls.model_types[model_type].get("multiple_players", False)

    @classmethod
    def is_probabilistic(cls, model_type):
        return cls.model_types[model_type].get("is_probabilistic", True)

    @classmethod
    def probability_or_rate(cls, model_type):
        return cls.model_types[model_type].get("probability_or_rate", cls.PROBABILITY)

    @classmethod
    def partially_observable(cls, model_type):
        return cls.model_types[model_type].get("partially_observable", False)

    @classmethod
    def uncertain(cls, model_type):
        return cls.model_types[model_type].get("uncertain", False)

    @classmethod
    def remove_nondeterminism(cls, model_type):
        return cls.model_types[model_type].get("remove_nondeterminism", model_type)

    @classmethod
    def parse_name(cls, name):
        try:
            return cls.model_types[name.upper()]
        except KeyError:
            return None

from prismpy import Prism, PrismLog
from random_walk import RandomWalk

# from prismpy.services.prismpy_logger import PrismPyLogger
# PrismPyLogger().set_level("DEBUG")

main_log = PrismLog("devnull")

# Initialise PRISM engine
prism = Prism(main_log)
prism.initialise()

# Create a model generator to specify the model that PRISM should build (in this case a simple random walk)
model_gen = RandomWalk(5, 0.6)
prism.load_model_generator(model_gen)

prism.export_trans_to_file(True, Prism.EXPORT_DOT_STATES, "dtmc.dot")

# Do some model checking and print the results
props = [
        "P=?[F \"end\"]",
        "P=?[F<=10 \"end\"]",
        "P=?[F \"left\"]",
        "P=?[F \"right\"]",
        "R=?[F \"end\"]"]

for prop in props:
    print(prop + ":")
    print(prism.model_check(prop).get_result())

prism.close_down()

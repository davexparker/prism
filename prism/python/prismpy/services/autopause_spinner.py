from halo import Halo
import builtins

class AutoPauseSpinner:
    _instance = None

    def __new__(cls, *args, **kwargs):
        if not cls._instance:
            cls._instance = super(AutoPauseSpinner, cls).__new__(cls)
        return cls._instance

    def __init__(self, text='Running...'):
        # Check if we've initialized it before
        if hasattr(self, 'initialized') and self.initialized:
            return

        self.spinner = Halo(text=text, spinner='dots')
        self.original_print = builtins.print
        self.initialized = True

    def start(self):
        builtins.print = self._new_print
        self.spinner.start()

    def stop(self):
        builtins.print = self.original_print
        self.spinner.stop()

    def _new_print(self, *args, **kwargs):
        self.spinner.stop()
        self.original_print(*args, **kwargs)
        self.spinner.start()

import logging

from prismpy.services.autopause_spinner import AutoPauseSpinner


class PrismPyLogger:
    _instance = None

    def __new__(cls, *args, **kwargs):
        if not cls._instance:
            cls._instance = super(PrismPyLogger, cls).__new__(cls, *args, **kwargs)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self, log_to_file=False, log_file='app.log'):
        if self._initialized:
            return
        self._initialized = True

        self.logger = logging.getLogger('prismpy')

        formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')

        # Log to console
        console_handler = logging.StreamHandler()
        console_handler.setFormatter(formatter)
        self.logger.addHandler(console_handler)

        # Optionally log to a file
        if log_to_file:
            file_handler = logging.FileHandler(log_file)
            file_handler.setFormatter(formatter)
            self.logger.addHandler(file_handler)

        self.spinner = AutoPauseSpinner()
        self.set_level("WARNING")


    def get_logger(self):
        return self.logger

    def set_level(self, level):
        if level == 'DEBUG':
            self.logger.setLevel(logging.DEBUG)
            self.spinner.stop()
        elif level == 'WARNING':
            self.logger.setLevel(logging.WARNING)
            self.spinner.start()

    def __del__(self):
        self.spinner.stop()


class ProviderError(RuntimeError):
    def __init__(self, message: str, error_type: str, code: str, status_code: int) -> None:
        super().__init__(message)
        self.message = message
        self.error_type = error_type
        self.code = code
        self.status_code = status_code

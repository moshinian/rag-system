class ProviderError(RuntimeError):
    """表示上游 provider 调用失败的统一业务异常。"""

    def __init__(self, message: str, error_type: str, code: str, status_code: int) -> None:
        """保存可直接透传给调用方的错误字段。"""
        super().__init__(message)
        self.message = message
        self.error_type = error_type
        self.code = code
        self.status_code = status_code

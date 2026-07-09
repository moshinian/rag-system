from __future__ import annotations

from typing import Any


def validate_arguments(arguments: dict[str, Any], schema: dict[str, Any]) -> None:
    """Validate the subset of JSON Schema used by current MCP tool arguments."""
    _validate_object(arguments, schema, "arguments")


def _validate_object(arguments: dict[str, Any], schema: dict[str, Any], field_path: str) -> None:
    required = schema.get("required", [])
    if isinstance(required, list):
        for name in required:
            if isinstance(name, str) and name not in arguments:
                raise ValueError(f"Missing required argument: {field_path}.{name}")
    properties = schema.get("properties", {})
    if not isinstance(properties, dict):
        properties = {}
    additional_properties = schema.get("additionalProperties", True)
    for name, value in arguments.items():
        spec = properties.get(name)
        if not isinstance(spec, dict):
            if additional_properties is False:
                raise ValueError(f"Unexpected argument: {field_path}.{name}")
            continue
        _validate_value(value, spec, f"{field_path}.{name}")


def _validate_value(value: Any, spec: dict[str, Any], field_path: str) -> None:
    expected_type = spec.get("type")
    if expected_type == "string" and not isinstance(value, str):
        raise ValueError(f"Argument {field_path} must be string")
    if expected_type == "integer" and (isinstance(value, bool) or not isinstance(value, int)):
        raise ValueError(f"Argument {field_path} must be integer")
    if expected_type == "number" and (isinstance(value, bool) or not isinstance(value, (int, float))):
        raise ValueError(f"Argument {field_path} must be number")
    if expected_type == "boolean" and not isinstance(value, bool):
        raise ValueError(f"Argument {field_path} must be boolean")
    if expected_type == "object":
        if not isinstance(value, dict):
            raise ValueError(f"Argument {field_path} must be object")
        _validate_object(value, spec, field_path)
    if expected_type == "array" and not isinstance(value, list):
        raise ValueError(f"Argument {field_path} must be array")
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        minimum = spec.get("minimum")
        maximum = spec.get("maximum")
        if isinstance(minimum, (int, float)) and value < minimum:
            raise ValueError(f"Argument {field_path} must be >= {minimum}")
        if isinstance(maximum, (int, float)) and value > maximum:
            raise ValueError(f"Argument {field_path} must be <= {maximum}")
